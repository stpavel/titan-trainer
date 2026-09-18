// app/src/main/java/com/svensson/titan/domain/model/Workout.kt
package com.svensson.titan.domain.model

import java.time.Instant

/**
 * Одна запись в истории: либо завершённая тренировка (isPenalty = false), либо штраф
 * (isPenalty = true, pointsEarned отрицательный). courseProgressId nullable — тренировка
 * и очки не требуют выбранного курса, привязка опциональна (на будущее).
 */
data class Workout(
    val id: Long = 0,
    val courseProgressId: Long? = null,
    /** Программа, по которой шёл заезд. null — свободный заезд без программы. */
    val programId: String? = null,
    val startedAt: Instant,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val caloriesKcal: Int,
    val avgHeartRateBpm: Int?,
    /** Средняя механическая мощность за заезд, Вт. */
    val avgPowerWatts: Int? = null,
    /**
     * Доля времени (0–100) с пульсом ниже целевой зоны. null — пульс не отслеживался
     * достаточно надёжно за эту тренировку (нет пульсометра или разовые касания
     * рукояток консоли, см. MIN_HR_COVERAGE_PERCENT в ActiveWorkoutViewModel).
     */
    val belowZoneSharePercent: Int? = null,
    val pointsEarned: Int,
    val isPenalty: Boolean = false,
)