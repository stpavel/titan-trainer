package com.svensson.titan.domain.model

import java.time.Instant
import java.time.LocalDate

/** Последний сгенерированный разбор от ИИ-тренера. Хранится один — история версий не нужна на MVP. */
data class TrainerReport(
    val id: Long = 0,
    val generatedAt: Instant,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val reportText: String,
    val promptHash: String,
)