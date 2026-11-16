package com.healassist.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healassist.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

data class DayExercise(
    val scheduled: ScheduledExerciseEntity,
    val exercise: ProgramExerciseEntity
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PoseCounterDatabase.get(app)
    private val scheduledDao = db.scheduledExerciseDao()
    private val exerciseDao = db.programExerciseDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _exercises = MutableStateFlow<List<DayExercise>>(emptyList())
    val exercises: StateFlow<List<DayExercise>> = _exercises

    init {
        viewModelScope.launch(Dispatchers.IO) {
            RehabSeed.seed(db) // Заполняем БД при старте (если пусто)
            loadForDate(LocalDate.now())
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch(Dispatchers.IO) { loadForDate(date) }
    }

    // Метод для обновления данных при возврате из камеры
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            loadForDate(_selectedDate.value)
        }
    }

    private suspend fun loadForDate(date: LocalDate) {
        val dateUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val items = scheduledDao.getForDate(dateUtc)
        val result = items.mapNotNull { s ->
            val ex = exerciseDao.findById(s.exerciseId) ?: return@mapNotNull null
            DayExercise(s, ex)
        }
        _exercises.value = result
    }
}