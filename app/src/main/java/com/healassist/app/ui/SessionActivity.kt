package com.healassist.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
import java.util.Locale
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

    // TTS переменные
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Список упражнений с инструкциями
    private val exercises = listOf(
        ExerciseModel("ex1", "Съжатие кулака", "Сожьмите руку в кулак, заъдержите на пару секунд и полностью его разожмите.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex2", "Веер", "Раскройте ладонь и максимально широко разведите все пальцы в стороны, затем сведите их вместе.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex3", "Когти", "Согните пальцы в средних суставах, имитируя кошачьи когти, не сжимая кулак. Затем выпрямите.", 2, 5, 10, ExerciseType.FINGER)
    )

    private var currentExerciseIndex = 0
    private val currentExercise get() = exercises[currentExerciseIndex]

    private var lastGesture = HandGesture.NONE
    private var isFistConfirmed = false
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

        initTts()

        pauseButton.setOnClickListener {
            isPaused = !isPaused
            pauseButton.text = if (isPaused) "Продолжить" else "Пауза"
        }

        findViewById<TextView>(com.healassist.app.R.id.circle1).setOnClickListener { selectExercise(0) }
        findViewById<TextView>(com.healassist.app.R.id.circle2).setOnClickListener { selectExercise(1) }
        findViewById<TextView>(com.healassist.app.R.id.circle3).setOnClickListener { selectExercise(2) }

        updateUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = false
                } else {
                    isTtsReady = true
                    // Проговариваем первую инструкцию после инициализации
                    speakInstruction()
                }
            } else {
                isTtsReady = false
            }
        }
    }

    private fun speakInstruction() {
        if (!isTtsReady) return
        val textToSpeak = "${currentExercise.name}. ${currentExercise.instruction}"
        // QUEUE_FLUSH прерывает предыдущую фразу, чтобы не было каши
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "instruction_$currentExerciseIndex")
    }

    private fun selectExercise(index: Int) {
        if (index >= exercises.size) {
            Toast.makeText(this, "Упражнение в разработке", Toast.LENGTH_SHORT).show()
            return
        }
        currentExerciseIndex = index
        currentSet = 1
        repsInSet = 0
        isFistConfirmed = false
        lastGesture = HandGesture.NONE

        val circleIds = listOf(
            com.healassist.app.R.id.circle1, com.healassist.app.R.id.circle2, com.healassist.app.R.id.circle3,
            com.healassist.app.R.id.circle4, com.healassist.app.R.id.circle5, com.healassist.app.R.id.circle6
        )
        for (id in circleIds) {
            findViewById<TextView>(id).setBackgroundResource(com.healassist.app.R.drawable.circle_inactive)
        }

        findViewById<TextView>(circleIds[index]).setBackgroundResource(com.healassist.app.R.drawable.circle_active)

        updateUi()

        // Проговариваем инструкцию для нового упражнения
        speakInstruction()
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
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor, ::analyzeFrame)

        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
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
        if (result.landmarks().isEmpty()) {
            statusText.text = "Статус: Рука не найдена"
            lastGesture = HandGesture.NONE
            return
        }

        val gesture = GestureAnalyzer.analyze(result)
        val statusMessage = when (gesture) {
            HandGesture.NONE -> "Рука не найдена"
            HandGesture.OPEN_PALM -> "Ладонь открыта"
            HandGesture.FIST -> "Кулак сжат"
            HandGesture.UNKNOWN -> "Промежуточное положение"
        }
        statusText.text = "Статус: $statusMessage"

        if (isPaused) return

        if (gesture == HandGesture.FIST) isFistConfirmed = true

        if (isFistConfirmed && gesture == HandGesture.OPEN_PALM) {
            isFistConfirmed = false
            repsInSet++
            updateUi()

            // Можно добавить голосовой отсчет повторений, если нужно
            // speakNumber(repsInSet)

            if (repsInSet >= currentExercise.repsPerSet) {
                if (currentSet >= currentExercise.targetSets) {
                    finishExercise()
                } else {
                    currentSet++
                    repsInSet = 0
                    updateUi()
                    startRest()
                }
            }
        }
        lastGesture = gesture
    }

    private fun startRest() {
        isPaused = true
        val restSec = currentExercise.restBetweenSetsSec
        statusText.text = "Отдых $restSec сек..."

        if (isTtsReady) {
            tts?.speak("Подход завершен. Отдых $restSec секунд.", TextToSpeech.QUEUE_FLUSH, null, "rest")
        }

        lifecycleScope.launch {
            delay(restSec * 1000L)
            isPaused = false
            lastGesture = HandGesture.NONE
            statusText.text = "Продолжаем!"
            if (isTtsReady) {
                tts?.speak("Продолжаем выполнение.", TextToSpeech.QUEUE_FLUSH, null, "resume")
            }
        }
    }

    private fun finishExercise() {
        isPaused = true
        Toast.makeText(this, "Упражнение выполнено!", Toast.LENGTH_LONG).show()
        if (isTtsReady) {
            tts?.speak("Упражнение ${currentExercise.name} выполнено.", TextToSpeech.QUEUE_FLUSH, null, "finish")
        }

        val circleIds = listOf(
            com.healassist.app.R.id.circle1, com.healassist.app.R.id.circle2, com.healassist.app.R.id.circle3,
            com.healassist.app.R.id.circle4, com.healassist.app.R.id.circle5, com.healassist.app.R.id.circle6
        )
        findViewById<TextView>(circleIds[currentExerciseIndex]).setBackgroundResource(com.healassist.app.R.drawable.circle_completed)

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

        // Освобождаем TTS
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}