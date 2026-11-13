package com.nafiz.studylock

import android.app.Application
import android.util.Log

/** Saves any crash so the app can show it on the next launch. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { Store.setCrash(this, Log.getStackTraceString(e)) } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }
}
