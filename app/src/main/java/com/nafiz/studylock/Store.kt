package com.nafiz.studylock

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Store {
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
        p(c).edit().putLong("start", now).putLong("end", now + minutes * 60_000L).apply()
    }

    /** Ends the session (natural end or manual stop) and saves the time studied to the log. */
    @Synchronized
    fun finish(c: Context) {
        val s = startMs(c)
        val e = endMs(c)
        if (e == 0L) return
        val stop = minOf(System.currentTimeMillis(), e)
        val secs = (stop - s) / 1000
        if (secs > 0) addLog(c, s, secs)
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

    @Synchronized
    private fun addLog(c: Context, startMs: Long, secs: Long) {
        val log = readLog(c)
        val k = dayKey(startMs)
        log.put(k, log.optLong(k, 0L) + secs)
        p(c).edit().putString("log", log.toString()).apply()
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

    fun todaySeconds(c: Context): Long {
        val k = dayKey(System.currentTimeMillis())
        return log(c).firstOrNull { it.first == k }?.second ?: 0L
    }
}
