package com.svensson.titan.data.repository

import com.svensson.titan.data.local.dao.CourseProgressDao
import com.svensson.titan.data.local.toDomain
import com.svensson.titan.data.local.toEntity
import com.svensson.titan.domain.model.CourseProgress
import com.svensson.titan.domain.model.CourseType
import com.svensson.titan.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class CourseRepositoryImpl @Inject constructor(
    private val dao: CourseProgressDao,
) : CourseRepository {

    override fun observeActiveCourse(): Flow<CourseProgress?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun getActiveCourse(): CourseProgress? =
        dao.getActive()?.toDomain()

    override suspend fun startCourse(courseType: CourseType, sessionsPerWeek: Int, monthlyGoalSessions: Int): Long {
        dao.deactivateAll()
        val progress = CourseProgress(
            courseType = courseType,
            sessionsPerWeek = sessionsPerWeek,
            monthlyGoalSessions = monthlyGoalSessions,
            startDate = LocalDate.now(),
            isActive = true,
            totalPoints = 0,
        )
        return dao.insert(progress.toEntity())
    }

    override suspend fun addPoints(courseProgressId: Long, delta: Int) {
        dao.addPoints(courseProgressId, delta)
    }
}
