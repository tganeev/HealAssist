package com.healassist.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

object HandRepCounters {
    private val FINGER_TIPS = listOf(16, 18, 20, 22)
    private const val WRIST_INDEX = 0
    private const val CLOSED_THRESHOLD = 0.12
    private const val OPEN_THRESHOLD = 0.18

    data class HandState(val state: String = STATE_OPEN, val count: Int = 0)

    const val STATE_OPEN = "open"
    const val STATE_CLOSED = "closed"

    fun updateFistCount(
        landmarks: List<NormalizedLandmark>,
        currentState: HandState,
        imageWidth: Int,
        imageHeight: Int
    ): HandState {
        if (landmarks.size < 23) return currentState
        val wrist = landmarks[WRIST_INDEX]
        var totalDist = 0.0
        for (tipIndex in FINGER_TIPS) {
            val tip = landmarks[tipIndex]
            val dx = (tip.x() - wrist.x()).toDouble() * imageWidth
            val dy = (tip.y() - wrist.y()).toDouble() * imageHeight
            totalDist += hypot(dx, dy)
        }
        val avgDist = totalDist / FINGER_TIPS.size
        val normalizedDist = avgDist / imageHeight

        return when {
            currentState.state == STATE_OPEN && normalizedDist < CLOSED_THRESHOLD -> {
                HandState(STATE_CLOSED, currentState.count)
            }
            currentState.state == STATE_CLOSED && normalizedDist > OPEN_THRESHOLD -> {
                HandState(STATE_OPEN, currentState.count + 1)
            }
            else -> currentState
        }
    }
}