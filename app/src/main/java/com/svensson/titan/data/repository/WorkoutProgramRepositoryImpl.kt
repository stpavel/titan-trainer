package com.svensson.titan.data.repository

import com.svensson.titan.data.local.dao.WorkoutProgramDao
import com.svensson.titan.data.local.toDomain
import com.svensson.titan.data.local.toEntity
import com.svensson.titan.domain.model.PredefinedPrograms
import com.svensson.titan.domain.model.WorkoutProgram
import com.svensson.titan.domain.repository.WorkoutProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkoutProgramRepositoryImpl @Inject constructor(
    private val dao: WorkoutProgramDao,
) : WorkoutProgramRepository {

    override fun observeCustomPrograms(): Flow<List<WorkoutProgram>> =
        dao.observeCustom().map { list -> list.map { it.toDomain() } }

    override suspend fun getProgram(id: String): WorkoutProgram? =
        PredefinedPrograms.all.find { it.id == id } ?: dao.getById(id)?.toDomain()

    override suspend fun saveCustomProgram(program: WorkoutProgram) {
        // Для существующей программы держим исходную дату создания: правка не должна
        // выглядеть как создание и переставлять её в начало списка.
        val createdAt = dao.getCreatedAt(program.id) ?: System.currentTimeMillis()
        dao.upsert(program.toEntity(createdAt))
    }

    override suspend fun deleteCustomProgram(id: String) {
        dao.deleteById(id)
    }

    override suspend fun purgeOldTemplateInstances(olderThanDays: Int) {
        val cutoff = System.currentTimeMillis() - olderThanDays * 24L * 60 * 60 * 1000
        dao.deleteOldTemplateInstances(cutoff)
    }
}