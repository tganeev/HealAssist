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
                id = exerciseId,
                programId = programId,
                exerciseType = "FINGER",
                targetAngle = null,
                targetReps = 5, // Для теста 5 раз
                targetSets = 2,
                restSeconds = 10,
                orderIndex = 0,
                notes = "Кулак"
            )
        )
        db.programExerciseDao().upsertAll(exercises)

        // 👇 ДОБАВЛЯЕМ НАЗНАЧЕНИЕ НА СЕГОДНЯ
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val scheduled = ScheduledExerciseEntity(
            id = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            scheduledDate = todayUtc,
            status = "PLANNED"
        )
        db.scheduledExerciseDao().upsert(scheduled)
    }

    fun phaseName(orderIndex: Int): String = when (orderIndex) {
        0 -> "Упражнение 1: Кулак"
        else -> "Упражнение ${orderIndex + 1}"
    }
}