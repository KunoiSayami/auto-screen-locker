package com.github.kunoisayami.autoscreenlocker

import android.content.Context
import androidx.core.content.edit

private const val PREF_FILE = "asl_prefs"
private const val KEY_TIMEOUT_MS = "timeout_ms"
private const val KEY_SERVICE_ENABLED = "service_enabled"
private const val KEY_PERSISTENT = "persistent"
private const val KEY_LAST_LOCK_TIME = "last_lock_time"

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

    fun isPersistent(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERSISTENT, false)

    fun setPersistent(context: Context, persistent: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PERSISTENT, persistent) }
    }

    fun lastLockTime(context: Context): Long =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_LOCK_TIME, 0L)

    fun setLastLockTime(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit { putLong(KEY_LAST_LOCK_TIME, timeMs) }
    }
}
