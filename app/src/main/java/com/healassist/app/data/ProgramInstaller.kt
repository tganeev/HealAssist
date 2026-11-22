package com.healassist.app.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class RehabProgramTemplate(
    val id: String,
    val title: String,
    val description: String,
    val durationDays: Int,
    val exerciseIds: List<String>,
    val exerciseNames: List<String> = emptyList(),
    val isAvailable: Boolean = true,
)

object RehabProgramCatalog {
    val STROKE_REHABILITATION = RehabProgramTemplate(
        id = "stroke-rehabilitation-120",
        title = "Реабилитация после инсульта",
        description = "120-дневная программа восстановления мелкой моторики рук. " +
            "Каждый день включает утренний и вечерний комплексы из 6 упражнений.",
        durationDays = 120,
        exerciseIds = listOf("ex1", "ex2", "ex3", "ex4", "ex5", "ex6"),
        exerciseNames = listOf(
            "Сжатие кулака",
            "Веер",
            "Когти",
            "Подушечки",
            "Стена",
            "Пианино",
        ),
    )

    val all: List<RehabProgramTemplate> = listOf(
        STROKE_REHABILITATION,
    )
}

object ProgramInstaller {
    suspend fun install(
        db: PoseCounterDatabase,
        template: RehabProgramTemplate,
        settings: ProgramSettings,
        startDate: LocalDate,
    ): RehabProgramEntity {
        val durationDays = settings.durationDays.coerceAtLeast(1)
        val startUtc = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val lastDate = startDate.plusDays((durationDays - 1).toLong())
        val endUtc = lastDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        return db.withTransaction {
            db.rehabProgramDao().deleteAll()

            val program = RehabProgramEntity(
                id = UUID.randomUUID().toString(),
                title = template.title,
                description = template.description,
                startDate = startUtc,
                endDate = endUtc,
                status = "ACTIVE",
            )
            db.rehabProgramDao().upsert(program)

            val exercises = template.exerciseIds.mapIndexed { index, exerciseId ->
                val exerciseSettings = settings.exercise(exerciseId)
                ProgramExerciseEntity(
                    id = exerciseId,
                    programId = program.id,
                    exerciseType = "FINGER",
                    targetReps = exerciseSettings.reps,
                    targetSets = exerciseSettings.sets,
                    restSeconds = exerciseSettings.restSeconds,
                    orderIndex = index,
                    durationDays = durationDays,
                )
            }
            db.programExerciseDao().upsertAll(exercises)

            val scheduled = ArrayList<ScheduledExerciseEntity>(
                durationDays * template.exerciseIds.size * 2
            )
            for (day in 0 until durationDays) {
                val dateUtc = startDate.plusDays(day.toLong())
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                for (complexType in listOf(ComplexType.MORNING, ComplexType.EVENING)) {
                    for (exerciseId in template.exerciseIds) {
                        scheduled += ScheduledExerciseEntity(
                            id = UUID.randomUUID().toString(),
                            exerciseId = exerciseId,
                            scheduledDate = dateUtc,
                            complexType = complexType,
                            status = ScheduledStatus.PLANNED,
                        )
                    }
                }
            }
            db.scheduledExerciseDao().upsertAll(scheduled)

            program
        }
    }
}
