package com.nafiz.studylock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class BlockActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var what: TextView
    private lateinit var left: TextView

    private val loop = object : Runnable {
        override fun run() {
            if (!Store.isRunning(this@BlockActivity)) { finish(); return }
            val sec = (Store.endMs(this@BlockActivity) - System.currentTimeMillis() + 999) / 1000
            left.text = fmtClock(sec)
            h.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        root.addView(tv("Stay focused", 26f, C.TEXT, true).apply { gravity = Gravity.CENTER }, lp())
        what = tv("", 15f, C.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(what, lp(8))
        left = tv("", 64f, C.ACCENT).apply { gravity = Gravity.CENTER }
        root.addView(left, lp(32))
        root.addView(tv("left in your study session", 14f, C.MUTED).apply { gravity = Gravity.CENTER }, lp(0))
        root.addView(button("Go home", C.ACCENT, C.BG) { goHome() }, lp(40))
        setContentView(root)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    @Suppress("DEPRECATION")
    private fun show(i: Intent?) {
        val pkg = i?.getStringExtra("pkg") ?: ""
        val name = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        what.text = if (name.isEmpty()) "This app is blocked right now" else "$name is blocked right now"
    }

    override fun onResume() { super.onResume(); h.post(loop) }
    override fun onPause() { h.removeCallbacks(loop); super.onPause() }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { goHome() }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
