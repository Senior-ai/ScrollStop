package com.senior.scrollstop

import android.app.Application

class ScrollStopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
