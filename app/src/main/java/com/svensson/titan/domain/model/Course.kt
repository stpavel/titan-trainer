package com.svensson.titan.domain.model

/**
 * Курсы тренировок. recommendedSessionsPerWeek — значение по умолчанию,
 * которое подставляется при выборе курса; пользователь может изменить его
 * на экране выбора курса.
 */
enum class CourseType(val displayName: String, val recommendedSessionsPerWeek: Int) {
    BEGINNER("Новичок", 3),
    CARDIO("Кардио", 4),
    WEIGHT_LOSS("Сброс веса", 5),
}

/**
 * Активный (или завершённый) курс, который выбрал пользователь.
 * totalPoints — текущий баланс очков по курсу (сумма начислений минус штрафы).
 */
data class CourseProgress(
    val id: Long = 0,
    val courseType: CourseType,
    val sessionsPerWeek: Int,
    val monthlyGoalSessions: Int,
    val startDate: java.time.LocalDate,
    val isActive: Boolean = true,
    val totalPoints: Int = 0,
)
