package com.svensson.titan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.svensson.titan.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.OnConflictStrategy

@Dao
interface WorkoutDao {

    /** REPLACE, а не ABORT: импорт CSV с уже существующими id должен обновлять записи,
     *  а не падать на UNIQUE constraint. Заодно чинит восстановление бэкапа в непустую базу. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("SELECT * FROM workouts WHERE courseProgressId = :courseProgressId ORDER BY startedAtEpochMillis DESC")
    fun observeByCourse(courseProgressId: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT AVG(pointsEarned) FROM workouts WHERE courseProgressId = :courseProgressId AND isPenalty = 0")
    suspend fun averagePoints(courseProgressId: Long): Double?

    @Query(
        "SELECT * FROM workouts WHERE courseProgressId = :courseProgressId AND isPenalty = 0 " +
            "AND startedAtEpochMillis BETWEEN :startMillis AND :endMillis LIMIT 1",
    )
    suspend fun findWorkoutInRange(courseProgressId: Long, startMillis: Long, endMillis: Long): WorkoutEntity?
	
	
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)
	
	@Query("SELECT COALESCE(SUM(pointsEarned), 0) FROM workouts")
	fun observeTotalPoints(): Flow<Int>
	
    @Query(
        "SELECT * FROM workouts WHERE programId = :programId AND isPenalty = 0 " +
        "ORDER BY startedAtEpochMillis DESC LIMIT :limit",
    )
    suspend fun getRecentByProgram(programId: String, limit: Int): List<WorkoutEntity>
}
