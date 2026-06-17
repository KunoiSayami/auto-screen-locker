package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kunoisayami.autoscreenlocker.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    companion object {
        const val MIN_TIMEOUT_SEC = 20
        private const val SHIZUKU_REQUEST_CODE = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private var shizukuPermissionRequested = false

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { updateMethodSelector() }
    }

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

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        loadSavedTimeout()
        binding.switchPersistent.isChecked = Prefs.isPersistent(this)
        binding.cbWarnBeforeLock.isChecked = Prefs.isWarnBeforeLock(this)
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        updateMethodSelector()
    }

    private fun loadSavedTimeout() {
        val totalMs = Prefs.timeoutMs(this)
        val totalSec = (totalMs / 1000).toInt()
        binding.etMinutes.setText((totalSec / 60).toString())
        binding.etSeconds.setText((totalSec % 60).toString())
    }

    private fun updateMethodSelector() {
        val rootAvailable = ScreenOff.isRootAvailable()
        val shizukuInstalled = ScreenOff.isShizukuInstalled()
        val shizukuAvailable = ScreenOff.isShizukuAvailable()
        val savedMethod = Prefs.screenOffMethod(this)

        val ta = theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorHint))
        val enabledColor = ta.getColor(0, 0xFFFFFFFF.toInt())
        val disabledColor = ta.getColor(1, 0xFF888888.toInt())
        ta.recycle()

        binding.rbMethodShizuku.apply {
            isEnabled = shizukuAvailable
            text = when {
                !shizukuInstalled -> getString(R.string.method_shizuku_not_installed)
                !shizukuAvailable -> getString(R.string.method_shizuku_no_permission)
                else -> getString(R.string.method_shizuku)
            }
            setTextColor(if (shizukuAvailable) enabledColor else disabledColor)
        }

        binding.rbMethodRoot.apply {
            isEnabled = rootAvailable
            text = if (rootAvailable) getString(R.string.method_root)
                   else getString(R.string.method_root_unavailable)
            setTextColor(if (rootAvailable) enabledColor else disabledColor)
        }

        // Fall back to LOCK_NOW if saved method is no longer available
        val effectiveMethod = when {
            savedMethod == ScreenOffMethod.SHIZUKU && !shizukuAvailable -> ScreenOffMethod.LOCK_NOW
            savedMethod == ScreenOffMethod.ROOT && !rootAvailable -> ScreenOffMethod.LOCK_NOW
            else -> savedMethod
        }

        // Suppress the listener while programmatically checking to avoid a save loop
        binding.rgMethod.setOnCheckedChangeListener(null)
        binding.rgMethod.check(
            when (effectiveMethod) {
                ScreenOffMethod.LOCK_NOW -> R.id.rbMethodLockNow
                ScreenOffMethod.SHIZUKU -> R.id.rbMethodShizuku
                ScreenOffMethod.ROOT -> R.id.rbMethodRoot
            }
        )
        binding.rgMethod.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.rbMethodShizuku -> ScreenOffMethod.SHIZUKU
                R.id.rbMethodRoot -> ScreenOffMethod.ROOT
                else -> ScreenOffMethod.LOCK_NOW
            }
            Prefs.setScreenOffMethod(this, method)
            updateMethodSummary(method)
        }

        updateMethodSummary(effectiveMethod)

        // Auto-request Shizuku permission at most once per activity session
        if (shizukuInstalled && !shizukuAvailable && !shizukuPermissionRequested) {
            shizukuPermissionRequested = true
            try { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) } catch (_: Exception) {}
        }
    }

    private fun updateMethodSummary(method: ScreenOffMethod) {
        binding.tvMethodCurrent.text = when (method) {
            ScreenOffMethod.LOCK_NOW -> getString(R.string.method_lock_now)
            ScreenOffMethod.SHIZUKU -> getString(R.string.method_shizuku)
            ScreenOffMethod.ROOT -> getString(R.string.method_root)
        }
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

        binding.cbWarnBeforeLock.setOnCheckedChangeListener { _, checked ->
            Prefs.setWarnBeforeLock(this, checked)
        }

        binding.llMethodHeader.setOnClickListener {
            val expanded = binding.rgMethod.visibility == View.VISIBLE
            binding.rgMethod.visibility = if (expanded) View.GONE else View.VISIBLE
            binding.ivMethodChevron.rotation = if (expanded) 0f else 180f
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
