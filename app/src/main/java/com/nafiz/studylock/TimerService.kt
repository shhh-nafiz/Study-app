package com.nafiz.studylock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class TimerService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val tick = Runnable { complete(true) }

    companion object {
        const val CH = "timer"
        const val CH_DONE = "done"
        const val ACTION_STOP = "com.nafiz.studylock.STOP"
        fun start(c: Context) {
            c.startForegroundService(Intent(c, TimerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        h.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            h.removeCallbacks(tick)
            Store.finish(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val end = Store.endMs(this)
        try {
            val n = buildNotification(end)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        h.removeCallbacks(tick)
        val left = end - System.currentTimeMillis()
        if (end == 0L) complete(false)
        else if (left <= 0) complete(true)
        else h.postDelayed(tick, left)
        return START_STICKY
    }

    private fun complete(notify: Boolean) {
        Store.finish(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (notify) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "Session finished", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Session complete")
                .setContentText("Nice work. Your apps are unblocked and your time is logged.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(2, n)
        }
        stopSelf()
    }

    private fun buildNotification(end: Long): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Study timer", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Study mode on")
            .setContentText("Time left · blocked apps are locked")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(end)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat, "Stop", stop)
            .build()
    }
}
