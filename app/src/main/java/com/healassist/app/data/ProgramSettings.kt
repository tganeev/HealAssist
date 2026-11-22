package com.healassist.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class DifficultyLevel(val label: String) {
    EASY("Лёгкий"),
    MEDIUM("Средний"),
    HARD("Сложный"),
    ;

    companion object {
        fun from(value: String?): DifficultyLevel = entries.find { it.name == value } ?: MEDIUM
    }
}

data class ExerciseThresholds(
    val gestureStableMs: Int = 300,
    val extensionRatio: Float = 1.20f,
    val fanOpenRatio: Float = 1.70f,
    val fanCloseRatio: Float = 1.52f,
    val clawFistRatio: Float = 0.90f,
    val clawStraightRatio: Float = 1.80f,
    val thumbTouchDistance: Float = 0.05f,
    val pianoTouchDistance: Float = 0.025f,
    val wallStableFrames: Int = 6,
    val wallLiftDelta: Float = 0.04f,
    val wallThumbDelta: Float = 0.10f,
    val wallReleaseDelta: Float = 0.02f,
    val wallThumbReleaseDelta: Float = 0.05f,
    val wallDownDelta: Float = 0.05f,
) {
    companion object {
        fun forDifficulty(level: DifficultyLevel): ExerciseThresholds = when (level) {
            DifficultyLevel.EASY -> ExerciseThresholds(
                gestureStableMs = 180,
                extensionRatio = 1.10f,
                fanOpenRatio = 1.55f,
                fanCloseRatio = 1.62f,
                clawFistRatio = 1.00f,
                clawStraightRatio = 1.60f,
                thumbTouchDistance = 0.07f,
                pianoTouchDistance = 0.035f,
                wallStableFrames = 4,
                wallLiftDelta = 0.03f,
                wallThumbDelta = 0.075f,
                wallReleaseDelta = 0.015f,
                wallThumbReleaseDelta = 0.035f,
                wallDownDelta = 0.07f,
            )
            DifficultyLevel.MEDIUM -> ExerciseThresholds()
            DifficultyLevel.HARD -> ExerciseThresholds(
                gestureStableMs = 450,
                extensionRatio = 1.35f,
                fanOpenRatio = 1.85f,
                fanCloseRatio = 1.42f,
                clawFistRatio = 0.80f,
                clawStraightRatio = 2.00f,
                thumbTouchDistance = 0.035f,
                pianoTouchDistance = 0.018f,
                wallStableFrames = 9,
                wallLiftDelta = 0.055f,
                wallThumbDelta = 0.13f,
                wallReleaseDelta = 0.03f,
                wallThumbReleaseDelta = 0.065f,
                wallDownDelta = 0.035f,
            )
        }

        private const val PREFIX = "threshold"

        fun fromPrefs(prefs: SharedPreferences, exerciseId: String): ExerciseThresholds {
            val defaults = ExerciseThresholds()
            fun float(name: String, default: Float) =
                prefs.getFloat("${exerciseId}_${PREFIX}_$name", default)
            fun int(name: String, default: Int) =
                prefs.getInt("${exerciseId}_${PREFIX}_$name", default)
            return ExerciseThresholds(
                gestureStableMs = int("gestureStableMs", defaults.gestureStableMs),
                extensionRatio = float("extensionRatio", defaults.extensionRatio),
                fanOpenRatio = float("fanOpenRatio", defaults.fanOpenRatio),
                fanCloseRatio = float("fanCloseRatio", defaults.fanCloseRatio),
                clawFistRatio = float("clawFistRatio", defaults.clawFistRatio),
                clawStraightRatio = float("clawStraightRatio", defaults.clawStraightRatio),
                thumbTouchDistance = float("thumbTouchDistance", defaults.thumbTouchDistance),
                pianoTouchDistance = float("pianoTouchDistance", defaults.pianoTouchDistance),
                wallStableFrames = int("wallStableFrames", defaults.wallStableFrames),
                wallLiftDelta = float("wallLiftDelta", defaults.wallLiftDelta),
                wallThumbDelta = float("wallThumbDelta", defaults.wallThumbDelta),
                wallReleaseDelta = float("wallReleaseDelta", defaults.wallReleaseDelta),
                wallThumbReleaseDelta = float("wallThumbReleaseDelta", defaults.wallThumbReleaseDelta),
                wallDownDelta = float("wallDownDelta", defaults.wallDownDelta),
            )
        }

        fun saveToPrefs(prefs: SharedPreferences.Editor, exerciseId: String, value: ExerciseThresholds) {
            fun float(name: String, v: Float) = prefs.putFloat("${exerciseId}_${PREFIX}_$name", v)
            fun int(name: String, v: Int) = prefs.putInt("${exerciseId}_${PREFIX}_$name", v)
            int("gestureStableMs", value.gestureStableMs)
            float("extensionRatio", value.extensionRatio)
            float("fanOpenRatio", value.fanOpenRatio)
            float("fanCloseRatio", value.fanCloseRatio)
            float("clawFistRatio", value.clawFistRatio)
            float("clawStraightRatio", value.clawStraightRatio)
            float("thumbTouchDistance", value.thumbTouchDistance)
            float("pianoTouchDistance", value.pianoTouchDistance)
            int("wallStableFrames", value.wallStableFrames)
            float("wallLiftDelta", value.wallLiftDelta)
            float("wallThumbDelta", value.wallThumbDelta)
            float("wallReleaseDelta", value.wallReleaseDelta)
            float("wallThumbReleaseDelta", value.wallThumbReleaseDelta)
            float("wallDownDelta", value.wallDownDelta)
        }
    }
}

data class ExerciseSettings(
    val exerciseId: String,
    val name: String,
    val sets: Int = 2,
    val reps: Int = 5,
    val restSeconds: Int = 10,
    val difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
    val useCustomThresholds: Boolean = false,
    val customThresholds: ExerciseThresholds = ExerciseThresholds(),
) {
    val effectiveThresholds: ExerciseThresholds
        get() = if (useCustomThresholds) customThresholds else ExerciseThresholds.forDifficulty(difficulty)
}

data class ProgramSettings(
    val durationDays: Int = 120,
    val exercises: List<ExerciseSettings> = defaultExercises(),
) {
    fun exercise(exerciseId: String): ExerciseSettings =
        exercises.find { it.exerciseId == exerciseId } ?: exercises.first()

    fun updateExercise(exerciseId: String, update: (ExerciseSettings) -> ExerciseSettings): ProgramSettings =
        copy(exercises = exercises.map { if (it.exerciseId == exerciseId) update(it) else it })

    companion object {
        fun defaultExercises(): List<ExerciseSettings> {
            val template = RehabProgramCatalog.STROKE_REHABILITATION
            return template.exerciseIds.indices.map { i ->
                ExerciseSettings(
                    exerciseId = template.exerciseIds[i],
                    name = template.exerciseNames[i],
                )
            }
        }
    }
}

class ProgramSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ProgramSettings = ProgramSettings(
        durationDays = prefs.getInt(KEY_DURATION, DEFAULT_DURATION),
        exercises = ProgramSettings.defaultExercises().map { default ->
            val id = default.exerciseId
            default.copy(
                sets = prefs.getInt("${id}_sets", default.sets),
                reps = prefs.getInt("${id}_reps", default.reps),
                restSeconds = prefs.getInt("${id}_rest", default.restSeconds),
                difficulty = DifficultyLevel.from(prefs.getString("${id}_difficulty", null)),
                useCustomThresholds = prefs.getBoolean("${id}_custom", false),
                customThresholds = ExerciseThresholds.fromPrefs(prefs, id),
            )
        },
    )

    fun save(settings: ProgramSettings) {
        prefs.edit {
            putInt(KEY_DURATION, settings.durationDays)
            settings.exercises.forEach { exercise ->
                val id = exercise.exerciseId
                putInt("${id}_sets", exercise.sets)
                putInt("${id}_reps", exercise.reps)
                putInt("${id}_rest", exercise.restSeconds)
                putString("${id}_difficulty", exercise.difficulty.name)
                putBoolean("${id}_custom", exercise.useCustomThresholds)
                ExerciseThresholds.saveToPrefs(this, id, exercise.customThresholds)
            }
        }
    }

    fun reset() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_NAME = "program_settings"
        const val KEY_DURATION = "duration_days"
        const val DEFAULT_DURATION = 120
    }
}
