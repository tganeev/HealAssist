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
import com.healassist.app.data.ExerciseModel
import com.healassist.app.data.ExerciseType
import com.healassist.app.data.PoseCounterDatabase
import com.healassist.app.data.ScheduledStatus
import com.healassist.app.TtsManager
import com.healassist.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "exercise_id"
        const val EXTRA_PENDING_LIST = "pending_list"
        const val EXTRA_IS_FIRST = "is_first"

        private const val EXERCISE_FAN_ID = "ex2"
        private const val EXERCISE_CLAW_ID = "ex3"
        private const val EXERCISE_FIRST_ID = "ex1"

        private const val GESTURE_STABLE_MS = 300L
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var targetText: TextView
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var exitButton: Button
    private lateinit var restTimerText: TextView

    private var helper: HandLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val allExercises = listOf(
        ExerciseModel("ex1", "Съжатие кулака", "Сожьмите руку в кулак, заъдержите на пару секунд и полностью его разожмите.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex2", "Веер", "Раскройте ладонь и максимально широко разведите все пальцы в стороны, затем сведите их вместе.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex3", "Когти", "Согните пальцы в средних суставах, имитируя кошачьи когти, не сжимая кулак. Затем выпрямите.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex4", "Подушечки", "Большим пальцем поочередно касайтесь подушечек остальных пальцев.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex5", "Стена", "Ладонь на столе. По очереди отрывайте каждый палец от поверхности.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex6", "Пианино", "Постучите подушечками пальцев по столу, имитируя игру на пианино.", 2, 5, 10, ExerciseType.FINGER)
    )

    private var currentExerciseId = ""
    private val currentExercise get() = allExercises.find { it.id == currentExerciseId } ?: allExercises[0]

    private var pendingIds = mutableListOf<String>()

    private var isFirstExerciseInSession = false

    private var lastGesture = HandGesture.NONE
    private var isFistConfirmed = false
    private var currentSet = 1
    private var repsInSet = 0
    private var lastTimestampMs = 0L
    private var isDestroyed = false

    private var isProcessingTransition = false
    private var isResting = false
    private var lastGestureLogTime = 0L

    // --- Дебаунс состояния жеста ---
    private var stableGesture: HandGesture = HandGesture.NONE
    private var pendingGesture: HandGesture = HandGesture.NONE
    private var pendingSinceMs = 0L
    private var lastAnalyzedGesture: HandGesture = HandGesture.UNKNOWN

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.healassist.app.R.layout.activity_session)

        viewFinder = findViewById(com.healassist.app.R.id.sessionViewFinder)
        targetText = findViewById(com.healassist.app.R.id.sessionTarget)
        progressText = findViewById(com.healassist.app.R.id.sessionProgress)
        statusText = findViewById(com.healassist.app.R.id.sessionStatus)
        exitButton = findViewById(com.healassist.app.R.id.sessionPauseButton)
        restTimerText = findViewById(com.healassist.app.R.id.restTimerText)

        setupCircleClickListeners()

        exitButton.text = "Выход"
        exitButton.setOnClickListener {
            TtsManager.stop()
            finish()
        }

        currentExerciseId = intent.getStringExtra(EXTRA_EXERCISE_ID) ?: "ex1"
        pendingIds = intent.getStringArrayListExtra(EXTRA_PENDING_LIST) ?: mutableListOf(currentExerciseId)
        isFirstExerciseInSession = intent.getBooleanExtra(EXTRA_IS_FIRST, false)

        AppLogger.log(
            "SESSION",
            "Session started: exercise=$currentExerciseId, pending=$pendingIds, isFirst=$isFirstExerciseInSession"
        )

        updateCircles()
        updateUi()

        val shouldAnnouncePhoneSetup =
            isFirstExerciseInSession && pendingIds.contains(EXERCISE_FIRST_ID)

        if (shouldAnnouncePhoneSetup) {
            TtsManager.speak(
                "Поставьте телефон перед собой таким образом, чтобы в камеру вы видели свои руки.",
                flushQueue = true
            )
            TtsManager.speak(exerciseAnnouncement(), flushQueue = false)
            isFirstExerciseInSession = false
            AppLogger.log("TTS", "Phone setup phrase + exercise announcement queued")
        } else {
            TtsManager.speak(exerciseAnnouncement())
            AppLogger.log("TTS", "Exercise announcement queued: $currentExerciseId")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            AppLogger.log("CAMERA", "Camera permission not granted, requesting")
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCircleClickListeners() {
        val circleIds = listOf(
            com.healassist.app.R.id.circle1, com.healassist.app.R.id.circle2, com.healassist.app.R.id.circle3,
            com.healassist.app.R.id.circle4, com.healassist.app.R.id.circle5, com.healassist.app.R.id.circle6
        )
        for (i in allExercises.indices) {
            val circle = findViewById<TextView>(circleIds[i])
            circle.setOnClickListener {
                switchToExercise(allExercises[i].id)
            }
        }
    }

    /**
     * Ручное переключение на упражнение по клику на круг.
     * Сбрасывает локальный прогресс счётчиков для нового упражнения.
     */
    private fun switchToExercise(newExerciseId: String) {
        if (newExerciseId == currentExerciseId) {
            AppLogger.log("SESSION", "Switch ignored: already on $newExerciseId")
            return
        }
        if (isProcessingTransition) {
            AppLogger.log("SESSION", "Switch ignored: transition in progress")
            return
        }

        AppLogger.log("SESSION", "Manual switch: $currentExerciseId -> $newExerciseId")

        currentExerciseId = newExerciseId
        currentSet = 1
        repsInSet = 0
        resetGestureState()
        isResting = false

        restTimerText.visibility = android.view.View.GONE
        statusText.text = "Ожидание..."

        updateCircles()
        updateUi()
        TtsManager.speak(exerciseAnnouncement())
    }

    private fun exerciseAnnouncement(): String {
        val ordinal = ordinalWord(ordinalNumberForCurrentExercise())
        return "Упражнение $ordinal. ${currentExercise.name}. ${currentExercise.instruction}"
    }

    private fun ordinalNumberForCurrentExercise(): Int {
        val idx = allExercises.indexOfFirst { it.id == currentExerciseId }
        return if (idx >= 0) idx + 1 else 1
    }

    private fun ordinalWord(n: Int): String = when (n) {
        1 -> "первое"
        2 -> "второе"
        3 -> "третье"
        4 -> "четвёртое"
        5 -> "пятое"
        6 -> "шестое"
        7 -> "седьмое"
        8 -> "восьмое"
        9 -> "девятое"
        10 -> "десятое"
        else -> "$n-е"
    }

    private fun startCamera() {
        viewFinder.post {
            try {
                helper = HandLandmarkerHelper(
                    context = this,
                    modelFile = "hand_landmarker.task",
                    onResult = { r, w, h -> runOnUiThread { processResult(r, w, h) } },
                    onError = { msg ->
                        AppLogger.log("MEDIAPIPE", "HandLandmarker error: $msg")
                        runOnUiThread { statusText.text = "Ошибка: $msg" }
                    },
                )
                AppLogger.log("CAMERA", "HandLandmarkerHelper created, model=hand_landmarker.task")

                val future = ProcessCameraProvider.getInstance(this)
                future.addListener({
                    cameraProvider = future.get()
                    bindCamera()
                }, ContextCompat.getMainExecutor(this))
            } catch (e: Exception) {
                AppLogger.log("CAMERA", "Camera init failed: ${e.message}")
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

        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
            AppLogger.log(
                "CAMERA",
                "Camera bound: ${if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}"
            )
        } catch (e: Exception) {
            AppLogger.log("CAMERA", "Camera bind failed: ${e.message}")
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

    private fun resetGestureState() {
        stableGesture = HandGesture.NONE
        pendingGesture = HandGesture.NONE
        pendingSinceMs = 0L
        isFistConfirmed = false
        lastGesture = HandGesture.NONE
        lastAnalyzedGesture = HandGesture.UNKNOWN
    }

    private fun processResult(result: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        val now = System.currentTimeMillis()
        if (now - lastGestureLogTime > 1000) {
            lastGestureLogTime = now
            if (result.landmarks().isEmpty()) {
                AppLogger.log("HAND_DETECT", "No hand in frame (exercise=$currentExerciseId)")
            } else {
                AppLogger.log(
                    "HAND_DETECT",
                    "Hand detected, landmarks=${result.landmarks()[0].size}, exercise=$currentExerciseId"
                )
            }
        }

        if (result.landmarks().isEmpty()) {
            statusText.text = "Статус: Рука не найдена"
            lastGesture = HandGesture.NONE
            return
        }

        val hand = result.landmarks()[0]

        val gesture = when (currentExerciseId) {
            EXERCISE_FAN_ID -> GestureAnalyzer.analyzeFan(hand, lastAnalyzedGesture)
            EXERCISE_CLAW_ID -> GestureAnalyzer.analyzeClaw(hand)
            else -> GestureAnalyzer.analyze(hand)
        }

        val statusMessage = when (gesture) {
            HandGesture.NONE -> "Рука не найдена"
            HandGesture.OPEN_PALM -> when (currentExerciseId) {
                EXERCISE_FAN_ID -> "Пальцы разведены"
                EXERCISE_CLAW_ID -> "Ладонь выпрямлена"
                else -> "Ладонь открыта"
            }
            HandGesture.FIST -> when (currentExerciseId) {
                EXERCISE_FAN_ID -> "Пальцы сведены"
                EXERCISE_CLAW_ID -> "Когти"
                else -> "Кулак сжат"
            }
            HandGesture.UNKNOWN -> "Промежуточное положение"
        }
        statusText.text = "Статус: $statusMessage"

        // Не считаем повторы во время перехода или отдыха.
        if (isProcessingTransition || isResting) return

        // --- ДЕБАУНС: жест должен удерживаться GESTURE_STABLE_MS ---
        if (gesture != pendingGesture) {
            pendingGesture = gesture
            pendingSinceMs = now
        }
        val confirmedGesture = if (now - pendingSinceMs >= GESTURE_STABLE_MS) gesture else stableGesture
        if (confirmedGesture != stableGesture) {
            stableGesture = confirmedGesture
        }
        lastAnalyzedGesture = gesture

        if (confirmedGesture == HandGesture.FIST) isFistConfirmed = true

        if (isFistConfirmed && confirmedGesture == HandGesture.OPEN_PALM) {
            isFistConfirmed = false

            // Жёсткое ограничение: не выходим за пределы повторов
            if (repsInSet >= currentExercise.repsPerSet) {
                AppLogger.log("REP", "Ignored: max reps reached ($repsInSet/${currentExercise.repsPerSet})")
                return
            }

            repsInSet++
            updateUi()

            AppLogger.log(
                "REP",
                "exercise=$currentExerciseId, set=$currentSet, rep=$repsInSet, gesture=$confirmedGesture"
            )

            TtsManager.speak("$repsInSet")

            if (repsInSet >= currentExercise.repsPerSet) {
                if (currentSet >= currentExercise.targetSets) {
                    finishCurrentExercise()
                } else {
                    currentSet++
                    repsInSet = 0
                    updateUi()
                    startRest()
                }
            }
        }
        lastGesture = confirmedGesture
    }

    private fun startRest() {
        val restSec = currentExercise.restBetweenSetsSec
        statusText.text = "Отдых"
        restTimerText.visibility = android.view.View.VISIBLE

        isResting = true
        resetGestureState()

        AppLogger.log("REST", "Rest started: exercise=$currentExerciseId, set=$currentSet, restSec=$restSec")
        TtsManager.speak("Подход завершен. Отдых $restSec секунд.")

        lifecycleScope.launch {
            var timeLeft = restSec
            while (timeLeft > 0) {
                restTimerText.text = timeLeft.toString()
                delay(1000L)
                timeLeft--
            }
            restTimerText.visibility = android.view.View.GONE
            isResting = false
            resetGestureState()
            statusText.text = "Продолжаем!"
            TtsManager.speak("Продолжаем выполнение.")
        }
    }

    private fun finishCurrentExercise() {
        if (isProcessingTransition) return
        isProcessingTransition = true

        AppLogger.log(
            "FINISH",
            "Exercise completed: $currentExerciseId (sets=${currentExercise.targetSets}, reps/set=${currentExercise.repsPerSet})"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = PoseCounterDatabase.get(this@SessionActivity)
            val todayUtc = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val schedules = db.scheduledExerciseDao().getForDate(todayUtc)
            val schedule = schedules.find { it.exerciseId == currentExerciseId }
            if (schedule != null) {
                db.scheduledExerciseDao().markStatus(schedule.id, ScheduledStatus.DONE, null)
                AppLogger.log("DB", "Marked exercise as DONE: $currentExerciseId")
            } else {
                AppLogger.log("DB", "No schedule found for $currentExerciseId on today")
            }
        }

        TtsManager.speak("Упражнение ${currentExercise.name} выполнено.")
        updateCircles(completedId = currentExerciseId)

        lifecycleScope.launch {
            delay(3000L)

            pendingIds.remove(currentExerciseId)

            if (pendingIds.isNotEmpty()) {
                currentExerciseId = pendingIds.first()
                currentSet = 1
                repsInSet = 0
                resetGestureState()

                AppLogger.log("SESSION", "Next exercise: $currentExerciseId, pending=$pendingIds")

                updateCircles()
                updateUi()
                TtsManager.speak(exerciseAnnouncement())

                isProcessingTransition = false
            } else {
                AppLogger.log("SESSION", "All exercises completed")
                Toast.makeText(this@SessionActivity, "Все упражнения выполнены!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun updateCircles(completedId: String? = null) {
        val circleIds = listOf(
            com.healassist.app.R.id.circle1, com.healassist.app.R.id.circle2, com.healassist.app.R.id.circle3,
            com.healassist.app.R.id.circle4, com.healassist.app.R.id.circle5, com.healassist.app.R.id.circle6
        )

        for (i in allExercises.indices) {
            val exId = allExercises[i].id
            val circle = findViewById<TextView>(circleIds[i])

            when {
                exId == currentExerciseId -> circle.setBackgroundResource(com.healassist.app.R.drawable.circle_active)
                !pendingIds.contains(exId) -> circle.setBackgroundResource(com.healassist.app.R.drawable.circle_completed)
                else -> circle.setBackgroundResource(com.healassist.app.R.drawable.circle_inactive)
            }
        }
    }

    private fun updateUi() {
        targetText.text = currentExercise.name
        // Жёсткое ограничение отображения: не показываем больше, чем задано
        val shownSet = currentSet.coerceAtMost(currentExercise.targetSets)
        val shownRep = repsInSet.coerceAtMost(currentExercise.repsPerSet)
        progressText.text = "Подход $shownSet/${currentExercise.targetSets} · Повтор $shownRep/${currentExercise.repsPerSet}"
    }

    override fun onDestroy() {
        isDestroyed = true
        AppLogger.log("SESSION", "Session destroyed, currentExercise=$currentExerciseId")
        super.onDestroy()
        cameraExecutor.shutdown()
        helper?.close()
    }
}