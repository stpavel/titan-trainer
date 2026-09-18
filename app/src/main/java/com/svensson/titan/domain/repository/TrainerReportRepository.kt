package com.svensson.titan.domain.repository

import com.svensson.titan.domain.model.TrainerReport
import kotlinx.coroutines.flow.Flow

interface TrainerReportRepository {
    fun observeLatest(): Flow<TrainerReport?>
    suspend fun saveReport(report: TrainerReport)
	suspend fun getLatestReport(): TrainerReport?
}