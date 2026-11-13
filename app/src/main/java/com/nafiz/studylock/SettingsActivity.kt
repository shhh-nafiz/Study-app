package com.nafiz.studylock

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class SettingsActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var body: LinearLayout
    private var preview: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(body) })
        render()
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    private fun restyle() {
        C.load(this)
        applyTheme()
        render()
    }

    private fun section(title: String) = tv(title, 13f, C.MUTED).apply { setPadding(0, 0, 0, 0) }

    private fun render() {
        body.removeAllViews()
        body.addView(header("Settings"), lp())

        // ---- Accent ----
        body.addView(section("ACCENT COLOUR"), lp(28))
        val accentCells = C.ACCENTS.mapIndexed { i, (name, _) ->
            swatch(C.accentFor(i, C.light), name, i == C.accentIndex) {
                Store.setAccentIndex(this, i); restyle()
            }
        }
        body.addView(grid(accentCells, 4), lp(10))

        // ---- Background ----
        body.addView(section("BACKGROUND"), lp(28))
        val bgCells = C.BGS.mapIndexed { i, b ->
            swatch(b.bg, b.name, i == C.bgIndex) { Store.setBgIndex(this, i); restyle() }
        }
        body.addView(grid(bgCells, 3), lp(10))

        // ---- Alarm ----
        body.addView(section("ALARM"), lp(28))
        body.addView(row("Sound   ·   ${soundTitle()}   ›") { pickSound() }, lp(10))
        body.addView(row("Vibration   ·   ${if (Store.vibrate(this)) "On" else "Off"}") {
            Store.setVibrate(this, !Store.vibrate(this)); render()
        }, lp(8))
        body.addView(tv("Rings for", 14f, C.MUTED), lp(14))
        body.addView(
            chips(listOf("30 sec" to 30, "1 min" to 60, "2 min" to 120, "5 min" to 300), Store.ringSecs(this)) {
                Store.setRingSecs(this, it); render()
            }, lp(8)
        )
        body.addView(row("Test alarm") { testAlarm() }, lp(14))

        // ---- Study ----
        body.addView(section("STUDY"), lp(28))
        body.addView(tv("Daily goal", 14f, C.MUTED), lp(10))
        body.addView(
            chips(listOf("Off" to 0, "1h" to 60, "2h" to 120, "3h" to 180, "4h" to 240), Store.goalMin(this)) {
                Store.setGoalMin(this, it); render()
            }, lp(8)
        )
        body.addView(row("Buddy messages   ·   ${if (Store.buddy(this)) "On" else "Off"}") {
            Store.setBuddy(this, !Store.buddy(this)); render()
        }, lp(14))
        body.addView(
            tv("A single quiet line on the home screen: progress to your goal and your streak.", 12f, C.MUTED),
            lp(6)
        )
    }

    private fun swatch(color: Int, name: String, selected: Boolean, onClick: () -> Unit): LinearLayout {
        val dot = View(this)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(if (selected) 3 else 1), if (selected) C.TEXT else C.MUTED)
        }
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.setPadding(0, dp(6), 0, dp(6))
        cell.addView(dot, LinearLayout.LayoutParams(dp(44), dp(44)))
        cell.addView(tv(name, 11f, if (selected) C.TEXT else C.MUTED).apply { gravity = Gravity.CENTER }, lp(4))
        cell.setOnClickListener { onClick() }
        return cell
    }

    private fun grid(cells: List<View>, perRow: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        cells.chunked(perRow).forEach { rowCells ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until perRow) {
                val v: View = rowCells.getOrNull(i) ?: View(this)
                r.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            col.addView(r, lp())
        }
        return col
    }

    private fun chips(options: List<Pair<String, Int>>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        options.forEachIndexed { i, (label, value) ->
            val on = value == selected
            r.addView(
                button(label, if (on) C.ACCENT else C.CARD, if (on) C.ON_ACCENT else C.MUTED, 4) { onPick(value) },
                wlp(if (i == 0) 0 else 8)
            )
        }
        return r
    }

    // ---------- alarm sound ----------

    private fun soundTitle(): String {
        val s = Store.alarmUri(this)
        if (s == "silent") return "Silent"
        if (s.isEmpty()) return "Default alarm"
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(s)).getTitle(this)
        } catch (e: Exception) { "Custom" }
    }

    private fun pickSound() {
        val s = Store.alarmUri(this)
        val existing: Uri? = if (s.isNotEmpty() && s != "silent") Uri.parse(s) else null
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        try {
            startActivityForResult(i, 7)
        } catch (e: Exception) {
            Toast.makeText(this, "No sound picker found on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Store.setAlarmUri(
                this,
                when {
                    uri == null -> "silent"
                    uri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) -> ""
                    else -> uri.toString()
                }
            )
            render()
        }
    }

    @Suppress("DEPRECATION")
    private fun testAlarm() {
        if (preview?.isPlaying == true) { stopPreview(); return }
        val uri = Store.alarmSound(this)
        if (uri == null) {
            Toast.makeText(this, "Alarm is set to silent", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val r = RingtoneManager.getRingtone(this, uri)
                r.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                r.play()
                preview = r
                h.postDelayed({ stopPreview() }, 4000)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't play that sound", Toast.LENGTH_SHORT).show()
            }
        }
        if (Store.vibrate(this)) {
            try {
                getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) { }
        }
    }

    private fun stopPreview() {
        h.removeCallbacksAndMessages(null)
        try { preview?.stop() } catch (e: Exception) { }
        preview = null
    }
}
