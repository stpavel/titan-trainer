package com.svensson.titan.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.svensson.titan.domain.repository.TrainerSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API-ключ — секрет, поэтому отдельный EncryptedSharedPreferences, а не общий
 * titan_settings (см. UserSettingsRepositoryImpl), где лежат вес/возраст.
 */
@Singleton
class TrainerSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : TrainerSettingsRepository {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    override val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    override fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
        _apiKey.value = key
    }

    private val _model = MutableStateFlow(
        prefs.getString(KEY_MODEL, TrainerSettingsRepository.DEFAULT_MODEL) ?: TrainerSettingsRepository.DEFAULT_MODEL,
    )
    override val model: StateFlow<String> = _model.asStateFlow()

    override fun setModel(model: String) {
        val value = model.ifBlank { TrainerSettingsRepository.DEFAULT_MODEL }
        prefs.edit().putString(KEY_MODEL, value).apply()
        _model.value = value
    }

    private val _baseUrl = MutableStateFlow(
        prefs.getString(KEY_BASE_URL, TrainerSettingsRepository.DEFAULT_BASE_URL) ?: TrainerSettingsRepository.DEFAULT_BASE_URL,
    )
    override val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    override fun setBaseUrl(url: String) {
        val value = url.ifBlank { TrainerSettingsRepository.DEFAULT_BASE_URL }
        prefs.edit().putString(KEY_BASE_URL, value).apply()
        _baseUrl.value = value
    }

    private companion object {
        const val PREFS_NAME = "titan_trainer_secure"
        const val KEY_ENABLED = "enabled"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_BASE_URL = "base_url"
    }
}