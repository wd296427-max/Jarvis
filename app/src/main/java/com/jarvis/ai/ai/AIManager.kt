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

enum class AIProvider { OPENROUTER }

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

    fun saveApiKey(key: String) { secrets.edit().putString("openrouter_api_key", key.trim()).apply() }
    fun hasApiKey(): Boolean = !secrets.getString("openrouter_api_key", null).isNullOrBlank()
    fun saveModel(model: String) { if (model.isNotBlank()) secrets.edit().putString("openrouter_model", model.trim()).apply() }
    fun model(): String = secrets.getString("openrouter_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun activeProvider(): AIProvider = AIProvider.OPENROUTER
    fun setActiveProvider(@Suppress("UNUSED_PARAMETER") provider: AIProvider) { }
    fun recentConversation(): List<Pair<String, String>> = memory.recent(40)
    fun clearMemory() = memory.clear()
    suspend fun ask(@Suppress("UNUSED_PARAMETER") provider: AIProvider, prompt: String): String = ask(prompt)

    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val key = secrets.getString("openrouter_api_key", null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Add your OpenRouter API key in the app first.")
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "You are JARVIS, a concise Android personal assistant. Never claim to change device settings unless the app confirms it. Use conversation history for continuity."))
        memory.recent(12).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject().put("model", model()).put("messages", messages)
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/wd296427-max/Jarvis")
            .addHeader("X-Title", "JARVIS Android Assistant")
            .post(payload.toString().toRequestBody(jsonType)).build()
        val answer = client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "OpenRouter request failed (HTTP ${response.code}). Check API key, model, and account credits.")
            }
            JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.optString("content")?.takeIf { it.isNotBlank() } ?: "OpenRouter returned an empty response."
        }
        memory.add("user", prompt)
        memory.add("assistant", answer)
        answer
    }

    companion object { const val DEFAULT_MODEL = "openai/gpt-4o-mini" }
}
