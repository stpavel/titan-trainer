package com.svensson.titan.domain.repository

import com.svensson.titan.domain.model.CourseProgress
import com.svensson.titan.domain.model.CourseType
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun observeActiveCourse(): Flow<CourseProgress?>
    suspend fun getActiveCourse(): CourseProgress?
    suspend fun startCourse(courseType: CourseType, sessionsPerWeek: Int, monthlyGoalSessions: Int): Long
    suspend fun addPoints(courseProgressId: Long, delta: Int)
}
