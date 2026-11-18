package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

enum class HandGesture {
    NONE,
    FIST,
    OPEN_PALM,
    UNKNOWN
}

object GestureAnalyzer {

    fun analyze(result: HandLandmarkerResult): HandGesture {
        if (result.landmarks().isEmpty()) return HandGesture.NONE
        return analyze(result.landmarks()[0])
    }

    fun analyze(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE

        val isIndexExtended = isFingerExtended(hand[8], hand[5], hand[0])
        val isMiddleExtended = isFingerExtended(hand[12], hand[9], hand[0])
        val isRingExtended = isFingerExtended(hand[16], hand[13], hand[0])
        val isPinkyExtended = isFingerExtended(hand[20], hand[17], hand[0])

        val extendedCount = listOf(
            isIndexExtended, isMiddleExtended, isRingExtended, isPinkyExtended
        ).count { it }

        return when {
            extendedCount == 4 -> HandGesture.OPEN_PALM
            extendedCount == 0 -> HandGesture.FIST
            else -> HandGesture.UNKNOWN
        }
    }

    /**
     * "Веер" (ex2) с гистерезисом.
     * @param previous предыдущий жест (для гистерезиса на границе порога).
     */
    fun analyzeFan(
        hand: List<NormalizedLandmark>,
        previous: HandGesture = HandGesture.UNKNOWN
    ): HandGesture {
        if (hand.size < 21) return HandGesture.NONE

        val totalSpread = dist(hand[8], hand[12]) + dist(hand[12], hand[16]) + dist(hand[16], hand[20])
        val palmLength = dist(hand[0], hand[9])
        if (palmLength < 0.01f) return HandGesture.UNKNOWN

        val spreadRatio = totalSpread / palmLength

        val isIndexExtended = isFingerExtended(hand[8], hand[5], hand[0])
        val isPinkyExtended = isFingerExtended(hand[20], hand[17], hand[0])
        if (!isIndexExtended || !isPinkyExtended) return HandGesture.UNKNOWN

        // Гистерезис: держим предыдущее состояние в "серой зоне"
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

    /**
     * "Когти" (ex3).
     * ratio = dist(tip, wrist) / dist(mcp, wrist) для каждого пальца:
     *   ratio < 0.9f   -> кулак
     *   ratio < 1.6f   -> "коготь"
     *   ratio >= 1.6f  -> прямой палец
     *
     * straightCount >= 3                          -> OPEN_PALM (ладонь прямая)
     * clawCount >= 2 && straightCount == 0        -> FIST      ("когти")
     * fistCount >= 3                              -> UNKNOWN   (кулак, не когти)
     * иначе                                       -> UNKNOWN
     */
    fun analyzeClaw(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE

        val fingers = listOf(
            Triple(8, 5, 0),
            Triple(12, 9, 0),
            Triple(16, 13, 0),
            Triple(20, 17, 0)
        )

        var straightCount = 0
        var clawCount = 0
        var fistCount = 0

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

    private fun isFingerExtended(
        tip: NormalizedLandmark,
        mcp: NormalizedLandmark,
        wrist: NormalizedLandmark
    ): Boolean {
        val distTip = hypot(tip.x() - wrist.x(), tip.y() - wrist.y())
        val distMcp = hypot(mcp.x() - wrist.x(), mcp.y() - wrist.y())
        return distTip > distMcp * 1.2f
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())
}