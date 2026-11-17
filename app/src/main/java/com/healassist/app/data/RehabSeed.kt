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

        // Создаем 6 упражнений
        val exIds = listOf("ex1", "ex2", "ex3", "ex4", "ex5", "ex6")
        val exercises = exIds.mapIndexed { index, id ->
            ProgramExerciseEntity(
                id = id, programId = programId, exerciseType = "FINGER",
                targetReps = 5, targetSets = 2, restSeconds = 10, orderIndex = index
            )
        }
        db.programExerciseDao().upsertAll(exercises)

        // Назначаем все 6 на сегодня
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        exIds.forEach { id ->
            db.scheduledExerciseDao().upsert(
                ScheduledExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    exerciseId = id,
                    scheduledDate = todayUtc,
                    status = "PLANNED"
                )
            )
        }
    }
}