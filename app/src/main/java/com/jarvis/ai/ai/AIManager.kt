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
    private val appContext = context.applicationContext
    private val secrets = EncryptedSharedPreferences.create(
        appContext, "jarvis_secure_settings",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val memory = ConversationMemory(appContext)
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun saveApiKey(provider: AIProvider, key: String) {
        secrets.edit().putString(keyName(provider), key.trim()).apply()
    }

    fun hasApiKey(provider: AIProvider): Boolean = !secrets.getString(keyName(provider), null).isNullOrBlank()
    fun clearMemory() = memory.clear()

    suspend fun ask(provider: AIProvider, prompt: String): String = withContext(Dispatchers.IO) {
        val key = secrets.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Add your ${provider.name} API key in the app first.")
        val history = memory.recent(12)
        val systemPrompt = "You are JARVIS, a concise, capable Android personal assistant. You can explain Android actions, but never claim to have changed device settings unless the app confirms it. Use the conversation history for continuity."
        val payload: JSONObject
        val request: Request

        if (provider == AIProvider.GEMINI) {
            val contents = JSONArray()
            history.forEach { (role, text) ->
                contents.put(JSONObject().put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            payload = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)
            request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
                .addHeader("x-goog-api-key", key)
                .post(payload.toString().toRequestBody(jsonType)).build()
        } else {
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
            messages.put(JSONObject().put("role", "user").put("content", prompt))
            payload = JSONObject().put("model", "gpt-4o-mini").put("messages", messages)
            request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody(jsonType)).build()
        }

        val answer = client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "AI request failed (HTTP ${response.code}).")
            }
            val json = JSONObject(raw)
            val text = if (provider == AIProvider.GEMINI) {
                json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            } else {
                json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            }
            text?.takeIf { it.isNotBlank() } ?: "The AI service returned an empty response."
        }
        memory.add("user", prompt)
        memory.add("assistant", answer)
        answer
    }

    private fun keyName(provider: AIProvider) =
        if (provider == AIProvider.GEMINI) "gemini_api_key" else "openai_api_key"
}
