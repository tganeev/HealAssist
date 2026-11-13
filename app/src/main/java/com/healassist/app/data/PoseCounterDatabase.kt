package com.healassist.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RehabProgramEntity::class,
        ProgramExerciseEntity::class,
        ScheduledExerciseEntity::class,
        ExerciseSessionEntity::class,
        AppLogEntity::class // <-- 1. ДОБАВЛЯЕМ ЛОГИ СЮДА
    ],
    version = 2,
    exportSchema = false
)
abstract class PoseCounterDatabase : RoomDatabase() {
    abstract fun rehabProgramDao(): RehabProgramDao
    abstract fun programExerciseDao(): ProgramExerciseDao
    abstract fun scheduledExerciseDao(): ScheduledExerciseDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun appLogDao(): AppLogDao // <-- 2. ДОБАВЛЯЕМ ЭТОТ МЕТОД

    companion object {
        @Volatile
        private var INSTANCE: PoseCounterDatabase? = null

        fun get(context: Context): PoseCounterDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PoseCounterDatabase::class.java,
                    "heal_assist.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}