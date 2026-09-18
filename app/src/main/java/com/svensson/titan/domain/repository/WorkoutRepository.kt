package com.svensson.titan.domain.repository

import com.svensson.titan.domain.model.Workout
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WorkoutRepository {
    fun observeWorkouts(courseProgressId: Long): Flow<List<Workout>>
    fun observeAllWorkouts(): Flow<List<Workout>>
    suspend fun saveWorkout(workout: Workout): Long
    suspend fun averagePoints(courseProgressId: Long): Double?
    suspend fun hasWorkoutOn(courseProgressId: Long, date: LocalDate): Boolean
    suspend fun deleteWorkout(id: Long)
	fun observeTotalPoints(): Flow<Int>
    suspend fun getRecentByProgram(programId: String, limit: Int): List<Workout>
}
