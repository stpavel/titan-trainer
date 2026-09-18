// app/src/main/java/com/svensson/titan/data/local/AppDatabase.kt
package com.svensson.titan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.svensson.titan.data.local.dao.CourseProgressDao
import com.svensson.titan.data.local.dao.TrainerReportDao
import com.svensson.titan.data.local.dao.WorkoutDao
import com.svensson.titan.data.local.dao.WorkoutProgramDao
import com.svensson.titan.data.local.entity.CourseProgressEntity
import com.svensson.titan.data.local.entity.TrainerReportEntity
import com.svensson.titan.data.local.entity.WorkoutEntity
import com.svensson.titan.data.local.entity.WorkoutProgramEntity

/**
 * 4 → 5: добавлены programId, avgPowerWatts, belowZoneSharePercent в workouts —
 * для оценки тренировки и рекомендаций по нагрузке. ALTER TABLE, не пересоздание:
 * в базе уже реальная история тренировок, destructive-миграция её бы стёрла.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workouts ADD COLUMN programId TEXT")
        db.execSQL("ALTER TABLE workouts ADD COLUMN avgPowerWatts INTEGER")
        db.execSQL("ALTER TABLE workouts ADD COLUMN belowZoneSharePercent INTEGER")
    }
}

/**
 * 5 → 6: новая таблица trainer_reports — кэш последнего отчёта ИИ-тренера
 * (вместе с хэшем промпта, чтобы не дёргать LLM повторно, если данные не изменились).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS trainer_reports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "generatedAtEpochMillis INTEGER NOT NULL, " +
                "periodStartEpochDay INTEGER NOT NULL, " +
                "periodEndEpochDay INTEGER NOT NULL, " +
                "reportText TEXT NOT NULL, " +
                "promptHash TEXT NOT NULL DEFAULT '')",
        )
    }
}

@Database(
    entities = [WorkoutEntity::class, CourseProgressEntity::class, WorkoutProgramEntity::class, TrainerReportEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun courseProgressDao(): CourseProgressDao
    abstract fun workoutProgramDao(): WorkoutProgramDao
    abstract fun trainerReportDao(): TrainerReportDao
}