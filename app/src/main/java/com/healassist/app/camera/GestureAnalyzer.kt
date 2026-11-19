package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

enum class HandGesture {
    NONE, FIST, OPEN_PALM, UNKNOWN
}

object GestureAnalyzer {

    fun analyze(result: HandLandmarkerResult): HandGesture {
        if (result.landmarks().isEmpty()) return HandGesture.NONE
        return analyze(result.landmarks()[0])
    }

    fun analyze(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val extendedCount = listOf(8, 12, 16, 20).count { tipIdx ->
            isFingerExtended(hand[tipIdx], hand[tipIdx - 3], hand[0])
        }
        return when {
            extendedCount == 4 -> HandGesture.OPEN_PALM
            extendedCount == 0 -> HandGesture.FIST
            else -> HandGesture.UNKNOWN
        }
    }

    /** "Веер" (ex2) */
    fun analyzeFan(hand: List<NormalizedLandmark>, previous: HandGesture = HandGesture.UNKNOWN): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val totalSpread = dist(hand[8], hand[12]) + dist(hand[12], hand[16]) + dist(hand[16], hand[20])
        val palmLength = dist(hand[0], hand[9])
        if (palmLength < 0.01f) return HandGesture.UNKNOWN
        val spreadRatio = totalSpread / palmLength
        if (!isFingerExtended(hand[8], hand[5], hand[0]) || !isFingerExtended(hand[20], hand[17], hand[0]))
            return HandGesture.UNKNOWN

        return when {
            spreadRatio > 1.80f -> HandGesture.OPEN_PALM
            spreadRatio < 1.60f -> HandGesture.FIST
            else -> when (previous) {
                HandGesture.OPEN_PALM -> HandGesture.OPEN_PALM
                HandGesture.FIST -> HandGesture.FIST
                else -> if (spreadRatio > 1.70f) HandGesture.OPEN_PALM else HandGesture.FIST
            }
        }
    }

    /** "Когти" (ex3) */
    fun analyzeClaw(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE

        val joints = listOf(
            Triple(5, 6, 8), Triple(9, 10, 12),
            Triple(13, 14, 16), Triple(17, 18, 20)
        )

        var straightCount = 0
        var clawCount = 0

        for ((mcpIdx, pipIdx, tipIdx) in joints) {
            val ang = angle(hand[mcpIdx], hand[pipIdx], hand[tipIdx])
            when {
                ang > 150f -> straightCount++
                ang >= 75f && ang <= 105f -> clawCount++
            }
        }

        return when {
            straightCount >= 3 -> HandGesture.OPEN_PALM
            clawCount >= 2 -> HandGesture.FIST // Используем FIST как триггер для счетчика
            else -> HandGesture.UNKNOWN
        }
    }

    /** "Подушечки" (ex4) */
    fun isThumbTouching(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false
        val thumbTip = hand[4]
        val fingerTips = listOf(8, 12, 16, 20)
        val touchThreshold = 0.05f
        for (tipIdx in fingerTips) {
            if (dist(thumbTip, hand[tipIdx]) < touchThreshold) return true
        }
        return false
    }

    /** "Стена" (ex5) */
    fun analyzeWall(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val fingers: List<Pair<Int, Int>> = listOf(
            Pair(8, 5), Pair(12, 9), Pair(16, 13), Pair(20, 17)
        )
        var liftedCount = 0
        for ((tipIdx, mcpIdx) in fingers) {
            if (hand[tipIdx].y() < hand[mcpIdx].y()) liftedCount++
        }
        return when {
            liftedCount >= 3 -> HandGesture.OPEN_PALM
            liftedCount <= 1 -> HandGesture.FIST
            else -> HandGesture.UNKNOWN
        }
    }

    /**
     * Анализ упражнения "Пианино".
     * Возвращает индекс пальца (0-4), который касается стола.
     * Индексы: 0=Указательный, 1=Средний, 2=Безымянный, 3=Мизинец, 4=Большой
     */
    fun analyzePiano(hand: List<NormalizedLandmark>): Int {
        if (hand.size < 21) return -1

        val wristY = hand[0].y()

        // Координаты кончиков пальцев и большого пальца
        // Порядок важен для приоритета проверки
        val fingers = listOf(
            8 to 0,   // Указательный
            12 to 1,  // Средний
            16 to 2,  // Безымянный
            20 to 3,  // Мизинец
            4 to 4    // Большой
        )

        // Порог снижен до 0.025 для лучшей чувствительности при плоском положении руки
        val touchThreshold = 0.025f

        var bestFingerIdx = -1
        var maxTipY = -1f

        for ((tipIdx, fingerIndex) in fingers) {
            val tipY = hand[tipIdx].y()

            // Палец должен быть ниже запястья на величину порога
            if (tipY > wristY + touchThreshold) {
                // Если этот палец ниже всех найденных ранее ИЛИ
                // если он является целевым для текущего шага (приоритет)
                if (tipY > maxTipY || bestFingerIdx == -1) {
                    maxTipY = tipY
                    bestFingerIdx = fingerIndex
                }
            }
        }

        return bestFingerIdx
    }

    private fun isFingerExtended(tip: NormalizedLandmark, mcp: NormalizedLandmark, wrist: NormalizedLandmark): Boolean {
        val distTip = hypot(tip.x() - wrist.x(), tip.y() - wrist.y())
        val distMcp = hypot(mcp.x() - wrist.x(), mcp.y() - wrist.y())
        return distTip > distMcp * 1.2f
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())

    private fun angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
        val abX = a.x() - b.x(); val abY = a.y() - b.y()
        val cbX = c.x() - b.x(); val cbY = c.y() - b.y()

        val dot = abX * cbX + abY * cbY
        val magAB = hypot(abX, abY)
        val magCB = hypot(cbX, cbY)

        if (magAB < 0.001f || magCB < 0.001f) return 0f

        val cosAngle = (dot / (magAB * magCB)).coerceIn(-1f, 1f)
        return Math.toDegrees(Math.acos(cosAngle.toDouble())).toFloat()
    }

    /**
     * Проверяет, достигнута ли форма "когтя" (угол ~90° в PIP суставе).
     * Возвращает true, если хотя бы 2 пальца согнуты под углом 75-105°.
     */
    fun isClawReached(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false

        // Индексы: MCP, PIP, TIP для каждого пальца
        val joints = listOf(
            Triple(5, 6, 8),   // Указательный
            Triple(9, 10, 12), // Средний
            Triple(13, 14, 16),// Безымянный
            Triple(17, 18, 20) // Мизинец
        )

        var clawCount = 0
        for ((mcpIdx, pipIdx, tipIdx) in joints) {
            val ang = angle(hand[mcpIdx], hand[pipIdx], hand[tipIdx])
            // Диапазон 75-105 градусов допускает небольшую погрешность трекинга
            if (ang >= 75f && ang <= 105f) {
                clawCount++
            }
        }

        // Считаем "когтем", если минимум 2 пальца согнуты правильно
        return clawCount >= 2
    }


}