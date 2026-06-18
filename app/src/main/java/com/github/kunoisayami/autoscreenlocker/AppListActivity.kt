package com.github.kunoisayami.autoscreenlocker

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class AppListActivity : AppCompatActivity() {

    private val selectedPackages = mutableSetOf<String>()
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()
    private var showSystem = false
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_app_list)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        selectedPackages.addAll(Prefs.appListPackages(this))

        adapter = AppAdapter(emptyList(), selectedPackages) { pkg, checked ->
            if (checked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
            Prefs.setAppListPackages(this, selectedPackages)
        }

        val rv = findViewById<RecyclerView>(R.id.rvApps)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        progressBar.visibility = View.VISIBLE
        Executors.newSingleThreadExecutor().execute {
            val apps = loadAllApps()
            runOnUiThread {
                allApps = apps
                progressBar.visibility = View.GONE
                adapter.setApps(filteredApps())
            }
        }

        val cbShowSystem = findViewById<CheckBox>(R.id.cbShowSystemApps)
        cbShowSystem.setOnClickListener {
            showSystem = cbShowSystem.isChecked
            adapter.setApps(filteredApps())
        }

        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                adapter.setApps(filteredApps())
            }
        })

        val rgMode = findViewById<RadioGroup>(R.id.rgAppListMode)
        val tvHint = findViewById<TextView>(R.id.tvModeHint)

        fun updateHint(mode: AppListMode) {
            tvHint.text = when (mode) {
                AppListMode.OFF -> getString(R.string.app_list_mode_off_hint)
                AppListMode.BLACKLIST -> getString(R.string.app_list_mode_blacklist_hint)
                AppListMode.WHITELIST -> getString(R.string.app_list_mode_whitelist_hint)
            }
        }

        val currentMode = Prefs.appListMode(this)
        rgMode.check(when (currentMode) {
            AppListMode.OFF -> R.id.rbModeOff
            AppListMode.BLACKLIST -> R.id.rbModeBlacklist
            AppListMode.WHITELIST -> R.id.rbModeWhitelist
        })
        updateHint(currentMode)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeBlacklist -> AppListMode.BLACKLIST
                R.id.rbModeWhitelist -> AppListMode.WHITELIST
                else -> AppListMode.OFF
            }
            Prefs.setAppListMode(this, mode)
            updateHint(mode)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun filteredApps(): List<AppInfo> {
        val base = if (showSystem) allApps else allApps.filter { !it.isSystem }
        if (searchQuery.isEmpty()) return base
        return base.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    private fun loadAllApps(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .map {
                val isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), isSystem)
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }
}

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

class AppAdapter(
    apps: List<AppInfo>,
    private val selected: Set<String>,
    private val onToggle: (String, Boolean) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private val visible = apps.toMutableList()

    fun setApps(apps: List<AppInfo>) {
        val oldSize = visible.size
        visible.clear()
        notifyItemRangeRemoved(0, oldSize)
        visible.addAll(apps)
        notifyItemRangeInserted(0, visible.size)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val pkg: TextView = view.findViewById(R.id.tvPackageName)
        val cb: CheckBox = view.findViewById(R.id.cbAppSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = visible.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = visible[position]
        val pm = holder.itemView.context.packageManager
        holder.name.text = app.label
        holder.pkg.text = app.packageName
        holder.cb.isChecked = app.packageName in selected
        try {
            holder.icon.setImageDrawable(pm.getApplicationIcon(app.packageName))
        } catch (_: PackageManager.NameNotFoundException) {
            holder.icon.setImageDrawable(null)
        }
        holder.itemView.setOnClickListener {
            val nowChecked = !holder.cb.isChecked
            holder.cb.isChecked = nowChecked
            onToggle(app.packageName, nowChecked)
        }
    }
}
