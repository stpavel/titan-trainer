package com.svensson.titan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trainer_reports")
data class TrainerReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generatedAtEpochMillis: Long,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val reportText: String,
    val promptHash: String,
)