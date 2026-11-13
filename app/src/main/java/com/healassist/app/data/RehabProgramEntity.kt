package com.healassist.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rehab_program")
data class RehabProgramEntity(
    @PrimaryKey val id: String,
    val patientId: String? = null,
    val title: String,
    val description: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val status: String = "ACTIVE",
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)