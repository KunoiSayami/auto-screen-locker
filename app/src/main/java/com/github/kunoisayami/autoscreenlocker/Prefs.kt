package com.github.kunoisayami.autoscreenlocker

import android.content.Context
import androidx.core.content.edit

private const val PREF_FILE = "asl_prefs"
private const val KEY_TIMEOUT_MS = "timeout_ms"
private const val KEY_SERVICE_ENABLED = "service_enabled"
private const val KEY_PERSISTENT = "persistent"
private const val KEY_SCREEN_OFF_METHOD = "screen_off_method"
private const val KEY_LAST_LOCK_TIME = "last_lock_time"
private const val KEY_WARN_BEFORE_LOCK = "warn_before_lock"
private const val KEY_LAST_LOCK_METHOD = "last_lock_method"
private const val KEY_APP_LIST_MODE = "app_list_mode"
private const val KEY_APP_LIST_PACKAGES = "app_list_packages"
private const val KEY_LANGUAGE = "language"

private const val DEFAULT_TIMEOUT_MS = 60_000L  // 1 minute

object Prefs {
    fun timeoutMs(context: Context): Long =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getLong(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)

    fun setTimeoutMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putLong(KEY_TIMEOUT_MS, ms) }
    }

    fun isServiceEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_SERVICE_ENABLED, enabled) }
    }

    fun screenOffMethod(context: Context): ScreenOffMethod {
        val name = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_SCREEN_OFF_METHOD, ScreenOffMethod.LOCK_NOW.name)
        return ScreenOffMethod.entries.find { it.name == name } ?: ScreenOffMethod.LOCK_NOW
    }

    fun setScreenOffMethod(context: Context, method: ScreenOffMethod) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_SCREEN_OFF_METHOD, method.name) }
    }

    fun isPersistent(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERSISTENT, false)

    fun setPersistent(context: Context, persistent: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PERSISTENT, persistent) }
    }

    fun isWarnBeforeLock(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_WARN_BEFORE_LOCK, true)

    fun setWarnBeforeLock(context: Context, warn: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_WARN_BEFORE_LOCK, warn) }
    }

    fun lastLockTime(context: Context): Long =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_LOCK_TIME, 0L)

    fun setLastLockTime(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putLong(KEY_LAST_LOCK_TIME, timeMs) }
    }

    fun lastLockMethod(context: Context): ScreenOffMethod? {
        val name = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LOCK_METHOD, null) ?: return null
        return ScreenOffMethod.entries.find { it.name == name }
    }

    fun setLastLockMethod(context: Context, method: ScreenOffMethod) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_LAST_LOCK_METHOD, method.name) }
    }

    fun appListMode(context: Context): AppListMode {
        val name = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_APP_LIST_MODE, AppListMode.OFF.name)
        return AppListMode.entries.find { it.name == name } ?: AppListMode.OFF
    }

    fun setAppListMode(context: Context, mode: AppListMode) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_APP_LIST_MODE, mode.name) }
    }

    fun appListPackages(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_APP_LIST_PACKAGES, null) ?: return emptySet()
        return raw.split("\n").filter { it.isNotEmpty() }.toSet()
    }

    fun setAppListPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_APP_LIST_PACKAGES, packages.joinToString("\n")) }
    }

    /** Returns the user-chosen language tag, or "auto" if following system locale. */
    fun language(context: Context): String =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "auto") ?: "auto"

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE, tag) }
    }
}
