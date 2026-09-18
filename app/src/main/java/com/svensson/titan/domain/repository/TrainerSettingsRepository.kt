package com.svensson.titan.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Настройки фичи "Персональный тренер": свой API-ключ к LLM, OpenAI-совместимый контракт. */
interface TrainerSettingsRepository {
    val isEnabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)

    val apiKey: StateFlow<String>
    fun setApiKey(key: String)

    val model: StateFlow<String>
    fun setModel(model: String)

    val baseUrl: StateFlow<String>
    fun setBaseUrl(url: String)

    companion object {
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}