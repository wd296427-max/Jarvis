package com.jarvis.ai.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AIProvider { GEMINI, OPENAI }

class AIManager(context: Context) {
    private val secrets = EncryptedSharedPreferences.create(
        context,
        "jarvis_secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun saveApiKey(provider: AIProvider, key: String) {
        secrets.edit().putString(keyName(provider), key.trim()).apply()
    }

    fun hasApiKey(provider: AIProvider): Boolean = !secrets.getString(keyName(provider), null).isNullOrBlank()

    suspend fun ask(provider: AIProvider, prompt: String): String = withContext(Dispatchers.IO) {
        val key = secrets.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Add your ${provider.name} API key in Settings first.")

        val payload: JSONObject
        val request: Request
        if (provider == AIProvider.GEMINI) {
            payload = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
                .addHeader("x-goog-api-key", key)
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        } else {
            payload = JSONObject().put("model", "gpt-4o-mini").put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
            request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        }

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "AI request failed (HTTP ${response.code}).")
            }
            val json = JSONObject(raw)
            val answer = if (provider == AIProvider.GEMINI) {
                json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            } else {
                json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            }
            answer?.takeIf { it.isNotBlank() } ?: "The AI service returned an empty response."
        }
    }

    private fun keyName(provider: AIProvider) = when (provider) {
        AIProvider.GEMINI -> "gemini_api_key"
        AIProvider.OPENAI -> "openai_api_key"
    }
}
