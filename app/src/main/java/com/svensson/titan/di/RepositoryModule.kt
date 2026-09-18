package com.svensson.titan.di

import com.svensson.titan.data.repository.BleRepositoryImpl
import com.svensson.titan.data.repository.CourseRepositoryImpl
import com.svensson.titan.data.repository.TrainerApiRepositoryImpl
import com.svensson.titan.data.repository.TrainerReportRepositoryImpl
import com.svensson.titan.data.repository.TrainerSettingsRepositoryImpl
import com.svensson.titan.data.repository.UserSettingsRepositoryImpl
import com.svensson.titan.data.repository.WorkoutProgramRepositoryImpl
import com.svensson.titan.data.repository.WorkoutRepositoryImpl
import com.svensson.titan.domain.repository.BleRepository
import com.svensson.titan.domain.repository.CourseRepository
import com.svensson.titan.domain.repository.TrainerApiRepository
import com.svensson.titan.domain.repository.TrainerReportRepository
import com.svensson.titan.domain.repository.TrainerSettingsRepository
import com.svensson.titan.domain.repository.UserSettingsRepository
import com.svensson.titan.domain.repository.WorkoutProgramRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutProgramRepository(impl: WorkoutProgramRepositoryImpl): WorkoutProgramRepository

    @Binds
    @Singleton
    abstract fun bindUserSettingsRepository(impl: UserSettingsRepositoryImpl): UserSettingsRepository

    @Binds
    @Singleton
    abstract fun bindTrainerSettingsRepository(impl: TrainerSettingsRepositoryImpl): TrainerSettingsRepository

    @Binds
    @Singleton
    abstract fun bindTrainerApiRepository(impl: TrainerApiRepositoryImpl): TrainerApiRepository

    @Binds
    @Singleton
    abstract fun bindTrainerReportRepository(impl: TrainerReportRepositoryImpl): TrainerReportRepository
}