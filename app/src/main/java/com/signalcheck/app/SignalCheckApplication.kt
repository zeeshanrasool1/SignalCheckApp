package com.signalcheck.app

import android.app.Application
import java.io.File

class SignalCheckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(filesDir, "last_crash.txt")
                file.writeText(android.util.Log.getStackTraceString(throwable))
            } catch (e: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
