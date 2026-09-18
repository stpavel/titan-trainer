// app/src/main/java/com/svensson/titan/domain/usecase/SaveWorkoutUseCase.kt
package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.repository.CourseRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import java.time.Instant
import javax.inject.Inject

class SaveWorkoutUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val courseRepository: CourseRepository,
) {
    suspend operator fun invoke(
        startedAt: Instant,
        durationSeconds: Int,
        distanceMeters: Int,
        caloriesKcal: Int,
        avgHeartRateBpm: Int?,
        pointsEarned: Int,
        programId: String? = null,
        avgPowerWatts: Int? = null,
        belowZoneSharePercent: Int? = null,
        courseProgressId: Long? = null,
    ): Int {
        workoutRepository.saveWorkout(
            Workout(
                courseProgressId = courseProgressId,
                programId = programId,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                caloriesKcal = caloriesKcal,
                avgHeartRateBpm = avgHeartRateBpm,
                avgPowerWatts = avgPowerWatts,
                belowZoneSharePercent = belowZoneSharePercent,
                pointsEarned = pointsEarned,
                isPenalty = false,
            ),
        )
        if (courseProgressId != null) courseRepository.addPoints(courseProgressId, pointsEarned)
        return pointsEarned
    }
}