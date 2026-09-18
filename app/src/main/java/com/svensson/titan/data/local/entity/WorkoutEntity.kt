// app/src/main/java/com/svensson/titan/data/local/entity/WorkoutEntity.kt
package com.svensson.titan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = CourseProgressEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseProgressId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseProgressId")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // NULL = тренировка вне курса (обычный случай). FK на NULL не проверяется.
    val courseProgressId: Long? = null,
    val programId: String? = null,
    val startedAtEpochMillis: Long,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val caloriesKcal: Int,
    val avgHeartRateBpm: Int?,
    val avgPowerWatts: Int? = null,
    val belowZoneSharePercent: Int? = null,
    val pointsEarned: Int,
    val isPenalty: Boolean,
)