package com.svensson.titan.data.repository

import android.content.Context
import com.svensson.titan.domain.repository.UserSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : UserSettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _weightKg = MutableStateFlow(
        prefs.getInt(KEY_WEIGHT, UserSettingsRepository.DEFAULT_WEIGHT_KG),
    )
    override val weightKg: StateFlow<Int> = _weightKg.asStateFlow()

    override fun setWeightKg(kg: Int) {
        val clamped = kg.coerceIn(UserSettingsRepository.MIN_WEIGHT_KG, UserSettingsRepository.MAX_WEIGHT_KG)
        prefs.edit().putInt(KEY_WEIGHT, clamped).apply()
        _weightKg.value = clamped
    }

    private val _ageYears = MutableStateFlow(readNullableInt(KEY_AGE))
    override val ageYears: StateFlow<Int?> = _ageYears.asStateFlow()

    override fun setAgeYears(age: Int?) {
        val clamped = age?.coerceIn(UserSettingsRepository.MIN_AGE_YEARS, UserSettingsRepository.MAX_AGE_YEARS)
        writeNullableInt(KEY_AGE, clamped)
        _ageYears.value = clamped
    }

    private val _heartRateAlertsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HR_ALERTS_ENABLED, false),
    )
    override val heartRateAlertsEnabled: StateFlow<Boolean> = _heartRateAlertsEnabled.asStateFlow()

    override fun setHeartRateAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HR_ALERTS_ENABLED, enabled).apply()
        _heartRateAlertsEnabled.value = enabled
    }

    private val _heartRateLowerOverride = MutableStateFlow(readNullableInt(KEY_HR_LOWER_OVERRIDE))
    override val heartRateLowerOverride: StateFlow<Int?> = _heartRateLowerOverride.asStateFlow()

    override fun setHeartRateLowerOverride(bpm: Int?) {
        writeNullableInt(KEY_HR_LOWER_OVERRIDE, bpm)
        _heartRateLowerOverride.value = bpm
    }

    private val _heartRateUpperOverride = MutableStateFlow(readNullableInt(KEY_HR_UPPER_OVERRIDE))
    override val heartRateUpperOverride: StateFlow<Int?> = _heartRateUpperOverride.asStateFlow()

    override fun setHeartRateUpperOverride(bpm: Int?) {
        writeNullableInt(KEY_HR_UPPER_OVERRIDE, bpm)
        _heartRateUpperOverride.value = bpm
    }

    /** SharedPreferences.getInt требует default — используем sentinel NO_VALUE = "не задано". */
    private fun readNullableInt(key: String): Int? {
        val stored = prefs.getInt(key, NO_VALUE)
        return if (stored == NO_VALUE) null else stored
    }

    private fun writeNullableInt(key: String, value: Int?) {
        prefs.edit().putInt(key, value ?: NO_VALUE).apply()
    }

    private companion object {
        const val PREFS_NAME = "titan_settings"
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_AGE = "age_years"
        const val KEY_HR_ALERTS_ENABLED = "hr_alerts_enabled"
        const val KEY_HR_LOWER_OVERRIDE = "hr_lower_override"
        const val KEY_HR_UPPER_OVERRIDE = "hr_upper_override"
        const val NO_VALUE = -1
    }
}