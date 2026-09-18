package com.svensson.titan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.svensson.titan.data.local.entity.TrainerReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainerReportDao {

    @Insert
    suspend fun insert(report: TrainerReportEntity): Long

    /** Храним по факту один актуальный отчёт — перед вставкой чистим старые (MVP, без истории). */
    @Query("DELETE FROM trainer_reports")
    suspend fun deleteAll()

    @Query("SELECT * FROM trainer_reports ORDER BY generatedAtEpochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<TrainerReportEntity?>
	
	@Query("SELECT * FROM trainer_reports ORDER BY generatedAtEpochMillis DESC LIMIT 1")
	suspend fun getLatest(): TrainerReportEntity?
}