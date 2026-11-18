package com.healassist.app

import android.app.Application
import com.healassist.app.data.CrashLogger
import com.healassist.app.utils.AppLogger

class HealAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализируем TTS при старте приложения
        TtsManager.init(this)
        // Инициализируем файловый логгер (перехват крашей + события)
        AppLogger.init(this)
        // Инициализируем логгер крашей в Room (для отображения в "Краши")
        CrashLogger.init(this)
    }

    override fun onTerminate() {
        TtsManager.shutdown()
        super.onTerminate()
    }
}