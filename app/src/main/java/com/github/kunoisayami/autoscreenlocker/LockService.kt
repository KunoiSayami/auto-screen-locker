package com.github.kunoisayami.autoscreenlocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class LockService : Service() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var scheduler: ScheduledExecutorService
    private var checkFuture: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LockDeviceAdmin::class.java)
        scheduler = Executors.newSingleThreadScheduledExecutor()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        scheduleCheck()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        checkFuture?.cancel(false)
        scheduler.shutdownNow()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleCheck() {
        checkFuture?.cancel(false)
        val intervalMs = 500L
        checkFuture = scheduler.scheduleWithFixedDelay({
            val now = SystemClock.elapsedRealtime()
            val lastEvent = lastInteractionTime.get()
            val timeoutMs = Prefs.timeoutMs(applicationContext)
            val idleMs = now - lastEvent
            val graceRemainingMs = GRACE_PERIOD_MS - (now - lastLockElapsedTime.get())
            Log.d(TAG, "idle=${idleMs}ms timeout=${timeoutMs}ms grace=${graceRemainingMs}ms")
            if (graceRemainingMs > 0) return@scheduleWithFixedDelay
            if (idleMs >= timeoutMs) {
                if (dpm.isAdminActive(adminComponent)) {
                    Log.d(TAG, "Locking screen now")
                    dpm.lockNow()
                    Prefs.setLastLockTime(applicationContext, System.currentTimeMillis())
                    lastLockElapsedTime.set(SystemClock.elapsedRealtime())
                    if (Prefs.isPersistent(applicationContext)) {
                        lastInteractionTime.set(Long.MAX_VALUE / 2)
                    } else {
                        checkFuture?.cancel(false)
                        Prefs.setServiceEnabled(applicationContext, false)
                        stopSelf()
                    }
                } else {
                    Log.w(TAG, "Timeout reached but device admin not active — cannot lock")
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    private fun buildNotification(): Notification {
        val channelId = "asl_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val TAG = "LockService"

        @Volatile
        private var instance: LockService? = null

        private const val GRACE_PERIOD_MS = 30_000L

        private val lastInteractionTime = AtomicLong(SystemClock.elapsedRealtime())
        private val lastLockElapsedTime = AtomicLong(0L)

        fun resetTimer() {
            lastInteractionTime.set(SystemClock.elapsedRealtime())
        }

        val isRunning get() = instance != null
    }
}
