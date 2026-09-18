package com.svensson.titan.di

import android.content.Context
import androidx.room.Room
import com.svensson.titan.data.local.AppDatabase
import com.svensson.titan.data.local.MIGRATION_4_5
import com.svensson.titan.data.local.MIGRATION_5_6
import com.svensson.titan.data.local.dao.CourseProgressDao
import com.svensson.titan.data.local.dao.TrainerReportDao
import com.svensson.titan.data.local.dao.WorkoutDao
import com.svensson.titan.data.local.dao.WorkoutProgramDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "titan_trainer.db")
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideWorkoutProgramDao(database: AppDatabase): WorkoutProgramDao = database.workoutProgramDao()

    @Provides
    fun provideWorkoutDao(database: AppDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideCourseProgressDao(database: AppDatabase): CourseProgressDao = database.courseProgressDao()

    @Provides
    fun provideTrainerReportDao(database: AppDatabase): TrainerReportDao = database.trainerReportDao()
}