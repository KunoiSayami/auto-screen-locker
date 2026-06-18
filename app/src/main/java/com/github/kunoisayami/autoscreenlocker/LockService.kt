package com.github.kunoisayami.autoscreenlocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LockService : Service() {

    private lateinit var scheduler: ScheduledExecutorService
    private var checkFuture: ScheduledFuture<*>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val warnToastShown = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
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
            val warnMs = timeoutMs - WARN_BEFORE_MS
            if (Prefs.isWarnBeforeLock(applicationContext)
                && warnMs > 0 && idleMs >= warnMs && idleMs < timeoutMs) {
                if (warnToastShown.compareAndSet(false, true)) {
                    mainHandler.post {
                        Toast.makeText(applicationContext, R.string.toast_locking_soon, Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (idleMs < warnMs) {
                warnToastShown.set(false)
            }
            if (idleMs >= timeoutMs) {
                val method = ScreenOff.resolveMethod(applicationContext)
                if (method == null) {
                    Log.w(TAG, "No screen-off method available, skipping lock")
                    return@scheduleWithFixedDelay
                }
                Log.d(TAG, "Turning off screen via $method")
                val success = ScreenOff.turnOffScreen(applicationContext, method)
                if (success) {
                    warnToastShown.set(false)
                    Prefs.setLastLockTime(applicationContext, System.currentTimeMillis())
                    Prefs.setLastLockMethod(applicationContext, method)
                    lastLockElapsedTime.set(SystemClock.elapsedRealtime())
                    if (Prefs.isPersistent(applicationContext)) {
                        lastInteractionTime.set(Long.MAX_VALUE / 2)
                    } else {
                        checkFuture?.cancel(false)
                        Prefs.setServiceEnabled(applicationContext, false)
                        stopSelf()
                    }
                } else {
                    Log.w(TAG, "Screen off failed for method $method")
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
        private const val WARN_BEFORE_MS = 5_000L

        private val lastInteractionTime = AtomicLong(SystemClock.elapsedRealtime())
        private val lastLockElapsedTime = AtomicLong(0L)

        fun resetTimer() {
            lastInteractionTime.set(SystemClock.elapsedRealtime())
        }

        val isRunning get() = instance != null
    }
}
