package com.healassist.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RehabProgramDao {
    @Query("SELECT * FROM rehab_program WHERE status = 'ACTIVE' ORDER BY startDate DESC")
    suspend fun getActive(): List<RehabProgramEntity>

    @Query("SELECT * FROM rehab_program WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RehabProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: RehabProgramEntity)
}

@Dao
interface ProgramExerciseDao {
    @Query("SELECT * FROM program_exercise WHERE programId = :programId ORDER BY orderIndex ASC")
    suspend fun getForProgram(programId: String): List<ProgramExerciseEntity>

    @Query("SELECT * FROM program_exercise WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProgramExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<ProgramExerciseEntity>)
}

@Dao
interface ScheduledExerciseDao {
    @Query("""
        SELECT se.* FROM scheduled_exercise se
        INNER JOIN program_exercise pe ON se.exerciseId = pe.id
        WHERE se.scheduledDate = :date
        ORDER BY pe.orderIndex ASC
    """)
    suspend fun getForDate(date: Long): List<ScheduledExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ScheduledExerciseEntity)
}

@Dao
interface ExerciseSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ExerciseSessionEntity)
}