package com.svensson.titan.domain.repository

/** Результат обращения к LLM: успешный текст ответа или сообщение об ошибке для показа пользователю. */
sealed class TrainerApiResult {
    data class Success(val text: String) : TrainerApiResult()
    data class Error(val message: String) : TrainerApiResult()
}

/**
 * Порт к LLM по OpenAI-совместимому контракту `/chat/completions`. baseUrl/apiKey/model —
 * параметры вызова, а не поля реализации: пользователь может сменить их в настройках
 * в рантайме без пересоздания графа зависимостей.
 */
interface TrainerApiRepository {
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): TrainerApiResult
}