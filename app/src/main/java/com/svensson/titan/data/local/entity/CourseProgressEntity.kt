package com.svensson.titan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "course_progress")
data class CourseProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseType: String,
    val sessionsPerWeek: Int,
    val monthlyGoalSessions: Int,
    val startDateEpochDay: Long,
    val isActive: Boolean,
    val totalPoints: Int,
)
