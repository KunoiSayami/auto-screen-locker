package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku

object ScreenOff {
    private const val TAG = "ScreenOff"

    fun isLockNowAvailable(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, LockDeviceAdmin::class.java))
    }

    fun isRootAvailable(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val result = process.inputStream.bufferedReader().readLine() ?: ""
        process.waitFor()
        result.contains("uid=0")
    } catch (e: Exception) {
        false
    }

    fun isShizukuInstalled(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun isAvailable(context: Context, method: ScreenOffMethod): Boolean = when (method) {
        ScreenOffMethod.LOCK_NOW -> isLockNowAvailable(context)
        ScreenOffMethod.SHIZUKU -> isShizukuAvailable()
        ScreenOffMethod.ROOT -> isRootAvailable()
    }

    /**
     * Returns the method that will actually be used at lock time.
     * Tries the stored preference first, then falls back: Shizuku → Root → LockNow.
     * Returns null if no method is currently available.
     */
    fun resolveMethod(context: Context): ScreenOffMethod? {
        val preferred = Prefs.screenOffMethod(context)
        if (isAvailable(context, preferred)) return preferred
        val fallbackOrder = listOf(ScreenOffMethod.SHIZUKU, ScreenOffMethod.ROOT, ScreenOffMethod.LOCK_NOW)
        return fallbackOrder.firstOrNull { it != preferred && isAvailable(context, it) }
    }

    fun turnOffScreen(context: Context, method: ScreenOffMethod): Boolean {
        return when (method) {
            ScreenOffMethod.LOCK_NOW -> lockNow(context)
            ScreenOffMethod.SHIZUKU -> goToSleepViaShizuku()
            ScreenOffMethod.ROOT -> goToSleepViaRoot()
        }
    }

    private fun lockNow(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, LockDeviceAdmin::class.java)
        return if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            true
        } else {
            Log.w(TAG, "Device admin not active")
            false
        }
    }

    private fun goToSleepViaShizuku(): Boolean = try {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).also { it.isAccessible = true }
        val process = newProcess.invoke(null, arrayOf("input", "keyevent", "26"), null, null) as Process
        process.waitFor()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Shizuku goToSleep failed", e)
        false
    }

    private fun goToSleepViaRoot(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 26"))
        process.waitFor()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Root goToSleep failed", e)
        false
    }
}
