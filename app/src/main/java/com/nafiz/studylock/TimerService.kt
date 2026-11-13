package com.nafiz.studylock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Runs the study session.
 *
 * The end of the session is driven by AlarmManager.setAlarmClock (exact, and it still fires
 * while the phone is asleep or in Doze). The old Handler timer is only a backup: Handler time
 * stops while the phone sleeps, which is why the countdown used to run past zero.
 */
class TimerService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val tick = Runnable { fire() }
    private val autoStop = Runnable { ringTimedOut() }
    private var player: MediaPlayer? = null
    private var ringing = false

    companion object {
        const val CH = "timer"
        const val CH_ALARM = "alarm"
        const val CH_DONE = "done"
        const val ACTION_STOP = "com.nafiz.studylock.STOP"       // end the session early
        const val ACTION_FIRE = "com.nafiz.studylock.FIRE"       // the timer reached zero
        const val ACTION_DISMISS = "com.nafiz.studylock.DISMISS" // silence the alarm

        fun start(c: Context) {
            c.startForegroundService(Intent(c, TimerService::class.java))
        }

        private fun firePending(c: Context): PendingIntent =
            PendingIntent.getForegroundService(
                c, 3,
                Intent(c, TimerService::class.java).setAction(ACTION_FIRE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun scheduleAlarm(c: Context, endMs: Long) {
            val am = c.getSystemService(AlarmManager::class.java)
            val show = PendingIntent.getActivity(
                c, 4, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(endMs, show), firePending(c))
            } catch (e: Exception) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, firePending(c)) }
                catch (e2: Exception) { }
            }
        }

        fun cancelAlarm(c: Context) {
            c.getSystemService(AlarmManager::class.java).cancel(firePending(c))
        }

        /** Ends the running session early: logs the time so far, cancels the alarm, stops the service. */
        fun abort(c: Context) {
            cancelAlarm(c)
            Store.clearAlarm(c)
            Store.finish(c)
            c.stopService(Intent(c, TimerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        h.removeCallbacks(tick)
        h.removeCallbacks(autoStop)
        if (ringing) silence()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                h.removeCallbacks(tick)
                cancelAlarm(this)
                Store.clearAlarm(this)
                Store.finish(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS -> {
                silence()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FIRE -> {
                fire()
                return START_NOT_STICKY
            }
        }

        // Normal start (or restart after the system killed us mid-session).
        val end = Store.endMs(this)
        try {
            startFg(buildNotification(end))
        } catch (e: Exception) {
            // Could not become a foreground service: record why, and don't leave a half-started session.
            Store.setCrash(this, "Timer service failed to start:\n" + android.util.Log.getStackTraceString(e))
            Store.clearAlarm(this)
            Store.finish(this)
            stopSelf()
            return START_NOT_STICKY
        }
        h.removeCallbacks(tick)
        val left = end - System.currentTimeMillis()
        if (end == 0L) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (left <= 0) {
            fire()
            return START_NOT_STICKY
        }
        scheduleAlarm(this, end)
        h.postDelayed(tick, left)
        return START_STICKY
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    /** The timer hit zero: log the session, unblock apps and start ringing. Safe to call twice. */
    private fun fire() {
        // Always become a foreground service first: the system requires it after a start.
        startFg(buildRingNotification())
        if (ringing || !Store.consumeAlarm(this)) {
            if (!ringing) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        h.removeCallbacks(tick)
        Store.finish(this)
        ringing = true
        Store.setRinging(this, true)
        startAlarmSound()
        h.postDelayed(autoStop, Store.ringMs(this))
    }

    private fun ringTimedOut() {
        silence()
        postDone()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun startAlarmSound() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // The sound picked in Settings first, then built-in fallbacks (unless "silent" was chosen).
        val candidates = ArrayList<android.net.Uri>()
        Store.alarmSound(this)?.let { candidates.add(it) }
        if (Store.alarmUri(this) != "silent") {
            for (kind in intArrayOf(
                RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE
            )) RingtoneManager.getDefaultUri(kind)?.let { candidates.add(it) }
        }
        for (uri in candidates) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.prepare()
                mp.start()
                player = mp
                break
            } catch (e: Exception) {
                // try the next sound
            }
        }
        if (Store.vibrate(this)) {
            try {
                val v = getSystemService(Vibrator::class.java)
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attrs)
            } catch (e: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun silence() {
        h.removeCallbacks(autoStop)
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        try { getSystemService(Vibrator::class.java)?.cancel() } catch (e: Exception) { }
        ringing = false
        Store.setRinging(this, false)
    }

    private fun postDone() {
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

    private fun buildRingNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CH_ALARM, "Timer alarm", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null) // the service plays the alarm sound itself
        ch.enableVibration(false)
        nm.createNotificationChannel(ch)
        val ring = PendingIntent.getActivity(
            this, 5, Intent(this, RingActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            this, 6,
            Intent(this, TimerService::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Time's up")
            .setContentText("Your study session is complete. Tap to stop the alarm.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(ring)
            .setFullScreenIntent(ring, true)
            .addAction(R.drawable.ic_stat, "Stop alarm", dismiss)
            .build()
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
