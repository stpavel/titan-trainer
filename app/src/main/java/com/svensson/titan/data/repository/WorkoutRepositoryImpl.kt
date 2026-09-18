package com.svensson.titan.data.repository

import com.svensson.titan.data.local.dao.WorkoutDao
import com.svensson.titan.data.local.toDomain
import com.svensson.titan.data.local.toEntity
import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {

    override fun observeWorkouts(courseProgressId: Long): Flow<List<Workout>> =
        dao.observeByCourse(courseProgressId).map { list -> list.map { it.toDomain() } }

    override fun observeAllWorkouts(): Flow<List<Workout>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveWorkout(workout: Workout): Long =
        dao.insert(workout.toEntity())

    override suspend fun averagePoints(courseProgressId: Long): Double? =
        dao.averagePoints(courseProgressId)

    override suspend fun hasWorkoutOn(courseProgressId: Long, date: LocalDate): Boolean {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return dao.findWorkoutInRange(courseProgressId, start, end) != null
    }
	
		
    override suspend fun deleteWorkout(id: Long) {
        dao.deleteById(id)
    }
	
	override fun observeTotalPoints(): Flow<Int> = dao.observeTotalPoints()
	
    override suspend fun getRecentByProgram(programId: String, limit: Int): List<Workout> =
        dao.getRecentByProgram(programId, limit).map { it.toDomain() }	
}
