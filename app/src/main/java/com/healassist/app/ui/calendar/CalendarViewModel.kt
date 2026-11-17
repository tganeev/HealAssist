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

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PoseCounterDatabase.get(app)
    private val scheduledDao = db.scheduledExerciseDao()
    private val exerciseDao = db.programExerciseDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    // Храним список ВСЕХ упражнений на день
    private val _dailyExercises = MutableStateFlow<List<ScheduledExerciseEntity>>(emptyList())
    val dailyExercises: StateFlow<List<ScheduledExerciseEntity>> = _dailyExercises

    init {
        viewModelScope.launch(Dispatchers.IO) {
            RehabSeed.seed(db)
            loadForDate(LocalDate.now())
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch(Dispatchers.IO) { loadForDate(date) }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            loadForDate(_selectedDate.value)
        }
    }

    private suspend fun loadForDate(date: LocalDate) {
        val dateUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // Просто получаем все назначения на этот день
        _dailyExercises.value = scheduledDao.getForDate(dateUtc)
    }

    // Вспомогательная функция для поиска первого невыполненного упражнения
    fun getFirstPendingExerciseId(): String? {
        return _dailyExercises.value.firstOrNull { it.status == ScheduledStatus.PLANNED }?.exerciseId
    }
}