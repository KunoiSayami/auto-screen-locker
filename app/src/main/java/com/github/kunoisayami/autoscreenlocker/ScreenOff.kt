package com.github.kunoisayami.autoscreenlocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ScreenOff {
    private const val TAG = "ScreenOff"

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
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("power"))
        // Use reflection to call IPowerManager.goToSleep via the wrapped binder
        val stubClass = Class.forName("android.os.IPowerManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        val pm = asInterface.invoke(null, binder)
        val goToSleep = pm.javaClass.getMethod("goToSleep", Long::class.java, Int::class.java, Int::class.java)
        goToSleep.invoke(pm, SystemClock.uptimeMillis(), 2 /* GO_TO_SLEEP_REASON_APPLICATION */, 0)
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
