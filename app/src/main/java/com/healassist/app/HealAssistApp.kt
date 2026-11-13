package com.healassist.app

import android.app.Application
import com.healassist.app.data.CrashLogger

class HealAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
    }
}