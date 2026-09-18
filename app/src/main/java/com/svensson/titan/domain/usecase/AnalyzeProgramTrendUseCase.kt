// app/src/main/java/com/svensson/titan/domain/usecase/AnalyzeProgramTrendUseCase.kt
package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.model.ProgramTrend
import com.svensson.titan.domain.repository.WorkoutRepository
import javax.inject.Inject
import kotlin.math.roundToInt

private const val SESSIONS_LOOKBACK = 3
/** Сканируем с запасом — часть заездов может быть без надёжного пульса. */
private const val SESSIONS_SCANNED = 8
private const val EASY_THRESHOLD_PERCENT = 50

/**
 * Решает, не пора ли поднять нагрузку по конкретной программе. Сигнал — доля
 * времени ниже целевой пульсовой зоны на нескольких последних заездах подряд,
 * а не один случайный лёгкий день.
 *
 * Средняя мощность (avgPowerWatts) в Workout уже сохраняется, но пока не участвует
 * в решении — планировалось как подтверждающий сигнал (Efficiency Factor), но это
 * лишняя сложность для первой версии. Если belowZoneShare начнёт давать ложные
 * срабатывания (например, из-за жары или плохого сна) — это то место, куда
 * добавлять проверку по EF.
 */
class AnalyzeProgramTrendUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(programId: String): ProgramTrend {
        val recent = workoutRepository.getRecentByProgram(programId, SESSIONS_SCANNED)
            .mapNotNull { workout -> workout.belowZoneSharePercent?.let { workout to it } }
            .take(SESSIONS_LOOKBACK)

        if (recent.size < SESSIONS_LOOKBACK) return ProgramTrend.InsufficientData

        val shares = recent.map { it.second }
        val avgBelowZone = shares.average()
        val latestBelowZone = shares.first()

        val easy = latestBelowZone >= EASY_THRESHOLD_PERCENT && avgBelowZone >= EASY_THRESHOLD_PERCENT
        return if (easy) {
            ProgramTrend.EasyTrend(avgBelowZonePercent = avgBelowZone.roundToInt(), sessionsConsidered = recent.size)
        } else {
            ProgramTrend.Stable
        }
    }
}