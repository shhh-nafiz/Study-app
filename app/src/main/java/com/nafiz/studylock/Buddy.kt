package com.nafiz.studylock

import android.content.Context
import java.util.Calendar

/** The quiet "study buddy": one short line of text, nothing animated. */
object Buddy {
    /** Consecutive days (ending today, or yesterday if today has no study yet) with at least 10 minutes. */
    fun streak(c: Context): Int {
        val days = Store.log(c).filter { it.second >= 600 }.map { it.first }.toHashSet()
        val cal = Calendar.getInstance()
        if (Store.dayKey(cal.timeInMillis) !in days) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (Store.dayKey(cal.timeInMillis) in days) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    fun line(c: Context): String {
        val goal = Store.goalMin(c) * 60L
        val today = Store.todaySeconds(c)
        val streak = streak(c)
        val base = when {
            goal > 0 && today >= goal -> "Daily goal reached. Anything more is a bonus."
            goal > 0 && today > 0 -> "${fmtDur(goal - today)} to your daily goal."
            else -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> "Good morning. Ready when you are."
                    hour < 18 -> "Good afternoon. One focused block?"
                    else -> "Good evening. One more block?"
                }
            }
        }
        return if (streak >= 2) "$base  ·  $streak-day streak" else base
    }
}
