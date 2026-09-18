package com.svensson.titan.data.repository

import com.svensson.titan.domain.repository.TrainerApiRepository
import com.svensson.titan.domain.repository.TrainerApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TrainerApiRepositoryImpl @Inject constructor(
    private val httpClient: OkHttpClient,
) : TrainerApiRepository {

    override suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): TrainerApiResult = withContext(Dispatchers.IO) {
        val client = httpClient.newBuilder()
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            .put("temperature", 0.4)

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use TrainerApiResult.Error(parseErrorMessage(response.code, responseText))
                }
                val content = JSONObject(responseText)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                TrainerApiResult.Success(content)
            }
        } catch (e: IOException) {
            TrainerApiResult.Error("Не удалось связаться с сервером: ${e.message}")
        } catch (e: Exception) {
            TrainerApiResult.Error("Не удалось разобрать ответ сервера: ${e.message}")
        }
    }

    private fun parseErrorMessage(code: Int, rawBody: String): String = try {
        val message = JSONObject(rawBody).optJSONObject("error")?.optString("message")
        if (message.isNullOrBlank()) "Сервер вернул ошибку $code" else "Ошибка $code: $message"
    } catch (e: Exception) {
        "Сервер вернул ошибку $code"
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 90L
    }
}