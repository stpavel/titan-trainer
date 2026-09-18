package com.svensson.titan.data.local

import com.svensson.titan.data.local.entity.CourseProgressEntity
import com.svensson.titan.data.local.entity.WorkoutEntity
import com.svensson.titan.data.local.entity.WorkoutProgramEntity
import com.svensson.titan.domain.model.CourseProgress
import com.svensson.titan.domain.model.CourseType
import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.model.WorkoutProgram
import java.time.Instant
import java.time.LocalDate

fun WorkoutEntity.toDomain(): Workout = Workout(
    id = id,
    courseProgressId = courseProgressId,
    programId = programId,
    startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    caloriesKcal = caloriesKcal,
    avgHeartRateBpm = avgHeartRateBpm,
    avgPowerWatts = avgPowerWatts,
    belowZoneSharePercent = belowZoneSharePercent,
    pointsEarned = pointsEarned,
    isPenalty = isPenalty,
)

fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    courseProgressId = courseProgressId,
    programId = programId,
    startedAtEpochMillis = startedAt.toEpochMilli(),
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    caloriesKcal = caloriesKcal,
    avgHeartRateBpm = avgHeartRateBpm,
    avgPowerWatts = avgPowerWatts,
    belowZoneSharePercent = belowZoneSharePercent,
    pointsEarned = pointsEarned,
    isPenalty = isPenalty,
)

fun CourseProgressEntity.toDomain(): CourseProgress = CourseProgress(
    id = id,
    courseType = CourseType.valueOf(courseType),
    sessionsPerWeek = sessionsPerWeek,
    monthlyGoalSessions = monthlyGoalSessions,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    isActive = isActive,
    totalPoints = totalPoints,
)

fun CourseProgress.toEntity(): CourseProgressEntity = CourseProgressEntity(
    id = id,
    courseType = courseType.name,
    sessionsPerWeek = sessionsPerWeek,
    monthlyGoalSessions = monthlyGoalSessions,
    startDateEpochDay = startDate.toEpochDay(),
    isActive = isActive,
    totalPoints = totalPoints,
)

fun WorkoutProgramEntity.toDomain(): WorkoutProgram = WorkoutProgram(
    id = id,
    name = name,
    segments = ProgramSegmentsCodec.decode(segmentsJson),
    isCustom = !isTemplateInstance,
)

/**
 * @param createdAtEpochMillis дата создания. При правке существующей программы сюда
 * передаётся её исходная дата — иначе редактирование выбрасывало бы программу
 * наверх списка как только что созданную.
 */
fun WorkoutProgram.toEntity(
    createdAtEpochMillis: Long = System.currentTimeMillis(),
): WorkoutProgramEntity = WorkoutProgramEntity(
    id = id,
    name = name,
    segmentsJson = ProgramSegmentsCodec.encode(segments),
    createdAtEpochMillis = createdAtEpochMillis,
    isTemplateInstance = !isCustom,
)