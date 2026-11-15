package com.healassist.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.healassist.app.camera.GestureAnalyzer
import com.healassist.app.camera.HandGesture
import com.healassist.app.camera.HandLandmarkerHelper
import com.healassist.app.camera.OverlayView
import com.healassist.app.data.ExerciseModel
import com.healassist.app.data.ExerciseType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SessionActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var targetText: TextView
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var pauseButton: Button

    private var helper: HandLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val currentExercise = ExerciseModel(
        id = "ex_kulak",
        name = "Сжимание кулака",
        instruction = "Сожмите пальцы в кулак и разожмите",
        targetSets = 2,
        repsPerSet = 5,
        restBetweenSetsSec = 10,
        type = ExerciseType.FINGER
    )

    private var lastGesture = HandGesture.NONE
    private var currentSet = 1
    private var repsInSet = 0
    private var isPaused = false
    private var lastTimestampMs = 0L
    private var isDestroyed = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.healassist.app.R.layout.activity_session)

        viewFinder = findViewById(com.healassist.app.R.id.sessionViewFinder)
        overlay = findViewById(com.healassist.app.R.id.sessionOverlay)
        targetText = findViewById(com.healassist.app.R.id.sessionTarget)
        progressText = findViewById(com.healassist.app.R.id.sessionProgress)
        statusText = findViewById(com.healassist.app.R.id.sessionStatus)
        pauseButton = findViewById(com.healassist.app.R.id.sessionPauseButton)

        pauseButton.setOnClickListener {
            isPaused = !isPaused
            pauseButton.text = if (isPaused) "Продолжить" else "Пауза"
        }

        updateUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        viewFinder.post {
            try {
                helper = HandLandmarkerHelper(
                    context = this,
                    modelFile = "hand_landmarker.task",
                    onResult = { r, w, h -> runOnUiThread { processResult(r, w, h) } },
                    onError = { msg -> runOnUiThread { statusText.text = "Ошибка: $msg" } },
                )

                val future = ProcessCameraProvider.getInstance(this)
                future.addListener({
                    cameraProvider = future.get()
                    bindCamera()
                }, ContextCompat.getMainExecutor(this))
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor, ::analyzeFrame)

        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        overlay.mirrored = (selector == CameraSelector.DEFAULT_FRONT_CAMERA)

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка камеры: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        if (isDestroyed) { proxy.close(); return }
        val h = helper ?: run { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        val bitmap = try { proxy.toBitmap() } catch (e: Exception) { proxy.close(); return }
        proxy.close()

        val ts = SystemClock.uptimeMillis()
        if (ts <= lastTimestampMs) return
        lastTimestampMs = ts
        val upright = if (rotation == 0) bitmap else rotateBitmap(bitmap, rotation)
        h.detectAsync(BitmapImageBuilder(upright).build(), ts)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }

    private fun processResult(result: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        // Руки нет в кадре
        if (result.landmarks().isEmpty()) {
            statusText.text = "Статус: Рука не найдена"
            lastGesture = HandGesture.NONE
            return
        }

        val gesture = GestureAnalyzer.analyze(result)

        // Обновляем статус на экране
        val statusMessage = when (gesture) {
            HandGesture.NONE -> "Рука не найдена"
            HandGesture.OPEN_PALM -> "Ладонь открыта"
            HandGesture.FIST -> "Кулак сжат"
            HandGesture.UNKNOWN -> "Промежуточное положение"
        }
        statusText.text = "Статус: $statusMessage"

        if (isPaused) return

        // Логика засчитывания повторения: кулак был сжат -> разжался
        if (lastGesture == HandGesture.FIST && gesture == HandGesture.OPEN_PALM) {
            repsInSet++
            updateUi()

            if (repsInSet >= currentExercise.repsPerSet) {
                if (currentSet >= currentExercise.targetSets) {
                    finishExercise()
                } else {
                    startRest()
                }
            }
        }

        lastGesture = gesture
    }

    private fun startRest() {
        isPaused = true
        statusText.text = "Отдых 10 сек..."
        lifecycleScope.launch {
            delay(10000)
            isPaused = false
            currentSet++
            repsInSet = 0
            lastGesture = HandGesture.NONE
            updateUi()
        }
    }

    private fun finishExercise() {
        isPaused = true
        Toast.makeText(this, "Упражнение выполнено!", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun updateUi() {
        targetText.text = currentExercise.name
        progressText.text = "Подход $currentSet/${currentExercise.targetSets} · Повтор $repsInSet/${currentExercise.repsPerSet}"
    }

    override fun onDestroy() {
        isDestroyed = true
        super.onDestroy()
        cameraExecutor.shutdown()
        helper?.close()
    }
}