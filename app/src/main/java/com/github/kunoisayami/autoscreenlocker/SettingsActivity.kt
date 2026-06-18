package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.os.Bundle
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.kunoisayami.autoscreenlocker.databinding.ActivitySettingsBinding
import rikka.shizuku.Shizuku

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { updateMethodSelector() }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1002
        private const val KEY_SUPPRESS_DROPDOWN = "suppress_dropdown"

        private val LANGUAGE_TAGS = listOf("auto", "en", "fr", "de", "ja", "zh-TW", "zh-CN")
        private val LANGUAGE_LABELS = listOf(
            "Auto", "English", "Français", "Deutsch", "日本語", "繁體中文", "简体中文"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockDeviceAdmin::class.java)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        binding.switchPersistent.isChecked = Prefs.isPersistent(this)
        binding.cbWarnBeforeLock.isChecked = Prefs.isWarnBeforeLock(this)
        loadAppListMode()
        setupLanguageSelector(savedInstanceState?.getBoolean(KEY_SUPPRESS_DROPDOWN, false) == true)
        setupListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SUPPRESS_DROPDOWN, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        updateMethodSelector()
        val adminActive = dpm.isAdminActive(adminComponent)
        binding.btnEnableAdmin.isEnabled = !adminActive
        binding.btnEnableAccessibility.isEnabled = !isAccessibilityEnabled()
    }

    private fun loadAppListMode() {
        val mode = Prefs.appListMode(this)
        binding.rgAppListMode.setOnCheckedChangeListener(null)
        binding.rgAppListMode.check(
            when (mode) {
                AppListMode.BLACKLIST -> R.id.rbModeBlacklist
                AppListMode.WHITELIST -> R.id.rbModeWhitelist
                AppListMode.OFF -> R.id.rbModeOff
            }
        )
        applyAppListMode(mode)
    }

    private fun applyAppListMode(mode: AppListMode) {
        binding.tvAppListHint.text = getString(
            when (mode) {
                AppListMode.OFF -> R.string.app_list_mode_off_hint
                AppListMode.BLACKLIST -> R.string.app_list_mode_blacklist_hint
                AppListMode.WHITELIST -> R.string.app_list_mode_whitelist_hint
            }
        )
        binding.btnAppList.visibility = if (mode == AppListMode.OFF) View.GONE else View.VISIBLE
    }

    private fun setupLanguageSelector(suppressDropdown: Boolean = false) {
        val actv = binding.actvLanguage as MaterialAutoCompleteTextView
        // Non-filterable adapter: getFilter returns a no-op so the full list always shows
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_dropdown_item_1line, LANGUAGE_LABELS
        ) {
            override fun getFilter() = object : android.widget.Filter() {
                override fun performFiltering(constraint: CharSequence?) =
                    FilterResults().apply { values = LANGUAGE_LABELS; count = LANGUAGE_LABELS.size }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }
        actv.setAdapter(adapter)

        val currentTag = Prefs.language(this)
        val currentIndex = LANGUAGE_TAGS.indexOf(currentTag).takeIf { it >= 0 } ?: 0
        actv.setText(LANGUAGE_LABELS[currentIndex], false)

        if (suppressDropdown) {
            actv.dismissDropDown()
            actv.clearFocus()
        }

        actv.setOnItemClickListener { _, _, position, _ ->
            val selectedTag = LANGUAGE_TAGS[position]
            actv.dismissDropDown()
            actv.clearFocus()
            if (selectedTag != Prefs.language(this)) {
                Prefs.setLanguage(this, selectedTag)
                recreate()
            }
        }
    }

    private fun setupListeners() {
        binding.switchPersistent.setOnCheckedChangeListener { _, checked ->
            Prefs.setPersistent(this, checked)
        }

        binding.cbWarnBeforeLock.setOnCheckedChangeListener { _, checked ->
            Prefs.setWarnBeforeLock(this, checked)
        }

        binding.rgAppListMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeBlacklist -> AppListMode.BLACKLIST
                R.id.rbModeWhitelist -> AppListMode.WHITELIST
                else -> AppListMode.OFF
            }
            Prefs.setAppListMode(this, mode)
            applyAppListMode(mode)
        }

        binding.btnAppList.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
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
        }

        val effectiveMethod = ScreenOff.resolveMethod(this)
        val methodLabel: (ScreenOffMethod) -> String = { m ->
            when (m) {
                ScreenOffMethod.LOCK_NOW -> getString(R.string.method_short_lock_now)
                ScreenOffMethod.SHIZUKU -> getString(R.string.method_short_shizuku)
                ScreenOffMethod.ROOT -> getString(R.string.method_short_root)
            }
        }
        binding.tvMethodCurrent.text = if (effectiveMethod == null) {
            getString(R.string.method_none_available)
        } else if (effectiveMethod != savedMethod) {
            getString(R.string.method_summary_fallback, methodLabel(savedMethod), methodLabel(effectiveMethod))
        } else {
            methodLabel(effectiveMethod)
        }

        if (savedMethod == ScreenOffMethod.SHIZUKU && shizukuInstalled && !shizukuAvailable) {
            try { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) } catch (_: Exception) {}
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(this, InteractionAccessibility::class.java).flattenToString()
        return enabledServices.split(":").any { it.equals(component, ignoreCase = true) }
    }
}
