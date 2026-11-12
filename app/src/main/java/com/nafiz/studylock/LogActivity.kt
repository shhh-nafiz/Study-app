package com.nafiz.studylock

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Locale

class LogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(header("Study log"), lp())

        val data = Store.log(this)
        val now = System.currentTimeMillis()
        val today = Store.dayKey(now)
        val weekStart = Store.dayKey(now - 6L * 24 * 3600 * 1000)
        val week: Long = data.filter { it.first >= weekStart }.sumOf { it.second }
        val all: Long = data.sumOf { it.second }

        root.addView(row("Last 7 days   ${fmtDur(week)}") {}, lp(20))
        root.addView(row("All time   ${fmtDur(all)}") {}, lp(8))

        if (data.isEmpty()) {
            root.addView(tv("No sessions yet. Start one and it will show up here.", 14f, C.MUTED), lp(24))
        }

        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        data.forEach { (k, secs) ->
            val label = if (k == today) "Today" else try {
                outFmt.format(inFmt.parse(k)!!)
            } catch (e: Exception) { k }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, dp(14))
            }
            line.addView(tv(label, 16f, C.TEXT),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(tv(fmtDur(secs), 16f, C.ACCENT, true))
            root.addView(line, lp(if (k == data.first().first) 20 else 0))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
