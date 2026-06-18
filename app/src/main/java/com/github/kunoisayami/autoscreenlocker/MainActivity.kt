package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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

        binding.tvVersion.text = getString(R.string.label_version, packageManager.getPackageInfo(packageName, 0).versionName)

        loadSavedTimeout()
        binding.switchPersistent.isChecked = Prefs.isPersistent(this)
        binding.cbWarnBeforeLock.isChecked = Prefs.isWarnBeforeLock(this)
        setupListeners()
        promptBatteryOptimizationIfNeeded()
    }

    private fun promptBatteryOptimizationIfNeeded() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_battery_title)
            .setMessage(R.string.dialog_battery_message)
            .setPositiveButton(R.string.dialog_battery_ok) { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton(R.string.dialog_battery_cancel, null)
            .show()
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
        val totalSec = (Prefs.timeoutMs(this) / 1000).toInt()
        binding.etMinutes.setText((totalSec / 60).toString())
        binding.etSeconds.setText((totalSec % 60).toString())
        binding.etTotalSeconds.setText(totalSec.toString())
    }

    private fun updateMethodSelector() {
        val adminActive = dpm.isAdminActive(adminComponent)
        val rootAvailable = ScreenOff.isRootAvailable()
        val shizukuInstalled = ScreenOff.isShizukuInstalled()
        val shizukuAvailable = ScreenOff.isShizukuAvailable()
        val savedMethod = Prefs.screenOffMethod(this)

        val ta = theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorHint))
        val enabledColor = ta.getColor(0, 0xFFFFFFFF.toInt())
        val disabledColor = ta.getColor(1, 0xFF888888.toInt())
        ta.recycle()

        binding.rbMethodLockNow.apply {
            isEnabled = adminActive
            text = if (adminActive) getString(R.string.method_lock_now)
                   else getString(R.string.method_lock_now_no_admin)
            setTextColor(if (adminActive) enabledColor else disabledColor)
        }

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

        // Show the saved preference in the radio group — never mutate it here
        binding.rgMethod.setOnCheckedChangeListener(null)
        binding.rgMethod.check(
            when (savedMethod) {
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
            updateUi()
        }

        // Auto-request Shizuku permission only when the user has explicitly chosen Shizuku
        if (savedMethod == ScreenOffMethod.SHIZUKU && shizukuInstalled && !shizukuAvailable) {
            try { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) } catch (_: Exception) {}
        }
    }

    private fun methodLabel(method: ScreenOffMethod) = when (method) {
        ScreenOffMethod.LOCK_NOW -> getString(R.string.method_short_lock_now)
        ScreenOffMethod.SHIZUKU -> getString(R.string.method_short_shizuku)
        ScreenOffMethod.ROOT -> getString(R.string.method_short_root)
    }

    private fun isSecondsModeActive() = binding.rgInputMode.checkedRadioButtonId == R.id.rbModeTotalSec

    private fun applyInputMode(secondsMode: Boolean) {
        if (secondsMode) {
            binding.tilMinutes.visibility = View.GONE
            binding.tilSeconds.visibility = View.GONE
            binding.tilTotalSeconds.visibility = View.VISIBLE
        } else {
            binding.tilMinutes.visibility = View.VISIBLE
            binding.tilSeconds.visibility = View.VISIBLE
            binding.tilTotalSeconds.visibility = View.GONE
        }
    }

    private fun syncToSecondMode() {
        val min = binding.etMinutes.text?.toString()?.toIntOrNull() ?: 0
        val sec = binding.etSeconds.text?.toString()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        binding.etTotalSeconds.setText((min * 60 + sec).toString())
    }

    private fun syncToMinSecMode() {
        val total = binding.etTotalSeconds.text?.toString()?.toIntOrNull() ?: 0
        binding.etMinutes.setText((total / 60).toString())
        binding.etSeconds.setText((total % 60).toString())
    }

    private fun setupListeners() {
        binding.rgInputMode.setOnCheckedChangeListener { _, checkedId ->
            val secondsMode = checkedId == R.id.rbModeTotalSec
            if (secondsMode) syncToSecondMode() else syncToMinSecMode()
            applyInputMode(secondsMode)
        }

        binding.btnSave.setOnClickListener {
            val totalSec = if (isSecondsModeActive()) {
                binding.etTotalSeconds.text?.toString()?.toIntOrNull() ?: 0
            } else {
                val minutes = binding.etMinutes.text?.toString()?.toIntOrNull() ?: 0
                val seconds = binding.etSeconds.text?.toString()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                minutes * 60 + seconds
            }
            if (totalSec < MIN_TIMEOUT_SEC) {
                if (isSecondsModeActive()) {
                    binding.tilTotalSeconds.error = getString(R.string.error_timeout_too_short, MIN_TIMEOUT_SEC)
                } else {
                    binding.tilSeconds.error = getString(R.string.error_timeout_too_short, MIN_TIMEOUT_SEC)
                }
                return@setOnClickListener
            }
            binding.tilTotalSeconds.error = null
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
        val savedMethod = Prefs.screenOffMethod(this)
        val effectiveMethod = ScreenOff.resolveMethod(this)

        // Update the collapsible header summary
        binding.tvMethodCurrent.text = if (effectiveMethod == null) {
            getString(R.string.method_none_available)
        } else if (effectiveMethod != savedMethod) {
            getString(R.string.method_summary_fallback, methodLabel(savedMethod), methodLabel(effectiveMethod))
        } else {
            methodLabel(effectiveMethod)
        }

        binding.btnEnableAdmin.isEnabled = !adminActive
        binding.btnEnableAccessibility.isEnabled = !accessibilityActive
        binding.btnToggleService.isEnabled = effectiveMethod != null && accessibilityActive

        binding.btnToggleService.setText(
            if (serviceEnabled) R.string.btn_stop_service else R.string.btn_start_service
        )

        val lastLockTime = Prefs.lastLockTime(this)
        val lastLockMethod = Prefs.lastLockMethod(this)
        val lastLockStr = if (lastLockTime > 0L)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(lastLockTime))
        else null

        binding.tvStatus.text = when {
            !accessibilityActive -> getString(R.string.status_missing_accessibility)
            effectiveMethod == null -> getString(R.string.status_no_method_available)
            serviceEnabled -> {
                val totalSec = (Prefs.timeoutMs(this) / 1000).toInt()
                buildString {
                    append(getString(R.string.status_running, totalSec / 60, totalSec % 60))
                    if (effectiveMethod != savedMethod) {
                        append("\n${getString(R.string.status_using_fallback, methodLabel(effectiveMethod))}")
                    }
                    if (lastLockStr != null) {
                        val methodStr = if (lastLockMethod != null) methodLabel(lastLockMethod) else "?"
                        append("\n${getString(R.string.status_last_lock, lastLockStr, methodStr)}")
                    }
                }
            }
            else -> buildString {
                append(getString(R.string.status_stopped))
                if (lastLockStr != null) {
                    val methodStr = if (lastLockMethod != null) methodLabel(lastLockMethod) else "?"
                    append("\n${getString(R.string.status_last_lock, lastLockStr, methodStr)}")
                }
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
