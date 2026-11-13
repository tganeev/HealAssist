package com.healassist.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "program_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RehabProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("programId")]
)
data class ProgramExerciseEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val exerciseType: String,
    val targetAngle: Double? = null,
    val targetReps: Int,
    val targetSets: Int,
    val restSeconds: Int,
    val orderIndex: Int,
    val durationDays: Int = 7,
    val notes: String? = null,
    val isSynced: Boolean = false,
)