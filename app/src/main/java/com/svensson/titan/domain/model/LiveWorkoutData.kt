package com.svensson.titan.domain.model

enum class FtmsSource { CROSS_TRAINER, INDOOR_BIKE }

/**
 * Снимок текущих показателей с тренажёра. Поля nullable, т.к. конкретный
 * набор данных зависит от Flags конкретного FTMS-пакета (см. FtmsParser) —
 * тренажёр может не слать часть полей в отдельных кадрах.
 */
data class LiveWorkoutData(
    val source: FtmsSource,
    val instantaneousSpeedKmh: Float? = null,
    val averageSpeedKmh: Float? = null,
    val instantaneousCadenceRpm: Float? = null,
    val averageCadenceRpm: Float? = null,
    val totalDistanceMeters: Int? = null,
    val resistanceLevel: Float? = null,
    val instantaneousPowerWatts: Int? = null,
    val averagePowerWatts: Int? = null,
    val totalEnergyKcal: Int? = null,
    val energyPerHourKcal: Int? = null,
    val energyPerMinuteKcal: Int? = null,
    val heartRateBpm: Int? = null,
    val elapsedTimeSeconds: Int? = null,
    val remainingTimeSeconds: Int? = null,
    /** Диагностика: сырой пакет и флаги — отличить реальный скачок нагрузки от ошибки разбора. */
    val flags: Int? = null,
    val rawHex: String? = null,
    /** Аппаратный счётчик шагов из Cross Trainer Data (0x2ACE) — накопительный. */
    val strideCount: Int? = null,
    /** Шагов в минуту оттуда же. Разрешение 1, делить ни на что не надо. */
    val stepsPerMinute: Float? = null,	
    /** Момент приёма кадра — по нему видно, живы ли уведомления вообще. */
    val receivedAtMs: Long = System.currentTimeMillis(),	
)
