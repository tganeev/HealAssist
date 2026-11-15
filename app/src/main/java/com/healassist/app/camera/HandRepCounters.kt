package com.healassist.app.camera

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    context: Context,
    modelFile: String = "hand_landmarker.task",
    private val onResult: (HandLandmarkerResult, imageWidth: Int, imageHeight: Int) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {

    private var handLandmarker: HandLandmarker? = null

    init {
        handLandmarker = create(context, Delegate.GPU, modelFile)
            ?: create(context, Delegate.CPU, modelFile)
        if (handLandmarker == null) {
            onError("не удалось создать HandLandmarker ($modelFile)")
        }
    }

    private fun create(context: Context, delegate: Delegate, modelFile: String): HandLandmarker? =
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelFile)
                .setDelegate(delegate)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image ->
                    onResult(result, image.width, image.height)
                }
                .setErrorListener { e -> onError(e.message ?: "ошибка инференса") }
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            null
        }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        handLandmarker?.detectAsync(image, timestampMs)
    }

    override fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}