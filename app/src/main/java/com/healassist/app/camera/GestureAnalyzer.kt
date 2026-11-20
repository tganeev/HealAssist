package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

enum class HandGesture {
    NONE, FIST, OPEN_PALM, UNKNOWN
}

object GestureAnalyzer {

    // ============================================================
    // Общий анализ (ex1 "Сжатие кулака" и любые прочие)
    // ============================================================

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

    // ============================================================
    // ex2 "Веер" — пальцы сведены / разведены (с гистерезисом)
    // ============================================================

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

    // ============================================================
    // ex3 "Когти" — пальцы полусогнуты в средних суставах
    // ============================================================

    fun analyzeClaw(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE
        val fingers: List<Triple<Int, Int, Int>> = listOf(
            Triple(8, 5, 0), Triple(12, 9, 0), Triple(16, 13, 0), Triple(20, 17, 0)
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

    // ============================================================
    // ex4 "Подушечки" — касание большим пальцем остальных
    // ============================================================

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

    // ============================================================
    // ex6 "Пианино" — касание стола каждым пальцем по очереди
    // ============================================================

    fun analyzePiano(hand: List<NormalizedLandmark>): Int {
        if (hand.size < 21) return -1
        val wristY = hand[0].y()
        val tips = listOf(8 to 0, 12 to 1, 16 to 2, 20 to 3, 4 to 4)
        var maxTipY = -1f
        var touchingFingerIdx = -1
        val touchThreshold = 0.025f
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
    // ex5 "Стена" — по очереди поднимаем каждый палец
    // ============================================================

    /** Размер ладони (расстояние запястье → основание среднего пальца). */
    fun getPalmSize(hand: List<NormalizedLandmark>): Float {
        if (hand.size < 21) return 0f
        return dist(hand[0], hand[9]).coerceAtLeast(0.001f)
    }

    /** Пары (tipIdx, mcpIdx) для пальцев: I, M, R, P, Th. */
    private val FINGER_PAIRS = listOf(
        8 to 5,    // Указательный
        12 to 9,   // Средний
        16 to 13,  // Безымянный
        20 to 17,  // Мизинец
        4 to 2     // Большой
    )

    /**
     * Возвращает массив "поднятости" для каждого пальца:
     *   lift[i] = (mcp.y - tip.y) / palmSize
     * Положительное значение = кончик выше своего MCP (палец поднят).
     * Отрицательное = кончик ниже MCP (палец лежит/опущен).
     *
     * Использование СВОЕГО MCP для каждого пальца компенсирует
     * анатомические различия в положении оснований пальцев.
     */
    fun getFingerLifts(hand: List<NormalizedLandmark>): FloatArray {
        if (hand.size < 21) return FloatArray(5) { 0f }
        val palmSize = getPalmSize(hand)
        return FloatArray(5) { i ->
            val (tipIdx, mcpIdx) = FINGER_PAIRS[i]
            (hand[mcpIdx].y() - hand[tipIdx].y()) / palmSize
        }
    }

    /**
     * "Стена" (ex5). Возвращает список индексов поднятых пальцев.
     * 0=Указательный, 1=Средний, 2=Безымянный, 3=Мизинец, 4=Большой.
     *
     * Критерий: lift = (mcp.y - tip.y) / palmSize > threshold (0.02).
     * Это низкий порог — реальное поднятие пальца даёт 0.05–0.20, шум ~0.005.
     * Указательный при этом обычно имеет наибольший lift, но для остальных
     * пальцев порог 0.02 тоже достигается при реальном поднятии.
     */
    fun getLiftedFingers(hand: List<NormalizedLandmark>): List<Int> {
        if (hand.size < 21) return emptyList()
        val lifts = getFingerLifts(hand)
        val threshold = 0.02f
        return lifts.indices.filter { lifts[it] > threshold }
    }

    /**
     * Проверка, является ли [expectedIdx] "самым поднятым" пальцем
     * (с допуском [tolerance]). Нужно, чтобы пользователь поднимал
     * именно ожидаемый палец, а не любой другой.
     */
    fun isExpectedMostLifted(
        lifts: FloatArray,
        expectedIdx: Int,
        tolerance: Float = 0.02f
    ): Boolean {
        if (expectedIdx !in lifts.indices) return false
        val expected = lifts[expectedIdx]
        val maxOther = lifts.filterIndexed { i, _ -> i != expectedIdx }.maxOrNull() ?: -1f
        return expected >= maxOther - tolerance
    }

    // ============================================================
    // Утилиты
    // ============================================================

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