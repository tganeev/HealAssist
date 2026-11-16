package com.healassist.app.data

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

object RehabSeed {
    suspend fun seed(db: PoseCounterDatabase) {
        if (db.rehabProgramDao().getActive().isNotEmpty()) return

        val programId = UUID.randomUUID().toString()
        val program = RehabProgramEntity(
            id = programId,
            title = "Мелкая моторика",
            startDate = System.currentTimeMillis(),
            status = "ACTIVE"
        )
        db.rehabProgramDao().upsert(program)

        val exerciseId = UUID.randomUUID().toString()
        val exercises = listOf(
            ProgramExerciseEntity(
                id = "ex1", // <-- Жесткий ID
                programId = programId,
                exerciseType = "FINGER",
                targetReps = 5,
                targetSets = 2,
                restSeconds = 10,
                orderIndex = 0
            ),
            ProgramExerciseEntity(
                id = "ex2", // <-- Жесткий ID
                programId = programId,
                exerciseType = "FINGER",
                targetReps = 5,
                targetSets = 2,
                restSeconds = 10,
                orderIndex = 1
            ),
        )
        db.programExerciseDao().upsertAll(exercises)

        // 👇 ДОБАВЛЯЕМ НАЗНАЧЕНИЕ НА СЕГОДНЯ
        val todayUtc = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val scheduled = ScheduledExerciseEntity(
            id = java.util.UUID.randomUUID().toString(),
            exerciseId = "ex1", // <-- Назначаем первое
            scheduledDate = todayUtc,
            status = "PLANNED"
        )
        db.scheduledExerciseDao().upsert(scheduled)
    }

    fun phaseName(orderIndex: Int): String = when (orderIndex) {
        0 -> "Утренний комплекс"
        else -> "Упражнение ${orderIndex + 1}"
    }
}