package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.hypot

enum class HandGesture {
    NONE,        // Руки нет в кадре
    FIST,        // Кулак (Сжат) / для "Веера" — пальцы сведены
    OPEN_PALM,   // Открытая ладонь (Разжат) / для "Веера" — пальцы разведены
    UNKNOWN      // Что-то среднее (не засчитываем)
}

object GestureAnalyzer {

    /** Общий анализ: кулак / открытая ладонь по вытянутости 4 пальцев. */
    fun analyze(result: HandLandmarkerResult): HandGesture {
        if (result.landmarks().isEmpty()) return HandGesture.NONE
        return analyze( result.landmarks()[0])
    }

    /** То же, но по списку лендмарков одной руки. */
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
            extendedCount == 4 -> HandGesture.OPEN_PALM // Все 4 пальца вытянуты
            extendedCount == 0 -> HandGesture.FIST      // Все 4 пальца сжаты
            else -> HandGesture.UNKNOWN                 // Промежуточное состояние
        }
    }

    /**
     * Специальный анализ для упражнения "Веер" (ex2):
     *  - пальцы должны быть прямыми (указательный и мизинец вытянуты);
     *  - считаем spreadRatio = (сумма расстояний между кончиками соседних пальцев) / длина ладони;
     *  - spreadRatio > 1.7  -> OPEN_PALM ("пальцы разведены");
     *  - иначе              -> FIST      ("пальцы сведены");
     *  - если пальцы согнуты -> UNKNOWN  (не засчитываем).
     */
    fun analyzeFan(hand: List<NormalizedLandmark>): HandGesture {
        if (hand.size < 21) return HandGesture.NONE

        val dist1_2 = dist(hand[8], hand[12])
        val dist2_3 = dist(hand[12], hand[16])
        val dist3_4 = dist(hand[16], hand[20])

        val totalSpread = dist1_2 + dist2_3 + dist3_4
        val palmLength = dist(hand[0], hand[9])

        if (palmLength < 0.01f) return HandGesture.UNKNOWN

        val spreadRatio = totalSpread / palmLength

        // Проверяем, что крайние пальцы прямые
        val isIndexExtended = isFingerExtended(hand[8], hand[5], hand[0])
        val isPinkyExtended = isFingerExtended(hand[20], hand[17], hand[0])

        return if (!isIndexExtended || !isPinkyExtended) {
            HandGesture.UNKNOWN // Рука в кулаке — не засчитываем
        } else {
            if (spreadRatio > 1.7f) HandGesture.OPEN_PALM else HandGesture.FIST
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