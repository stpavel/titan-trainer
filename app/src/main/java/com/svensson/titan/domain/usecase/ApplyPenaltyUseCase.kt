package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.repository.CourseRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Штраф за пропуск запланированного дня тренировки = 1/3 от среднего
 * начисления за тренировку по этому курсу (округление вверх до целого,
 * минимум 1 очко). Если истории тренировок ещё нет (курс только начат),
 * используется DEFAULT_AVERAGE — тоже заглушка, подбери свою стартовую
 * цифру или другую политику для "первого" штрафа.
 */
class ApplyPenaltyUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val courseRepository: CourseRepository,
) {
    suspend operator fun invoke(courseProgressId: Long, missedDate: LocalDate) {
        val avg = workoutRepository.averagePoints(courseProgressId) ?: DEFAULT_AVERAGE
        val penalty = (avg / 3).roundToInt().coerceAtLeast(1)

        workoutRepository.saveWorkout(
            Workout(
                courseProgressId = courseProgressId,
                startedAt = missedDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                durationSeconds = 0,
                distanceMeters = 0,
                caloriesKcal = 0,
                avgHeartRateBpm = null,
                pointsEarned = -penalty,
                isPenalty = true,
            ),
        )
        courseRepository.addPoints(courseProgressId, -penalty)
    }

    companion object {
        private const val DEFAULT_AVERAGE = 30.0
    }
}
