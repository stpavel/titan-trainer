package com.svensson.titan.domain.repository

import com.svensson.titan.domain.model.WorkoutProgram
import kotlinx.coroutines.flow.Flow

interface WorkoutProgramRepository {
    fun observeCustomPrograms(): Flow<List<WorkoutProgram>>

    /** Ищет и среди предустановленных (PredefinedPrograms), и среди сохранённых в Room. */
    suspend fun getProgram(id: String): WorkoutProgram?

    /** Создаёт новую или перезаписывает существующую по id, сохраняя исходную дату создания. */
    suspend fun saveCustomProgram(program: WorkoutProgram)

    suspend fun deleteCustomProgram(id: String)

    /** Убирает скрытые инстансы шаблонов старше указанного возраста. */
    suspend fun purgeOldTemplateInstances(olderThanDays: Int = 7)
}