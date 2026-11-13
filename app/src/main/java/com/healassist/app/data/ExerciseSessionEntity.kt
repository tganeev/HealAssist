package com.healassist.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_session",
    indices = [Index("programId"), Index("exerciseId")]
)
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val patientId: String? = null,
    val programId: String,
    val exerciseId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val completedReps: Int,
    val completedSets: Int,
    val maxAngle: Double? = null,
    val targetAngle: Double? = null,
    val targetReps: Int,
    val targetSets: Int,
    val syncStatus: String = "PENDING"
)