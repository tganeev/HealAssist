package com.healassist.app.camera

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    context: Context,
    modelFile: String = "pose_landmarker_full.task",
    private val onResult: (PoseLandmarkerResult, imageWidth: Int, imageHeight: Int) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        poseLandmarker = create(context, Delegate.GPU, modelFile)
            ?: create(context, Delegate.CPU, modelFile)
        if (poseLandmarker == null) {
            onError("не удалось создать PoseLandmarker ($modelFile)")
        }
    }

    private fun create(context: Context, delegate: Delegate, modelFile: String): PoseLandmarker? =
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelFile)
                .setDelegate(delegate)
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener { result, image ->
                    onResult(result, image.width, image.height)
                }
                .setErrorListener { e -> onError(e.message ?: "ошибка инференса") }
                .build()
            PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            null
        }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        poseLandmarker?.detectAsync(image, timestampMs)
    }

    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}