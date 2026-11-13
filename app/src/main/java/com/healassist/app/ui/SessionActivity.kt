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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.healassist.app.camera.HandRepCounters
import com.healassist.app.camera.OverlayView
import com.healassist.app.camera.PoseLandmarkerHelper
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

    private var helper: PoseLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // ТЕСТОВОЕ УПРАЖНЕНИЕ (Пальцы/Кулак)
    private val currentExercise = ExerciseModel(
        id = "ex_kulak",
        name = "Сжимание кулака",
        instruction = "Сожмите пальцы в кулак и разожмите",
        targetSets = 2,
        repsPerSet = 5,
        restBetweenSetsSec = 10,
        type = ExerciseType.FINGER
    )

    private var handState = HandRepCounters.HandState()
    private var currentSet = 1
    private var repsInSet = 0
    private var isPaused = false
    private var lastTimestampMs = 0L

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else {
                Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_SHORT).show()
                finish()
            }
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
        // Выбираем легкую модель для пальцев
        helper = PoseLandmarkerHelper(
            context = this,
            modelFile = "pose_landmarker_light.task",
            onResult = { r, w, h -> runOnUiThread { processResult(r, w, h) } },
            onError = { msg -> runOnUiThread { statusText.text = msg } },
        )

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
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

        val selector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        overlay.mirrored = true

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка камеры", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeFrame(proxy: ImageProxy) {
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

    private fun processResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        val lm = result.landmarks().firstOrNull() ?: return
        overlay.setFrame(lm, imageWidth, imageHeight)

        if (isPaused) return

        val prev = handState.count
        handState = HandRepCounters.updateFistCount(lm, handState, imageWidth, imageHeight)

        if (handState.count > prev) {
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
        statusText.text = "Состояние: ${handState.state}"
    }

    private fun startRest() {
        isPaused = true
        statusText.text = "Отдых 10 сек..."
        lifecycleScope.launch {
            delay(10000)
            isPaused = false
            currentSet++
            repsInSet = 0
            handState = HandRepCounters.HandState()
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
        super.onDestroy()
        cameraExecutor.shutdown()
        helper?.close()
    }
}