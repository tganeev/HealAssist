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

        val ex1 = ProgramExerciseEntity(
            id = "ex1", programId = programId, exerciseType = "FINGER",
            targetReps = 5, targetSets = 2, restSeconds = 10, orderIndex = 0
        )
        val ex2 = ProgramExerciseEntity(
            id = "ex2", programId = programId, exerciseType = "FINGER",
            targetReps = 5, targetSets = 2, restSeconds = 10, orderIndex = 1
        )
        val ex3 = ProgramExerciseEntity(
            id = "ex3", programId = programId, exerciseType = "FINGER",
            targetReps = 5, targetSets = 2, restSeconds = 10, orderIndex = 2
        )
        db.programExerciseDao().upsertAll(listOf(ex1, ex2, ex3))

        // 👇 Создаем 3 назначения на сегодня (каждое упражнение - отдельная запись)
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val scheduledList = listOf(
            ScheduledExerciseEntity(id = UUID.randomUUID().toString(), exerciseId = "ex1", scheduledDate = todayUtc, status = "PLANNED"),
            ScheduledExerciseEntity(id = UUID.randomUUID().toString(), exerciseId = "ex2", scheduledDate = todayUtc, status = "PLANNED"),
            ScheduledExerciseEntity(id = UUID.randomUUID().toString(), exerciseId = "ex3", scheduledDate = todayUtc, status = "PLANNED")
        )
        scheduledList.forEach { db.scheduledExerciseDao().upsert(it) }
    }
}