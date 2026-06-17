package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kunoisayami.autoscreenlocker.databinding.ActivityMainBinding
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    companion object {
        const val MIN_TIMEOUT_SEC = 60
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockDeviceAdmin::class.java)

        loadSavedTimeout()
        binding.switchPersistent.isChecked = Prefs.isPersistent(this)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun loadSavedTimeout() {
        val totalMs = Prefs.timeoutMs(this)
        val totalSec = (totalMs / 1000).toInt()
        binding.etMinutes.setText((totalSec / 60).toString())
        binding.etSeconds.setText((totalSec % 60).toString())
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val minutes = binding.etMinutes.text?.toString()?.toIntOrNull() ?: 0
            val seconds = binding.etSeconds.text?.toString()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val totalSec = minutes * 60 + seconds
            if (totalSec < MIN_TIMEOUT_SEC) {
                binding.tilSeconds.error = getString(R.string.error_timeout_too_short, MIN_TIMEOUT_SEC)
                return@setOnClickListener
            }
            binding.tilSeconds.error = null
            binding.tilMinutes.error = null
            Prefs.setTimeoutMs(this, totalSec * 1000L)
            updateUi()
        }

        binding.switchPersistent.setOnCheckedChangeListener { _, checked ->
            Prefs.setPersistent(this, checked)
        }

        binding.btnEnableAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            }
            startActivity(intent)
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnToggleService.setOnClickListener {
            if (Prefs.isServiceEnabled(this)) {
                stopService(Intent(this, LockService::class.java))
                Prefs.setServiceEnabled(this, false)
            } else {
                startForegroundService(Intent(this, LockService::class.java))
                Prefs.setServiceEnabled(this, true)
            }
            updateUi()
        }
    }

    private fun updateUi() {
        val adminActive = dpm.isAdminActive(adminComponent)
        val accessibilityActive = isAccessibilityEnabled()
        val serviceEnabled = Prefs.isServiceEnabled(this)

        binding.btnEnableAdmin.isEnabled = !adminActive
        binding.btnEnableAccessibility.isEnabled = !accessibilityActive
        binding.btnToggleService.isEnabled = adminActive && accessibilityActive

        binding.btnToggleService.setText(
            if (serviceEnabled) R.string.btn_stop_service else R.string.btn_start_service
        )

        val lastLockTime = Prefs.lastLockTime(this)
        val lastLockStr = if (lastLockTime > 0L)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(lastLockTime))
        else null

        binding.tvStatus.text = when {
            !adminActive -> getString(R.string.status_missing_admin)
            !accessibilityActive -> getString(R.string.status_missing_accessibility)
            serviceEnabled -> {
                val totalSec = (Prefs.timeoutMs(this) / 1000).toInt()
                buildString {
                    append(getString(R.string.status_running, totalSec / 60, totalSec % 60))
                    if (lastLockStr != null) append("\n${getString(R.string.status_last_lock, lastLockStr)}")
                }
            }
            else -> buildString {
                append(getString(R.string.status_stopped))
                if (lastLockStr != null) append("\n${getString(R.string.status_last_lock, lastLockStr)}")
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(this, InteractionAccessibility::class.java).flattenToString()
        return enabledServices.split(":").any { it.equals(component, ignoreCase = true) }
    }
}
