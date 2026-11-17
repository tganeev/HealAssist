package com.healassist.app

import android.app.Application

class HealAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализируем TTS при старте приложения
        TtsManager.init(this)
        // Инициализация логгера крашей (если он у вас был)
        // CrashLogger.init(this)
    }
}