package com.nafiz.studylock

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout

/** Full-screen "Time's up" screen. Shows over the lock screen and stops the alarm. */
class RingActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())

    private val loop = object : Runnable {
        override fun run() {
            if (!Store.isRinging(this@RingActivity)) { finish(); return }
            h.postDelayed(this, 500)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val last = Store.sessions(this).firstOrNull()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        root.addView(tv("Time's up", 40f, C.TEXT, true).apply { gravity = Gravity.CENTER }, lp())
        val line = if (last != null) "You studied ${fmtDur(last.secs)}" else "Session complete"
        root.addView(tv(line, 18f, C.ACCENT).apply { gravity = Gravity.CENTER }, lp(12))
        root.addView(
            tv("Today   ·   ${fmtDur(Store.todaySeconds(this))}", 14f, C.MUTED)
                .apply { gravity = Gravity.CENTER }, lp(6)
        )
        if (Store.buddy(this)) {
            root.addView(
                tv("Take five. Water, stretch, eyes off the screen.", 14f, C.MUTED)
                    .apply { gravity = Gravity.CENTER }, lp(20)
            )
        }
        root.addView(button("Stop alarm", C.ACCENT, C.ON_ACCENT) { stopAlarm() }, lp(48))
        setContentView(root)
    }

    private fun stopAlarm() {
        startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_DISMISS))
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { stopAlarm() }

    override fun onResume() { super.onResume(); h.post(loop) }
    override fun onPause() { h.removeCallbacks(loop); super.onPause() }
}
