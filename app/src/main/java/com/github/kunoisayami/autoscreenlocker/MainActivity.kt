package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.github.kunoisayami.autoscreenlocker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockDeviceAdmin::class.java)

        loadSavedTimeout()
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
            val totalMs = (minutes * 60L + seconds) * 1000L
            Prefs.setTimeoutMs(this, totalMs.coerceAtLeast(1000L))
            updateUi()
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
            if (LockService.isRunning) {
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
        val serviceRunning = LockService.isRunning

        binding.btnEnableAdmin.isEnabled = !adminActive
        binding.btnEnableAccessibility.isEnabled = !accessibilityActive
        binding.btnToggleService.isEnabled = adminActive && accessibilityActive

        binding.btnToggleService.setText(
            if (serviceRunning) R.string.btn_stop_service else R.string.btn_start_service
        )

        binding.tvStatus.text = when {
            !adminActive -> getString(R.string.status_missing_admin)
            !accessibilityActive -> getString(R.string.status_missing_accessibility)
            serviceRunning -> {
                val totalSec = (Prefs.timeoutMs(this) / 1000).toInt()
                getString(R.string.status_running, totalSec / 60, totalSec % 60)
            }
            else -> getString(R.string.status_stopped)
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
