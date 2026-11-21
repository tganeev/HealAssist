package com.healassist.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.healassist.app.R
import com.healassist.app.TtsManager
import com.healassist.app.camera.GestureAnalyzer
import com.healassist.app.camera.HandGesture
import com.healassist.app.camera.HandLandmarkerHelper
import com.healassist.app.data.ComplexType
import com.healassist.app.data.ExerciseModel
import com.healassist.app.data.ExerciseType
import com.healassist.app.data.PoseCounterDatabase
import com.healassist.app.data.ScheduledStatus
import com.healassist.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SessionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EXERCISE_ID = "exercise_id"
        const val EXTRA_PENDING_LIST = "pending_list"
        const val EXTRA_IS_FIRST = "is_first"
        const val EXTRA_COMPLEX_TYPE = "complex_type"
        private const val EXERCISE_FIRST_ID = "ex1"
        private const val EXERCISE_FAN_ID = "ex2"
        private const val EXERCISE_CLAW_ID = "ex3"
        private const val EXERCISE_THUMB_ID = "ex4"
        private const val EXERCISE_WALL_ID = "ex5"
        private const val EXERCISE_PIANO_ID = "ex6"
        private const val GESTURE_STABLE_MS = 300L

        private const val WALL_STABLE_FRAMES_REQUIRED = 6

        /** Порог для "обычных" пальцев (I, M, R, P) относительно медианы. */
        private const val WALL_RELATIVE_LIFT_THRESHOLD = 0.03f

        /** Порог для большого пальца — его raw изначально высокий. */
        private const val WALL_THUMB_LIFT_THRESHOLD = 0.50f

        /** Допуск для "самый поднятый" среди обычных пальцев. */
        private const val WALL_LIFT_TOLERANCE = 0.05f

        /** Остальные пальцы должны быть ниже этого уровня, чтобы засчитать. */
        private const val WALL_DOWN_THRESHOLD = 0.10f
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
        ExerciseModel("ex1", "Сжатие кулака", "Сожьмите руку в кулак, задержиъте на пару секунд и полностью разожмите.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex2", "Веер", "Раскройте ладонь и максимально широко разведите все пальцы в стороны, затем сведите их вместе.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex3", "Когти", "Согните пальцы в средних суставах, имитируя кошачьи когти, не сжимая кулак. Затем выпрямите.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex4", "Подушечки", "Большим пальцем поочередно касайтесь подушечек остальных пальцев.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex5", "Стена", "Ладонь на столе. По очереди поднимайте каждый палец: указательный, средний, безымянный, мизинец, большой.", 2, 5, 10, ExerciseType.FINGER),
        ExerciseModel("ex6", "Пианино", "Постучите подушечками пальцев по столу, имитируя игру на пианино.", 2, 5, 10, ExerciseType.FINGER)
    )

    private var currentExerciseId = ""
    private val currentExercise get() = allExercises.find { it.id == currentExerciseId } ?: allExercises[0]
    private var currentComplexType = ComplexType.MORNING
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

    private var stableGesture: HandGesture = HandGesture.NONE
    private var pendingGesture: HandGesture = HandGesture.NONE
    private var pendingSinceMs = 0L
    private var lastAnalyzedGesture: HandGesture = HandGesture.UNKNOWN

    private var pianoCurrentStep = 0
    private val pianoFingerNames = listOf("указательным", "средним", "безымянным", "мизинцем", "большим")
    private var pianoRepCount = 0

    private var wallCurrentStep = 0
    private val wallFingerNames = listOf("указательный", "средний", "безымянный", "мизинец", "большой")
    private var wallRepCount = 0
    private var wallStableFrames = 0
    private var wallStarted = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        viewFinder = findViewById(R.id.sessionViewFinder)
        targetText = findViewById(R.id.sessionTarget)
        progressText = findViewById(R.id.sessionProgress)
        statusText = findViewById(R.id.sessionStatus)
        exitButton = findViewById(R.id.sessionPauseButton)
        restTimerText = findViewById(R.id.restTimerText)

        setupCircleClickListeners()
        exitButton.text = "Выход"
        exitButton.setOnClickListener {
            TtsManager.stop()
            finish()
        }

        currentExerciseId = intent.getStringExtra(EXTRA_EXERCISE_ID) ?: "ex1"
        pendingIds = intent.getStringArrayListExtra(EXTRA_PENDING_LIST) ?: mutableListOf(currentExerciseId)
        isFirstExerciseInSession = intent.getBooleanExtra(EXTRA_IS_FIRST, false)
        currentComplexType = intent.getStringExtra(EXTRA_COMPLEX_TYPE) ?: ComplexType.MORNING

        AppLogger.log(
            "SESSION",
            "Session started: exercise=$currentExerciseId, complex=$currentComplexType, " +
                "pending=$pendingIds, isFirst=$isFirstExerciseInSession"
        )
        updateCircles()
        updateUi()

        val shouldAnnouncePhoneSetup = isFirstExerciseInSession && pendingIds.contains(EXERCISE_FIRST_ID)
        if (shouldAnnouncePhoneSetup) {
            TtsManager.speak("Поставьте телефон перед собой таким образом, чтобы в камеру вы видели свои руки.", flushQueue = true)
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
        val circleIds = listOf<Int>(
            R.id.circle1, R.id.circle2, R.id.circle3,
            R.id.circle4, R.id.circle5, R.id.circle6
        )
        for (i in allExercises.indices) {
            val circle = findViewById<TextView>(circleIds[i])
            circle.setOnClickListener { switchToExercise(allExercises[i].id) }
        }
    }

    private fun switchToExercise(newExerciseId: String) {
        if (newExerciseId == currentExerciseId || isProcessingTransition) return
        AppLogger.log("SESSION", "Manual switch: $currentExerciseId -> $newExerciseId")
        currentExerciseId = newExerciseId
        currentSet = 1
        repsInSet = 0
        resetSequenceState()
        resetGestureState()
        isResting = false
        restTimerText.visibility = View.GONE
        statusText.text = "Ожидание..."
        updateCircles()
        updateUi()
        TtsManager.speak(exerciseAnnouncement())
    }

    private fun resetSequenceState() {
        pianoCurrentStep = 0
        pianoRepCount = 0
        wallCurrentStep = 0
        wallRepCount = 0
        wallStableFrames = 0
        wallStarted = false
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
        1 -> "первое"; 2 -> "второе"; 3 -> "третье"; 4 -> "четвёртое"
        5 -> "пятое"; 6 -> "шестое"; 7 -> "седьмое"; 8 -> "восьмое"
        9 -> "девятое"; 10 -> "десятое"; else -> "$n-е"
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
                    }
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
            AppLogger.log("CAMERA", "Camera bound: ${if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}")
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
        wallStableFrames = 0
    }

    private fun processResult(result: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        if (isDestroyed) return
        val now = System.currentTimeMillis()

        if (now - lastGestureLogTime > 1000) {
            lastGestureLogTime = now
            if (result.landmarks().isEmpty()) {
                AppLogger.log("HAND_DETECT", "No hand in frame (exercise=$currentExerciseId)")
            } else {
                AppLogger.log("HAND_DETECT", "Hand detected, landmarks=${result.landmarks()[0].size}, exercise=$currentExerciseId")
            }
        }

        if (result.landmarks().isEmpty()) {
            statusText.text = "Статус: Рука не найдена"
            lastGesture = HandGesture.NONE
            return
        }

        val hand = result.landmarks()[0]

        if (currentExerciseId == EXERCISE_PIANO_ID && now % 200 < 20) {
            AppLogger.log(
                "DIAG_ex6",
                "WristY=${String.format("%.3f", hand[0].y())} | Tips: I=${String.format("%.3f", hand[8].y())} M=${String.format("%.3f", hand[12].y())} R=${String.format("%.3f", hand[16].y())} P=${String.format("%.3f", hand[20].y())} Th=${String.format("%.3f", hand[4].y())}"
            )
        }

        // Диагностика для "Стены"
        if (currentExerciseId == EXERCISE_WALL_ID && now % 200 < 20) {
            val raw = GestureAnalyzer.getFingerLifts(hand)
            val rel = GestureAnalyzer.getRelativeLifts4(hand)
            val names = listOf("I", "M", "R", "P", "Th")
            val diag = names.indices.joinToString(" ") { i ->
                "${names[i]}=${String.format("%.2f", raw[i])}/${String.format("%.2f", rel[i])}"
            }
            val others = GestureAnalyzer.othersAreDown(rel, wallCurrentStep, WALL_DOWN_THRESHOLD)
            AppLogger.log("DIAG_ex5", "step=$wallCurrentStep frames=$wallStableFrames othersDown=$others | $diag")
        }

        if (isProcessingTransition || isResting) {
            statusText.text = if (isResting) "Статус: Отдых" else "Статус: Переход к следующему упражнению"
            return
        }

        val gesture: HandGesture
        val statusMessage: String

        if (currentExerciseId == EXERCISE_WALL_ID) {
            // --- ЛОГИКА УПРАЖНЕНИЯ "СТЕНА" ---
            val relLifts = GestureAnalyzer.getRelativeLifts4(hand)
            val expectedIdx = wallCurrentStep

            val expectedLifted = if (expectedIdx == 4) {
                relLifts[4] > WALL_THUMB_LIFT_THRESHOLD
            } else {
                relLifts[expectedIdx] > WALL_RELATIVE_LIFT_THRESHOLD
            }
            val expectedIsMost = GestureAnalyzer.isExpectedMostLifted4(
                relLifts, expectedIdx, WALL_LIFT_TOLERANCE
            )
            val othersDown = GestureAnalyzer.othersAreDown(
                relLifts, expectedIdx, WALL_DOWN_THRESHOLD
            )
            val isExpectedLifted = expectedLifted && expectedIsMost && othersDown

            if (!wallStarted) {
                wallStarted = true
                TtsManager.speak("Поднимите ${wallFingerNames[expectedIdx]}", flushQueue = false)
                AppLogger.log("TTS", "Wall start prompt: ${wallFingerNames[expectedIdx]}")
            }

            if (isExpectedLifted) {
                wallStableFrames++
                if (wallStableFrames >= WALL_STABLE_FRAMES_REQUIRED) {
                    wallStableFrames = 0
                    gesture = HandGesture.OPEN_PALM
                    val countedName = wallFingerNames[wallCurrentStep]
                    AppLogger.log("REP", "exercise=ex5, set=$currentSet, cycle=$wallRepCount, step=$wallCurrentStep ($countedName)")
                    TtsManager.speak("Отлично", flushQueue = false)

                    wallCurrentStep++

                    if (wallCurrentStep >= 5) {
                        wallCurrentStep = 0
                        wallRepCount++
                        if (wallRepCount >= currentExercise.repsPerSet) {
                            if (currentSet >= currentExercise.targetSets) {
                                updateUi()
                                finishCurrentExercise()
                            } else {
                                currentSet++
                                wallRepCount = 0
                                wallStarted = false
                                updateUi()
                                startRest()
                            }
                        } else {
                            updateUi()
                            TtsManager.speak("Следующее повторение. Поднимите ${wallFingerNames[wallCurrentStep]}", flushQueue = false)
                        }
                    } else {
                        TtsManager.speak("Поднимите ${wallFingerNames[wallCurrentStep]}", flushQueue = false)
                    }
                    statusMessage = "Палец поднят!"
                } else {
                    gesture = HandGesture.UNKNOWN
                    statusMessage = "Держите ${wallFingerNames[wallCurrentStep]}..."
                }
            } else {
                wallStableFrames = 0
                gesture = HandGesture.NONE
                statusMessage = "Поднимите ${wallFingerNames[wallCurrentStep]}"
            }
        } else if (currentExerciseId == EXERCISE_PIANO_ID) {
            // --- ЛОГИКА УПРАЖНЕНИЯ "ПИАНИНО" ---
            val touchedFinger = GestureAnalyzer.analyzePiano(hand)
            if (touchedFinger == -1) {
                gesture = HandGesture.NONE
                statusMessage = if (pianoCurrentStep == 0)
                    "Исходное положение: Поднимите ладонь над столом на высоту мизинца"
                else
                    "Жду касания ${pianoFingerNames[pianoCurrentStep]} пальцем..."
            } else if (touchedFinger == pianoCurrentStep) {
                gesture = HandGesture.OPEN_PALM
                AppLogger.log("REP", "exercise=ex6, set=$currentSet, cycle=$pianoRepCount, step=$pianoCurrentStep")
                TtsManager.speak(pianoFingerNames[pianoCurrentStep], flushQueue = false)
                pianoCurrentStep++
                if (pianoCurrentStep >= 5) {
                    pianoCurrentStep = 0
                    pianoRepCount++
                    if (pianoRepCount >= currentExercise.repsPerSet) {
                        if (currentSet >= currentExercise.targetSets) {
                            updateUi()
                            finishCurrentExercise()
                        } else {
                            currentSet++
                            pianoRepCount = 0
                            updateUi()
                            startRest()
                        }
                    } else {
                        updateUi()
                        TtsManager.speak("Отлично! Следующее повторение.", flushQueue = false)
                    }
                }
                statusMessage = "Касание засчитано!"
            } else {
                gesture = HandGesture.UNKNOWN
                statusMessage = "Коснитесь стола ${pianoFingerNames[pianoCurrentStep]} пальцем"
            }
        } else {
            // --- СТАНДАРТНАЯ ЛОГИКА ---
            gesture = when (currentExerciseId) {
                EXERCISE_FAN_ID -> GestureAnalyzer.analyzeFan(hand, lastAnalyzedGesture)
                EXERCISE_CLAW_ID -> GestureAnalyzer.analyzeClaw(hand)
                EXERCISE_THUMB_ID -> if (GestureAnalyzer.isThumbTouching(hand)) HandGesture.OPEN_PALM else HandGesture.FIST
                else -> GestureAnalyzer.analyze(hand)
            }

            statusMessage = when (gesture) {
                HandGesture.NONE -> "Рука не найдена"
                HandGesture.OPEN_PALM -> when (currentExerciseId) {
                    EXERCISE_FAN_ID -> "Пальцы разведены"
                    EXERCISE_CLAW_ID -> "Ладонь выпрямлена"
                    EXERCISE_THUMB_ID -> "Касание!"
                    else -> "Ладонь открыта"
                }
                HandGesture.FIST -> when (currentExerciseId) {
                    EXERCISE_FAN_ID -> "Пальцы сведены"
                    EXERCISE_CLAW_ID -> "Когти"
                    EXERCISE_THUMB_ID -> "Разведите пальцы"
                    else -> "Кулак сжат"
                }
                HandGesture.UNKNOWN -> "Промежуточное положение"
            }

            if (gesture != pendingGesture) {
                pendingGesture = gesture
                pendingSinceMs = now
            }
            val confirmedGesture = if (now - pendingSinceMs >= GESTURE_STABLE_MS) gesture else stableGesture
            if (confirmedGesture != stableGesture) stableGesture = confirmedGesture
            lastAnalyzedGesture = gesture

            if (currentExerciseId == EXERCISE_CLAW_ID) {
                if (confirmedGesture == HandGesture.OPEN_PALM) isFistConfirmed = false
                if (!isFistConfirmed && confirmedGesture == HandGesture.FIST) {
                    isFistConfirmed = true
                    if (repsInSet < currentExercise.repsPerSet) {
                        repsInSet++
                        updateUi()
                        AppLogger.log("REP", "exercise=$currentExerciseId, set=$currentSet, rep=$repsInSet, gesture=CLAW_REACHED")
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
                }
            } else {
                if (confirmedGesture == HandGesture.FIST) isFistConfirmed = true
                if (isFistConfirmed && confirmedGesture == HandGesture.OPEN_PALM) {
                    isFistConfirmed = false
                    if (repsInSet < currentExercise.repsPerSet) {
                        repsInSet++
                        updateUi()
                        AppLogger.log("REP", "exercise=$currentExerciseId, set=$currentSet, rep=$repsInSet")
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
                }
            }
            lastGesture = confirmedGesture
        }

        statusText.text = "Статус: $statusMessage"
    }

    private fun startRest() {
        val restSec = currentExercise.restBetweenSetsSec
        statusText.text = "Отдых"
        restTimerText.visibility = View.VISIBLE
        isResting = true
        wallStableFrames = 0
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
            restTimerText.visibility = View.GONE
            isResting = false
            wallStableFrames = 0
            resetGestureState()
            statusText.text = "Продолжаем!"
            TtsManager.speak("Продолжаем выполнение.")
            if (currentExerciseId == EXERCISE_WALL_ID) {
                wallStarted = false
            }
        }
    }

    private fun finishCurrentExercise() {
        if (isProcessingTransition) return
        isProcessingTransition = true
        AppLogger.log("FINISH", "Exercise completed: $currentExerciseId")

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val db = PoseCounterDatabase.get(this@SessionActivity)
                    val todayUtc = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    val schedules = db.scheduledExerciseDao().getForDate(todayUtc)
                    val schedule = schedules.find {
                        it.exerciseId == currentExerciseId && it.complexType == currentComplexType
                    }
                    if (schedule != null) {
                        db.scheduledExerciseDao().markStatus(schedule.id, ScheduledStatus.DONE, null)
                        AppLogger.log(
                            "DB",
                            "Marked exercise as DONE: $currentExerciseId, complex=$currentComplexType"
                        )
                    } else {
                        AppLogger.log(
                            "DB",
                            "No schedule found for $currentExerciseId, complex=$currentComplexType on today"
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.log("DB", "Error saving status: ${e.message}")
                }
            }

            TtsManager.speak("Упражнение ${currentExercise.name} выполнено.")
            updateCircles(completedId = currentExerciseId)
            delay(2500L)

            pendingIds.remove(currentExerciseId)
            if (pendingIds.isNotEmpty()) {
                currentExerciseId = pendingIds.first()
                currentSet = 1
                repsInSet = 0
                resetSequenceState()
                resetGestureState()
                AppLogger.log("SESSION", "Next exercise: $currentExerciseId, pending=$pendingIds")
                updateCircles()
                updateUi()
                TtsManager.speak(exerciseAnnouncement())
                isProcessingTransition = false
            } else {
                AppLogger.log("SESSION", "All exercises completed")
                TtsManager.speak("Поздравляем! Утренний комплекс упражнений полностью выполнен.")
                Toast.makeText(this@SessionActivity, "Все упражнения выполнены!", Toast.LENGTH_LONG).show()
                delay(4000L)
                finish()
            }
        }
    }

    private fun updateCircles(completedId: String? = null) {
        val circleIds = listOf<Int>(
            R.id.circle1, R.id.circle2, R.id.circle3,
            R.id.circle4, R.id.circle5, R.id.circle6
        )
        for (i in allExercises.indices) {
            val exId = allExercises[i].id
            val circle = findViewById<TextView>(circleIds[i])
            when {
                exId == currentExerciseId -> circle.setBackgroundResource(R.drawable.circle_active)
                !pendingIds.contains(exId) -> circle.setBackgroundResource(R.drawable.circle_completed)
                else -> circle.setBackgroundResource(R.drawable.circle_inactive)
            }
        }
    }

    private fun updateUi() {
        targetText.text = currentExercise.name
        val shownSet = currentSet.coerceAtMost(currentExercise.targetSets)
        val shownRep = when (currentExerciseId) {
            EXERCISE_PIANO_ID -> pianoRepCount
            EXERCISE_WALL_ID -> wallRepCount
            else -> repsInSet.coerceAtMost(currentExercise.repsPerSet)
        }
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