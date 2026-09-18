package com.svensson.titan.domain.repository

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/** Настройки пользователя: вес, возраст и зоны пульса. */
interface UserSettingsRepository {
    val weightKg: StateFlow<Int>
    fun setWeightKg(kg: Int)

    /** Возраст в годах. null — не задан пользователем, угадывать/подставлять дефолт нельзя. */
    val ageYears: StateFlow<Int?>
    fun setAgeYears(age: Int?)

    /** Звуковое предупреждение при выходе пульса за пределы зоны. Выключено по умолчанию. */
    val heartRateAlertsEnabled: StateFlow<Boolean>
    fun setHeartRateAlertsEnabled(enabled: Boolean)

    /** Ручное переопределение порогов зоны пульса. null — использовать авторасчёт по AHA. */
    val heartRateLowerOverride: StateFlow<Int?>
    fun setHeartRateLowerOverride(bpm: Int?)

    val heartRateUpperOverride: StateFlow<Int?>
    fun setHeartRateUpperOverride(bpm: Int?)

    companion object {
        const val DEFAULT_WEIGHT_KG = 80
        const val MIN_WEIGHT_KG = 30
        const val MAX_WEIGHT_KG = 200

        const val MIN_AGE_YEARS = 10
        const val MAX_AGE_YEARS = 100

        /**
         * Формулы American Heart Association (heart.org):
         * максимальный пульс ≈ 220 − возраст; умеренная зона 50–70% от максимума,
         * высокая зона 70–85% от максимума. Это усреднённый ориентир, не медицинская норма.
         */
        fun maxHeartRate(age: Int): Int = 220 - age

        fun moderateLowerBound(age: Int): Int = (maxHeartRate(age) * 0.50f).roundToInt()
        fun moderateUpperBound(age: Int): Int = (maxHeartRate(age) * 0.70f).roundToInt()
        fun vigorousUpperBound(age: Int): Int = (maxHeartRate(age) * 0.85f).roundToInt()

        fun effectiveLowerThreshold(age: Int?, override: Int?): Int? =
            override ?: age?.let { moderateLowerBound(it) }

        fun effectiveUpperThreshold(age: Int?, override: Int?): Int? =
            override ?: age?.let { vigorousUpperBound(it) }
    }
}