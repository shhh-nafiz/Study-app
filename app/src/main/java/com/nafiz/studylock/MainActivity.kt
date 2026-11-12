package com.nafiz.studylock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var minutes = 45
    private lateinit var clock: TextView
    private lateinit var sub: TextView
    private lateinit var picker: LinearLayout
    private lateinit var action: TextView
    private lateinit var appsRow: TextView
    private lateinit var logRow: TextView
    private lateinit var warn: TextView
    private var lastRunning: Boolean? = null

    private val loop = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        minutes = Store.lastMinutes(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        root.addView(tv("StudyLock", 15f, C.MUTED), lp())

        clock = tv("", 76f, C.TEXT).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        root.addView(clock, lp(40))
        sub = tv("", 14f, C.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(sub, lp(0))

        picker = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val steps = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        steps.addView(button("−  5 min", C.CARD, C.TEXT) { setMinutes(maxOf(5, minutes - 5)) }, wlp())
        steps.addView(button("+  5 min", C.CARD, C.TEXT) { setMinutes(minOf(720, minutes + 5)) }, wlp(8))
        picker.addView(steps, lp(28))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(15, 25, 45, 60, 90).forEachIndexed { i, m ->
            chips.addView(button("$m", C.CARD, C.MUTED, 4) { setMinutes(m) }, wlp(if (i == 0) 0 else 8))
        }
        picker.addView(chips, lp(8))
        root.addView(picker, lp())

        action = button("", C.ACCENT, C.BG) { onAction() }
        root.addView(action, lp(24))

        appsRow = row("") { startActivity(Intent(this, AppsActivity::class.java)) }
        root.addView(appsRow, lp(28))
        logRow = row("") { startActivity(Intent(this, LogActivity::class.java)) }
        root.addView(logRow, lp(10))

        warn = tv(
            "Blocking is off. Tap to open Accessibility settings and turn on StudyLock.\n\n" +
                "If it's greyed out: Settings > Apps > StudyLock > ⋮ menu > Allow restricted settings.",
            13f, C.DANGER
        ).apply {
            background = pill(C.CARD)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(warn, lp(16))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() { super.onResume(); h.post(loop) }
    override fun onPause() { h.removeCallbacks(loop); super.onPause() }

    private fun setMinutes(m: Int) {
        minutes = m
        Store.setLastMinutes(this, m)
        refresh()
    }

    private fun onAction() {
        if (Store.isRunning(this)) {
            AlertDialog.Builder(this)
                .setTitle("End session early?")
                .setMessage("Time studied so far will be saved to your log.")
                .setPositiveButton("End") { _, _ ->
                    Store.finish(this)
                    stopService(Intent(this, TimerService::class.java))
                    refresh()
                }
                .setNegativeButton("Keep going", null)
                .show()
        } else {
            if (Store.blocked(this).isEmpty()) {
                Toast.makeText(this, "Pick the apps to block first", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AppsActivity::class.java))
                return
            }
            Store.begin(this, minutes)
            TimerService.start(this)
            refresh()
        }
    }

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refresh() {
        Store.settle(this)
        val running = Store.isRunning(this)
        if (running) {
            val sec = (Store.endMs(this) - System.currentTimeMillis() + 999) / 1000
            clock.text = fmtClock(sec)
            sub.text = "remaining · selected apps are blocked"
        } else {
            clock.text = fmtClock(minutes * 60L)
            sub.text = "session length"
        }
        if (lastRunning != running) {
            lastRunning = running
            picker.visibility = if (running) android.view.View.GONE else android.view.View.VISIBLE
            action.text = if (running) "Stop" else "Start"
            action.background = pill(if (running) C.DANGER else C.ACCENT)
        }
        val n = Store.blocked(this).size
        appsRow.text = "Blocked apps   ·   $n selected   ›"
        logRow.text = "Today   ·   ${fmtDur(Store.todaySeconds(this))}   ›   log"
        warn.visibility = if (a11yEnabled()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
