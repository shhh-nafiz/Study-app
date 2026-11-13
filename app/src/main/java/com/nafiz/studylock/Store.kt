package com.nafiz.studylock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One finished study session. [planned] is the length that was originally set. */
data class Session(val start: Long, val secs: Long, val planned: Long)

object Store {
    private const val RING_WINDOW_MS = 150_000L
    private const val MAX_SESSIONS = 1000

    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("studylock", Context.MODE_PRIVATE)

    fun blocked(c: Context): Set<String> =
        HashSet(p(c).getStringSet("blocked", emptySet()) ?: emptySet())

    fun setBlocked(c: Context, s: Set<String>) {
        p(c).edit().putStringSet("blocked", HashSet(s)).apply()
    }

    fun lastMinutes(c: Context): Int = p(c).getInt("minutes", 45)
    fun setLastMinutes(c: Context, m: Int) { p(c).edit().putInt("minutes", m).apply() }

    fun startMs(c: Context): Long = p(c).getLong("start", 0L)
    fun endMs(c: Context): Long = p(c).getLong("end", 0L)

    fun isRunning(c: Context): Boolean {
        val e = endMs(c)
        return e != 0L && System.currentTimeMillis() < e
    }

    @Synchronized
    fun begin(c: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        p(c).edit()
            .putLong("start", now)
            .putLong("end", now + minutes * 60_000L)
            .putBoolean("alarm", true) // an alarm is owed when this session runs out
            .apply()
    }

    /** Returns true exactly once per session, so the alarm can never ring twice. */
    @Synchronized
    fun consumeAlarm(c: Context): Boolean {
        val owed = p(c).getBoolean("alarm", false)
        if (owed) p(c).edit().putBoolean("alarm", false).apply()
        return owed
    }

    fun clearAlarm(c: Context) { p(c).edit().putBoolean("alarm", false).apply() }

    /** True while the "time's up" alarm is sounding. Expires on its own as a safety net. */
    fun isRinging(c: Context): Boolean {
        val since = p(c).getLong("ringing_since", 0L)
        return since != 0L && System.currentTimeMillis() - since < RING_WINDOW_MS
    }

    fun setRinging(c: Context, on: Boolean) {
        p(c).edit().putLong("ringing_since", if (on) System.currentTimeMillis() else 0L).apply()
    }

    /** Ends the session (natural end or manual stop) and saves the time studied to the log. */
    @Synchronized
    fun finish(c: Context) {
        val s = startMs(c)
        val e = endMs(c)
        if (e == 0L) return
        val stop = minOf(System.currentTimeMillis(), e)
        val secs = (stop - s) / 1000
        if (secs > 0) addLog(c, s, secs, (e - s) / 1000)
        p(c).edit().remove("start").remove("end").apply()
    }

    fun settle(c: Context) {
        val e = endMs(c)
        if (e != 0L && System.currentTimeMillis() >= e) finish(c)
    }

    fun dayKey(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms)) // phone's local time zone

    private fun readLog(c: Context): JSONObject =
        try { JSONObject(p(c).getString("log", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun readSessions(c: Context): JSONArray =
        try { JSONArray(p(c).getString("sessions", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }

    @Synchronized
    private fun addLog(c: Context, startMs: Long, secs: Long, planned: Long) {
        // Per-day totals (this is what the chart reads).
        val log = readLog(c)
        val k = dayKey(startMs)
        log.put(k, log.optLong(k, 0L) + secs)

        // Individual sessions (this is the dated history list).
        val all = readSessions(c)
        all.put(JSONObject().put("s", startMs).put("d", secs).put("p", planned))
        val keep = if (all.length() > MAX_SESSIONS) {
            val trimmed = JSONArray()
            for (i in all.length() - MAX_SESSIONS until all.length()) trimmed.put(all.get(i))
            trimmed
        } else all

        p(c).edit()
            .putString("log", log.toString())
            .putString("sessions", keep.toString())
            .apply()
    }

    /** Day -> seconds studied, newest first. Includes the session in progress. */
    fun log(c: Context): List<Pair<String, Long>> {
        val o = readLog(c)
        val m = HashMap<String, Long>()
        val it = o.keys()
        while (it.hasNext()) { val k = it.next(); m[k] = o.optLong(k, 0L) }
        if (isRunning(c)) {
            val k = dayKey(startMs(c))
            m[k] = (m[k] ?: 0L) + (System.currentTimeMillis() - startMs(c)) / 1000
        }
        return m.toList().sortedByDescending { it.first }
    }

    /** Finished sessions, newest first. Sessions from before v1.1 only exist as day totals. */
    fun sessions(c: Context): List<Session> {
        val a = readSessions(c)
        val out = ArrayList<Session>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Session(o.optLong("s"), o.optLong("d"), o.optLong("p")))
        }
        return out.sortedByDescending { it.start }
    }

    fun todaySeconds(c: Context): Long {
        val k = dayKey(System.currentTimeMillis())
        return log(c).firstOrNull { it.first == k }?.second ?: 0L
    }
}
