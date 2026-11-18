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
        val fingers: List<Triple<Int, Int, Int>> = listOf(
            Triple(8, 5, 0), Triple(12, 9, 0), Triple(16, 13, 0), Triple(20, 17, 0)
        )
        var straightCount = 0; var clawCount = 0; var fistCount = 0
        for ((tipIdx, mcpIdx, wristIdx) in fingers) {
            val tipDist = dist(hand[tipIdx], hand[wristIdx])
            val mcpDist = dist(hand[mcpIdx], hand[wristIdx])
            if (mcpDist < 0.001f) continue
            val ratio = tipDist / mcpDist
            when {
                ratio < 0.9f -> fistCount++
                ratio < 1.6f -> clawCount++
                else -> straightCount++
            }
        }
        return when {
            straightCount >= 3 -> HandGesture.OPEN_PALM
            clawCount >= 2 && straightCount == 0 -> HandGesture.FIST
            fistCount >= 3 -> HandGesture.UNKNOWN
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

    /** "Пианино" (ex6) */
    fun analyzePiano(hand: List<NormalizedLandmark>): Int {
        if (hand.size < 21) return -1

        val wristY = hand[0].y()
        // Координаты кончиков пальцев и большого пальца
        val tips = listOf(
            8 to 0,   // Указательный
            12 to 1,  // Средний
            16 to 2,  // Безымянный
            20 to 3,  // Мизинец
            4 to 4    // Большой
        )

        var maxTipY = -1f
        var touchingFingerIdx = -1

        // Порог: палец должен быть ниже запястья минимум на 0.03 (3% высоты экрана)
        val touchThreshold = 0.03f

        for ((tipIdx, fingerIndex) in tips) {
            val tipY = hand[tipIdx].y()
            // Проверяем, что палец реально ниже запястья и является самым нижним
            if (tipY > wristY + touchThreshold && tipY > maxTipY) {
                maxTipY = tipY
                touchingFingerIdx = fingerIndex
            }
        }

        return touchingFingerIdx
    }

    private fun isFingerExtended(tip: NormalizedLandmark, mcp: NormalizedLandmark, wrist: NormalizedLandmark): Boolean {
        val distTip = hypot(tip.x() - wrist.x(), tip.y() - wrist.y())
        val distMcp = hypot(mcp.x() - wrist.x(), mcp.y() - wrist.y())
        return distTip > distMcp * 1.2f
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())
}