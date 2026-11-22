package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot
import kotlin.math.sqrt

enum class HandGesture {
    NONE, FIST, OPEN_PALM, UNKNOWN
}

object GestureAnalyzer {

    fun analyze(result: HandLandmarkerResult): HandGesture {
        if (result.landmarks().isEmpty()) return HandGesture.NONE
        return analyze(result.landmarks()[0])
    }

    fun analyze(hand: List<NormalizedLandmark>, extensionRatio: Float = 1.20f): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val extendedCount = listOf(8, 12, 16, 20).count { tipIdx ->
            isFingerExtended(hand[tipIdx], hand[tipIdx - 3], hand[0], extensionRatio)
        }
        return when {
            extendedCount == 4 -> HandGesture.OPEN_PALM
            extendedCount == 0 -> HandGesture.FIST
            else -> HandGesture.UNKNOWN
        }
    }

    fun analyzeFan(
        hand: List<NormalizedLandmark>,
        previous: HandGesture = HandGesture.UNKNOWN,
        openRatio: Float = 1.70f,
        closeRatio: Float = 1.52f,
        extensionRatio: Float = 1.20f,
    ): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val totalSpread = dist(hand[8], hand[12]) + dist(hand[12], hand[16]) + dist(hand[16], hand[20])
        val palmLength = dist(hand[0], hand[9])
        if (palmLength < 0.01f) return HandGesture.UNKNOWN
        val spreadRatio = totalSpread / palmLength
        if (!isFingerExtended(hand[8], hand[5], hand[0], extensionRatio) ||
            !isFingerExtended(hand[20], hand[17], hand[0], extensionRatio)
        ) return HandGesture.UNKNOWN
        return when {
            spreadRatio > openRatio -> HandGesture.OPEN_PALM
            spreadRatio < closeRatio -> HandGesture.FIST
            else -> when (previous) {
                HandGesture.OPEN_PALM -> HandGesture.OPEN_PALM
                HandGesture.FIST -> HandGesture.FIST
                else -> if (spreadRatio > (openRatio + closeRatio) / 2f) {
                    HandGesture.OPEN_PALM
                } else {
                    HandGesture.FIST
                }
            }
        }
    }

    fun analyzeClaw(
        hand: List<NormalizedLandmark>,
        fistRatio: Float = 0.90f,
        straightRatio: Float = 1.80f,
    ): HandGesture {
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
                ratio < fistRatio -> fistCount++
                ratio < straightRatio -> clawCount++
                else -> straightCount++
            }
        }
        return when {
            straightCount >= 3 -> HandGesture.OPEN_PALM
            clawCount >= 2 && straightCount <= 1 -> HandGesture.FIST
            fistCount >= 3 -> HandGesture.UNKNOWN
            else -> HandGesture.UNKNOWN
        }
    }

    fun isThumbTouching(hand: List<NormalizedLandmark>, touchThreshold: Float = 0.05f): Boolean {
        if (hand.size < 21) return false
        val thumbTip = hand[4]
        val fingerTips = listOf(8, 12, 16, 20)
        for (tipIdx in fingerTips) {
            if (dist(thumbTip, hand[tipIdx]) < touchThreshold) return true
        }
        return false
    }

    fun analyzePiano(hand: List<NormalizedLandmark>, touchThreshold: Float = 0.025f): Int {
        if (hand.size < 21) return -1
        val wristY = hand[0].y()
        val tips = listOf(8 to 0, 12 to 1, 16 to 2, 20 to 3, 4 to 4)
        var maxTipY = -1f
        var touchingFingerIdx = -1
        for ((tipIdx, fingerIndex) in tips) {
            val tipY = hand[tipIdx].y()
            if (tipY > wristY + touchThreshold && tipY > maxTipY) {
                maxTipY = tipY
                touchingFingerIdx = fingerIndex
            }
        }
        return touchingFingerIdx
    }

    // ============================================================
    // ex5 "Стена"
    // ============================================================

    fun getPalmSize(hand: List<NormalizedLandmark>): Float {
        if (hand.size < 21) return 0f
        return dist(hand[0], hand[9]).coerceAtLeast(0.001f)
    }

    private val FINGER_PAIRS = listOf(
        8 to 5, 12 to 9, 16 to 13, 20 to 17, 4 to 2
    )

    fun getFingerLifts(hand: List<NormalizedLandmark>): FloatArray {
        if (hand.size < 21) return FloatArray(5) { 0f }
        val palmSize = getPalmSize(hand)
        return FloatArray(5) { i ->
            val (tipIdx, mcpIdx) = FINGER_PAIRS[i]
            (hand[mcpIdx].y() - hand[tipIdx].y()) / palmSize
        }
    }

    fun getPalmSizeWorld(hand: List<Landmark>): Float {
        if (hand.size < 21) return 0f
        return dist3D(hand[0], hand[9]).coerceAtLeast(0.001f)
    }

    fun getFingerLiftsWorld(hand: List<Landmark>): FloatArray {
        if (hand.size < 21) return FloatArray(5)
        val palmSize = getPalmSizeWorld(hand)
        return FloatArray(5) { i ->
            val (tipIdx, mcpIdx) = FINGER_PAIRS[i]
            (hand[mcpIdx].y() - hand[tipIdx].y()) / palmSize
        }
    }

    /**
     * Относительные lift'ы, нормализованные ТОЛЬКО по 4 "обычным" пальцам
     * (I, M, R, P — индексы 0..3). Большой (idx=4) из медианы исключён,
     * потому что его MCP анатомически ниже и raw всегда завышен.
     */
    fun getRelativeLifts4(hand: List<NormalizedLandmark>): FloatArray {
        val raw = getFingerLifts(hand)
        if (raw.size < 5) return raw
        val ordinary = floatArrayOf(raw[0], raw[1], raw[2], raw[3])
        val sorted = ordinary.sorted()
        val median = (sorted[1] + sorted[2]) / 2f  // медиана 4 значений = среднее двух средних
        return FloatArray(5) { raw[it] - median }
    }

    /**
     * Проверяет, что expectedIdx — самый поднятый среди "обычных" пальцев
     * (I, M, R, P). Для большого пальца (idx=4) всегда true —
     * его проверяем отдельным порогом в SessionActivity.
     */
    fun isExpectedMostLifted4(
        lifts: FloatArray,
        expectedIdx: Int,
        tolerance: Float = 0.05f
    ): Boolean {
        if (expectedIdx !in lifts.indices) return false
        if (expectedIdx == 4) return true

        val expected = lifts[expectedIdx]
        val maxOther = (0..3)
            .filter { it != expectedIdx }
            .maxOfOrNull { lifts[it] } ?: -1f
        return expected >= maxOther - tolerance
    }

    /**
     * Все "обычные" пальцы (0..3), кроме expectedIdx, должны быть ниже
     * downThreshold. Для большого (idx=4) проверяем, что все 4 обычных опущены.
     */
    fun othersAreDown(
        lifts: FloatArray,
        expectedIdx: Int,
        downThreshold: Float = 0.10f
    ): Boolean {
        if (expectedIdx !in lifts.indices) return false
        return (0..3)
            .filter { it != expectedIdx }
            .all { lifts[it] < downThreshold }
    }

    fun othersAreDownDelta(
        lifts: FloatArray,
        expectedIdx: Int,
        downThreshold: Float = 0.05f
    ): Boolean {
        if (expectedIdx !in lifts.indices) return false
        return (0..3)
            .filter { it != expectedIdx }
            .all { lifts[it] < downThreshold }
    }

    private fun isFingerExtended(
        tip: NormalizedLandmark,
        mcp: NormalizedLandmark,
        wrist: NormalizedLandmark,
        ratio: Float = 1.20f,
    ): Boolean {
        val distTip = hypot(tip.x() - wrist.x(), tip.y() - wrist.y())
        val distMcp = hypot(mcp.x() - wrist.x(), mcp.y() - wrist.y())
        return distTip > distMcp * ratio
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())

    private fun dist3D(a: Landmark, b: Landmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = a.z() - b.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}