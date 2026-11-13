package com.nafiz.studylock

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object C {
    class Bg(val name: String, val bg: Int, val card: Int, val text: Int, val muted: Int, val danger: Int, val light: Boolean)

    private fun c(s: String) = Color.parseColor(s)

    val BGS = listOf(
        Bg("Midnight", c("#0F1115"), c("#181B21"), c("#F2F3F5"), c("#8A8F98"), c("#FF8A8A"), false),
        Bg("Black", c("#000000"), c("#121214"), c("#F2F3F5"), c("#8A8F98"), c("#FF8A8A"), false),
        Bg("Navy", c("#0D1426"), c("#16203A"), c("#EEF2FA"), c("#8B97B3"), c("#FF8A8A"), false),
        Bg("Forest", c("#0E1713"), c("#16241D"), c("#EEF5F0"), c("#87988D"), c("#FF8A8A"), false),
        Bg("Plum", c("#17111F"), c("#221A2E"), c("#F4EEF8"), c("#9A8FA6"), c("#FF8A8A"), false),
        Bg("Paper", c("#F4F1EA"), c("#E8E3D6"), c("#1B1B1B"), c("#77736A"), c("#C93C3C"), true)
    )
    val ACCENTS = listOf(
        "Mint" to c("#7CF0B5"), "Sky" to c("#7CC4F0"), "Lavender" to c("#B69CFF"), "Rose" to c("#FF8FB1"),
        "Amber" to c("#FFC857"), "Coral" to c("#FF8A6B"), "Lime" to c("#C5F06A"), "Silver" to c("#D8DADF")
    )

    var bgIndex = 0
    var accentIndex = 0
    var version = 0 // bumps whenever the theme changes so open screens know to rebuild

    var BG = BGS[0].bg
    var CARD = BGS[0].card
    var TEXT = BGS[0].text
    var MUTED = BGS[0].muted
    var DANGER = BGS[0].danger
    var ACCENT = ACCENTS[0].second
    var ON_ACCENT = c("#0F1115")
    var light = false

    fun accentFor(i: Int, onLight: Boolean): Int {
        val a = ACCENTS[i.coerceIn(0, ACCENTS.size - 1)].second
        return if (onLight) Color.rgb(
            (Color.red(a) * 0.62f).toInt(), (Color.green(a) * 0.62f).toInt(), (Color.blue(a) * 0.62f).toInt()
        ) else a
    }

    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    fun load(ctx: Context) {
        bgIndex = Store.bgIndex(ctx)
        accentIndex = Store.accentIndex(ctx)
        recompute()
    }

    private fun recompute() {
        val b = BGS[bgIndex.coerceIn(0, BGS.size - 1)]
        BG = b.bg; CARD = b.card; TEXT = b.text; MUTED = b.muted; DANGER = b.danger; light = b.light
        ACCENT = accentFor(accentIndex, b.light)
        val lum = (0.299f * Color.red(ACCENT) + 0.587f * Color.green(ACCENT) + 0.114f * Color.blue(ACCENT)) / 255f
        ON_ACCENT = if (lum > 0.55f) c("#0F1115") else Color.WHITE
        version++
    }
}

/** Paints the window, status bar and nav bar in the chosen background colour. */
@Suppress("DEPRECATION")
fun Activity.applyTheme() {
    window.setBackgroundDrawable(ColorDrawable(C.BG))
    window.statusBarColor = C.BG
    window.navigationBarColor = C.BG
    val d = window.decorView
    val bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    d.systemUiVisibility = if (C.light) d.systemUiVisibility or bits else d.systemUiVisibility and bits.inv()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

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
