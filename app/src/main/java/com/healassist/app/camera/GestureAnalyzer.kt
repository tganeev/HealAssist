package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

enum class HandGesture {
    NONE,        // Руки нет в кадре
    FIST,        // Кулак (Сжат)
    OPEN_PALM,   // Открытая ладонь (Разжат)
    UNKNOWN      // Что-то среднее (не засчитываем)
}

object GestureAnalyzer {
    fun analyze(result: HandLandmarkerResult): HandGesture {
        if (result.landmarks().isEmpty()) return HandGesture.NONE

        val hand = result.landmarks()[0]

        // Проверяем вытянутость 4 пальцев
        val isIndexExtended = isFingerExtended(hand[8], hand[5], hand[0])
        val isMiddleExtended = isFingerExtended(hand[12], hand[9], hand[0])
        val isRingExtended = isFingerExtended(hand[16], hand[13], hand[0])
        val isPinkyExtended = isFingerExtended(hand[20], hand[17], hand[0])

        val extendedCount = listOf(isIndexExtended, isMiddleExtended, isRingExtended, isPinkyExtended).count { it }

        return when {
            extendedCount == 4 -> HandGesture.OPEN_PALM // Все 4 пальца вытянуты
            extendedCount == 0 -> HandGesture.FIST      // Все 4 пальца сжаты
            else -> HandGesture.UNKNOWN                 // Промежуточное состояние
        }
    }

    private fun isFingerExtended(tip: NormalizedLandmark, mcp: NormalizedLandmark, wrist: NormalizedLandmark): Boolean {
        val distTip = hypot(tip.x() - wrist.x(), tip.y() - wrist.y())
        val distMcp = hypot(mcp.x() - wrist.x(), mcp.y() - wrist.y())
        return distTip > distMcp * 1.2f
    }
}