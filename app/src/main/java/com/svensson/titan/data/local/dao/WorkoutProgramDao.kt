package com.svensson.titan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.svensson.titan.data.local.entity.WorkoutProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutProgramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: WorkoutProgramEntity)

    @Query("SELECT * FROM workout_programs WHERE isTemplateInstance = 0 ORDER BY createdAtEpochMillis DESC")
    fun observeCustom(): Flow<List<WorkoutProgramEntity>>

    @Query("SELECT * FROM workout_programs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutProgramEntity?

    /** Дата создания отдельно — чтобы при правке программы не затирать её новой датой. */
    @Query("SELECT createdAtEpochMillis FROM workout_programs WHERE id = :id LIMIT 1")
    suspend fun getCreatedAt(id: String): Long?

    @Query("DELETE FROM workout_programs WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Инстансы шаблонов сохраняются при каждом запуске готовой программы и пользователю
     * не видны — чистим старые, иначе таблица растёт бесконечно.
     */
    @Query("DELETE FROM workout_programs WHERE isTemplateInstance = 1 AND createdAtEpochMillis < :beforeEpochMillis")
    suspend fun deleteOldTemplateInstances(beforeEpochMillis: Long)
}