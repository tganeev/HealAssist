package com.healassist.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object ScheduledStatus {
    const val PLANNED = "PLANNED"
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
}

@Entity(
    tableName = "scheduled_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ProgramExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduledDate"), Index("exerciseId")]
)
data class ScheduledExerciseEntity(
    @PrimaryKey val id: String,
    val patientId: String? = null,
    val exerciseId: String,
    val scheduledDate: Long,
    val status: String = "PLANNED",
    val sessionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)