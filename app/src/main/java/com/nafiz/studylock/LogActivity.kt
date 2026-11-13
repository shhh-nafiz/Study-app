package com.nafiz.studylock

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LogActivity : Activity() {
    private var range = 7
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(body) })
        render()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(header("Study log"), lp())

        val data = Store.log(this)
        val totals = data.toMap()
        val todayKey = Store.dayKey(System.currentTimeMillis())

        // Range toggle
        val toggle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(7 to "7 days", 30 to "30 days").forEachIndexed { i, (n, label) ->
            val on = range == n
            toggle.addView(
                button(label, if (on) C.ACCENT else C.CARD, if (on) C.BG else C.MUTED, 8) {
                    range = n
                    render()
                },
                wlp(if (i == 0) 0 else 8)
            )
        }
        body.addView(toggle, lp(20))

        // Chart data: oldest -> newest
        val weekdayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val dayNumFmt = SimpleDateFormat("d", Locale.getDefault())
        val fullFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val bars = ArrayList<BarChartView.Bar>()
        for (i in range - 1 downTo 0) {
            val d = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }.time
            val secs = totals[Store.dayKey(d.time)] ?: 0L
            val x = when {
                range == 7 -> if (i == 0) "Today" else weekdayFmt.format(d)
                i % 5 == 0 -> dayNumFmt.format(d)
                else -> ""
            }
            val full = (if (i == 0) "Today" else fullFmt.format(d)) + "   ·   " + fmtDur(secs)
            bars.add(BarChartView.Bar(x, if (range == 7) fmtDur(secs) else "", full, secs))
        }

        // Summary
        val total = bars.sumOf { it.secs }
        val best = bars.maxOf { it.secs }
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("Total", fmtDur(total)), wlp(0))
        stats.addView(statCard("Daily avg", fmtDur(total / range)), wlp(8))
        stats.addView(statCard("Best day", fmtDur(best)), wlp(8))
        body.addView(stats, lp(16))

        // Chart card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(C.CARD)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val caption = tv(bars.last().full, 14f, C.TEXT, true)
        card.addView(caption, lp())
        val chart = BarChartView(this)
        chart.setBars(bars, bars.size - 1)
        chart.onPick = { caption.text = bars[it].full }
        card.addView(chart, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(220)).apply { topMargin = dp(8) })
        body.addView(card, lp(12))

        // Dated history
        body.addView(tv("History", 18f, C.TEXT, true), lp(28))
        if (data.isEmpty()) {
            body.addView(tv("No sessions yet. Start one and it will show up here.", 14f, C.MUTED), lp(12))
            return
        }
        val byDay = Store.sessions(this).groupBy { Store.dayKey(it.start) }
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val running = Store.isRunning(this)

        data.forEachIndexed { idx, (k, secs) ->
            val label = if (k == todayKey) "Today" else try {
                outFmt.format(inFmt.parse(k)!!)
            } catch (e: Exception) { k }
            body.addView(line(label, fmtDur(secs), 16f, C.TEXT, C.ACCENT, true), lp(if (idx == 0) 8 else 14))

            if (running && Store.dayKey(Store.startMs(this)) == k) {
                val st = Store.startMs(this)
                val el = (System.currentTimeMillis() - st) / 1000
                body.addView(
                    line("${clockFmt.format(Date(st))} – now  ·  in progress", fmtDur(el), 13f, C.ACCENT, C.MUTED, false),
                    lp(2)
                )
            }
            byDay[k]?.forEach { s ->
                val end = clockFmt.format(Date(s.start + s.secs * 1000))
                val early = if (s.planned - s.secs > 30) "  ·  ended early" else ""
                body.addView(
                    line("${clockFmt.format(Date(s.start))} – $end$early", fmtDur(s.secs), 13f, C.MUTED, C.MUTED, false),
                    lp(2)
                )
            }
        }
    }

    private fun statCard(title: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(C.CARD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(tv(title, 12f, C.MUTED), lp())
            addView(tv(value, 18f, C.TEXT, true), lp(4))
        }

    private fun line(left: String, right: String, sp: Float, lc: Int, rc: Int, bold: Boolean): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        l.setPadding(0, dp(6), 0, dp(6))
        l.addView(tv(left, sp, lc, bold),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        l.addView(tv(right, sp, rc, bold))
        return l
    }
}
