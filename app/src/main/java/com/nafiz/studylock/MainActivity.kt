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
    private lateinit var wheelH: WheelPicker
    private lateinit var wheelM: WheelPicker
    private var ringShown = false
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
        minutes = Store.lastMinutes(this).coerceIn(1, 719)

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
        wheelH = WheelPicker(this, 0, 11, "%d")
        wheelM = WheelPicker(this, 0, 59, "%02d")
        wheelH.setValue(minutes / 60, false)
        wheelM.setValue(minutes % 60, false)
        wheelH.onChange = { onWheel() }
        wheelM.onChange = { onWheel() }
        val cols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        cols.addView(column(wheelH, "hours"), LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT))
        cols.addView(column(wheelM, "minutes"),
            LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(16) })
        picker.addView(cols, lp(8))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(15, 25, 45, 60, 90).forEachIndexed { i, m ->
            chips.addView(button("$m", C.CARD, C.MUTED, 4) { setMinutes(m) }, wlp(if (i == 0) 0 else 8))
        }
        picker.addView(chips, lp(20))
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

    private fun column(w: WheelPicker, label: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(w, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(tv(label, 13f, C.MUTED).apply { gravity = Gravity.CENTER }, lp(6))
        }

    private fun onWheel() {
        minutes = wheelH.value * 60 + wheelM.value
        if (minutes > 0) Store.setLastMinutes(this, minutes)
    }

    private fun setMinutes(total: Int) {
        minutes = total.coerceIn(1, 719)
        wheelH.setValue(minutes / 60, true)
        wheelM.setValue(minutes % 60, true)
        Store.setLastMinutes(this, minutes)
    }

    private fun onAction() {
        if (Store.isRunning(this)) {
            AlertDialog.Builder(this)
                .setTitle("End session early?")
                .setMessage("Time studied so far will be saved to your log.")
                .setPositiveButton("End") { _, _ ->
                    TimerService.abort(this)
                    refresh()
                }
                .setNegativeButton("Keep going", null)
                .show()
        } else {
            minutes = wheelH.value * 60 + wheelM.value
            if (minutes < 1) {
                Toast.makeText(this, "Pick at least 1 minute", Toast.LENGTH_SHORT).show()
                return
            }
            if (Store.blocked(this).isEmpty()) {
                Toast.makeText(this, "Pick the apps to block first", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AppsActivity::class.java))
                return
            }
            Store.setLastMinutes(this, minutes)
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

        // Alarm is sounding: bring up the full-screen "Time's up" screen once.
        if (Store.isRinging(this)) {
            if (!ringShown) {
                ringShown = true
                startActivity(Intent(this, RingActivity::class.java))
            }
        } else ringShown = false

        if (running) {
            val sec = maxOf(0L, (Store.endMs(this) - System.currentTimeMillis() + 999) / 1000)
            clock.text = fmtClock(sec)
            sub.text = "remaining · selected apps are blocked"
        } else {
            sub.text = "Set your study time"
        }
        if (lastRunning != running) {
            lastRunning = running
            picker.visibility = if (running) android.view.View.GONE else android.view.View.VISIBLE
            clock.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE
            (sub.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (running) 0 else 32)
            sub.requestLayout()
            action.text = if (running) "Stop" else "Start"
            action.background = pill(if (running) C.DANGER else C.ACCENT)
        }
        val n = Store.blocked(this).size
        appsRow.text = "Blocked apps   ·   $n selected   ›"
        logRow.text = "Today   ·   ${fmtDur(Store.todaySeconds(this))}   ›   log"
        warn.visibility = if (a11yEnabled()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
