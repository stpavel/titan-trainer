package com.svensson.titan.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.svensson.titan.domain.repository.CourseRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import com.svensson.titan.domain.usecase.ApplyPenaltyUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Раз в сутки проверяет: был ли вчера "запланированный" день тренировки
 * по активному курсу, и если да — была ли тренировка фактически пройдена.
 * Если нет — начисляет штраф через ApplyPenaltyUseCase.
 *
 * ПРИМЕЧАНИЕ по product-логике: в исходном ТЗ пользователь задаёт только
 * "частоту в неделю" и "цель на месяц", без выбора конкретных дней недели.
 * Здесь это упрощено до равномерного распределения (scheduledDaysOfWeek) —
 * например, 3 трен./нед. -> Пн/Ср/Пт. Если тебе нужна другая политика
 * (пользователь сам выбирает дни, либо штраф считается по итогам недели
 * целиком, а не по конкретному дню) — это меняется только в этом файле,
 * остальной код от точной формулы не зависит.
 */
@HiltWorker
class PenaltyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val courseRepository: CourseRepository,
    private val workoutRepository: WorkoutRepository,
    private val applyPenaltyUseCase: ApplyPenaltyUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val course = courseRepository.getActiveCourse() ?: return Result.success()
        val yesterday = LocalDate.now().minusDays(1)

        if (yesterday.isBefore(course.startDate)) return Result.success()

        val scheduledDays = scheduledDaysOfWeek(course.sessionsPerWeek)
        if (yesterday.dayOfWeek !in scheduledDays) return Result.success()

        val hadWorkout = workoutRepository.hasWorkoutOn(course.id, yesterday)
        if (!hadWorkout) {
            applyPenaltyUseCase(course.id, yesterday)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "penalty_check_worker"

        fun scheduledDaysOfWeek(sessionsPerWeek: Int): Set<DayOfWeek> {
            val clamped = sessionsPerWeek.coerceIn(1, 7)
            // DayOfWeek — enum из java.time (не из Kotlin), поэтому берём values(),
            // а не entries (entries доступен только для enum-классов, скомпилированных Kotlin'ом).
            val allDays = DayOfWeek.values()
            val step = 7.0 / clamped
            return (0 until clamped).map { allDays[(it * step).toInt() % 7] }.toSet()
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PenaltyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelayMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Первый запуск планируем на 03:00 следующих суток, дальше — раз в 24 часа. */
        private fun calculateInitialDelayMillis(): Long {
            val now = LocalDateTime.now()
            val nextRun = now.toLocalDate().plusDays(1).atTime(3, 0)
            return Duration.between(now, nextRun).toMillis()
        }
    }
}
