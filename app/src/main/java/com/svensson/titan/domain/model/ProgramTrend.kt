// app/src/main/java/com/svensson/titan/domain/model/ProgramTrend.kt
package com.svensson.titan.domain.model

/** Результат разбора последних тренировок по одной программе. */
sealed interface ProgramTrend {
    /** Меньше 3 заездов с надёжным пульсом по этой программе — сравнивать не с чем. */
    data object InsufficientData : ProgramTrend

    data object Stable : ProgramTrend

    /** Несколько заездов подряд стабильно ниже целевой зоны — похоже, полегчало. */
    data class EasyTrend(val avgBelowZonePercent: Int, val sessionsConsidered: Int) : ProgramTrend
}