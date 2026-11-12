package com.nafiz.studylock

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object C {
    val BG = Color.parseColor("#0F1115")
    val CARD = Color.parseColor("#181B21")
    val TEXT = Color.parseColor("#F2F3F5")
    val MUTED = Color.parseColor("#8A8F98")
    val ACCENT = Color.parseColor("#7CF0B5")
    val DANGER = Color.parseColor("#FF8A8A")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.tv(s: String, sp: Float, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.pill(bg: Int, radiusDp: Int = 16): GradientDrawable =
    GradientDrawable().apply { setColor(bg); cornerRadius = dp(radiusDp).toFloat() }

fun Context.button(s: String, bg: Int, fg: Int, padH: Int = 20, onClick: () -> Unit): TextView =
    tv(s, 16f, fg, true).apply {
        gravity = Gravity.CENTER
        background = pill(bg)
        setPadding(dp(padH), dp(16), dp(padH), dp(16))
        setOnClickListener { onClick() }
    }

fun Context.row(s: String, onClick: () -> Unit): TextView =
    tv(s, 16f, C.TEXT).apply {
        background = pill(C.CARD)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        setOnClickListener { onClick() }
    }

fun Context.lp(top: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

fun Context.wlp(left: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { leftMargin = dp(left) }

fun Activity.header(title: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val back = tv("‹", 30f, C.TEXT).apply {
            setPadding(0, 0, dp(16), dp(4))
            setOnClickListener { finish() }
        }
        addView(back)
        addView(tv(title, 22f, C.TEXT, true))
    }

fun fmtClock(sec: Long): String {
    val s = maxOf(0L, sec)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x)
    else String.format(Locale.US, "%02d:%02d", m, x)
}

fun fmtDur(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        sec > 0 -> "<1m"
        else -> "0m"
    }
}
