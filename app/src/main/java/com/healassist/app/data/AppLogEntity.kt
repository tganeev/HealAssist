package com.healassist.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.util.UUID

@Entity(tableName = "app_log")
data class AppLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String,
    val message: String,
    val stackTrace: String? = null,
)

@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_log ORDER BY timestamp DESC LIMIT 100")
    suspend fun getLatest(): List<AppLogEntity>

    @Insert
    suspend fun insert(log: AppLogEntity)

    @Query("DELETE FROM app_log")
    suspend fun clear()
}