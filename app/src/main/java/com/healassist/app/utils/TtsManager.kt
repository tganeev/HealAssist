package com.healassist.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsManager {
    private var tts: TextToSpeech? = null
    var isReady = false
        private set

    // Инициализация (вызывается один раз при старте приложения)
    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ru", "RU"))
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        isReady = true
                    }
                }
            }
        }
    }

    // Метод для произношения текста
    fun speak(text: String, flushQueue: Boolean = true) {
        if (!isReady) return
        val mode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "tts_${System.currentTimeMillis()}")
    }

    // Остановить речь (например, при выходе)
    fun stop() {
        tts?.stop()
    }

    // Освободить ресурсы (вызывать при полном закрытии приложения)
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}