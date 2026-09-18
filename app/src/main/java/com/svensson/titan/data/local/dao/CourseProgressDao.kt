package com.svensson.titan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.svensson.titan.data.local.entity.CourseProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseProgressDao {

    @Insert
    suspend fun insert(course: CourseProgressEntity): Long

    @Query("SELECT * FROM course_progress WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<CourseProgressEntity?>

    @Query("SELECT * FROM course_progress WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): CourseProgressEntity?

    @Query("UPDATE course_progress SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query("UPDATE course_progress SET totalPoints = totalPoints + :delta WHERE id = :id")
    suspend fun addPoints(id: Long, delta: Int)
}
