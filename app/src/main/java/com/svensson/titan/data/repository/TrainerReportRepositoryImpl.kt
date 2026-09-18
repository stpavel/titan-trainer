package com.svensson.titan.data.repository

import com.svensson.titan.data.local.dao.TrainerReportDao
import com.svensson.titan.data.local.entity.TrainerReportEntity
import com.svensson.titan.domain.model.TrainerReport
import com.svensson.titan.domain.repository.TrainerReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class TrainerReportRepositoryImpl @Inject constructor(
    private val dao: TrainerReportDao,
) : TrainerReportRepository {

    override fun observeLatest(): Flow<TrainerReport?> = dao.observeLatest().map { it?.toDomain() }

    override suspend fun saveReport(report: TrainerReport) {
        dao.deleteAll()
        dao.insert(report.toEntity())
    }
	override suspend fun getLatestReport(): TrainerReport? {
        return dao.getLatest()?.toDomain()
    }
    private fun TrainerReportEntity.toDomain() = TrainerReport(
        id = id,
        generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
        periodStart = LocalDate.ofEpochDay(periodStartEpochDay),
        periodEnd = LocalDate.ofEpochDay(periodEndEpochDay),
        reportText = reportText,
        promptHash = promptHash,
    )

    private fun TrainerReport.toEntity() = TrainerReportEntity(
        id = id,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        periodStartEpochDay = periodStart.toEpochDay(),
        periodEndEpochDay = periodEnd.toEpochDay(),
        reportText = reportText,
        promptHash = promptHash,
    )
}