package com.healassist.app.ui.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.healassist.app.data.DifficultyLevel
import com.healassist.app.data.ExerciseSettings
import com.healassist.app.data.ExerciseThresholds
import com.healassist.app.data.ProgramSettings
import com.healassist.app.data.ProgramSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProgramSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProgramSettingsStore(app)

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<ProgramSettings> = _settings

    fun setDuration(days: Int) {
        update { it.copy(durationDays = days.coerceIn(1, 365)) }
    }

    fun setSets(exerciseId: String, sets: Int) {
        updateExercise(exerciseId) { it.copy(sets = sets.coerceIn(1, 20)) }
    }

    fun setReps(exerciseId: String, reps: Int) {
        updateExercise(exerciseId) { it.copy(reps = reps.coerceIn(1, 50)) }
    }

    fun setRestSeconds(exerciseId: String, seconds: Int) {
        updateExercise(exerciseId) { it.copy(restSeconds = seconds.coerceIn(0, 300)) }
    }

    fun setDifficulty(exerciseId: String, difficulty: DifficultyLevel) {
        updateExercise(exerciseId) { it.copy(difficulty = difficulty) }
    }

    fun setUseCustomThresholds(exerciseId: String, enabled: Boolean) {
        updateExercise(exerciseId) { exercise ->
            if (enabled) {
                exercise.copy(
                    useCustomThresholds = true,
                    customThresholds = if (exercise.useCustomThresholds) {
                        exercise.customThresholds
                    } else {
                        ExerciseThresholds.forDifficulty(exercise.difficulty)
                    },
                )
            } else {
                exercise.copy(useCustomThresholds = false)
            }
        }
    }

    fun setThreshold(exerciseId: String, update: (ExerciseThresholds) -> ExerciseThresholds) {
        updateExercise(exerciseId) { exercise ->
            exercise.copy(customThresholds = update(exercise.customThresholds))
        }
    }

    fun reset() {
        store.reset()
        _settings.value = store.load()
    }

    private fun updateExercise(exerciseId: String, update: (ExerciseSettings) -> ExerciseSettings) {
        update { settings -> settings.updateExercise(exerciseId, update) }
    }

    private fun update(update: (ProgramSettings) -> ProgramSettings) {
        val next = update(_settings.value)
        _settings.value = next
        store.save(next)
    }
}
