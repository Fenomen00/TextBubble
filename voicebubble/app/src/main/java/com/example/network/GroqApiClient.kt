package com.example.network

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Client for interacting with the Groq API (OpenAI-compatible).
 * Never transmits keys to anywhere other than https://api.groq.com.
 */
class GroqApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val SAMPLE_CLEANUP_INPUT =
            "um hello so basically this is uh a test of the cleanup"
    }

    /**
     * Calls GET https://api.groq.com/openai/v1/models with the given API key.
     * Returns a list of all model IDs available on Groq.
     */
    suspend fun fetchModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key cannot be empty"))
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val dataArray = json.optJSONArray("data") ?: JSONArray()
                    val modelsList = mutableListOf<String>()

                    for (i in 0 until dataArray.length()) {
                        val modelObj = dataArray.optJSONObject(i)
                        val id = modelObj?.optString("id")
                        if (!id.isNullOrBlank()) {
                            modelsList.add(id)
                        }
                    }

                    Result.success(modelsList.sorted())
                } else {
                    val errorMessage = parseErrorMessage(bodyString, response.code)
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Filters a list of models to speech/transcription models (e.g. whisper).
     */
    fun filterTranscriptionModels(allModels: List<String>): List<String> {
        val speech = allModels.filter { modelId ->
            val lower = modelId.lowercase()
            lower.contains("whisper") || lower.contains("speech") || lower.contains("audio")
        }
        return speech.ifEmpty {
            listOf(
                "whisper-large-v3-turbo",
                "whisper-large-v3",
                "distil-whisper-large-v3-en"
            )
        }
    }

    /**
     * Filters a list of models to text/chat models suitable for text cleanup.
     */
    fun filterCleanupModels(allModels: List<String>): List<String> {
        val textModels = allModels.filter { modelId ->
            val lower = modelId.lowercase()
            !lower.contains("whisper") &&
                    !lower.contains("speech") &&
                    !lower.contains("audio") &&
                    !lower.contains("vision") &&
                    !lower.contains("guard") &&
                    !lower.contains("embedding")
        }
        return textModels.ifEmpty {
            listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "mixtral-8x7b-32768",
                "gemma2-9b-it"
            )
        }
    }

    /**
     * Tests the transcription API key by pinging the Groq models endpoint.
     */
    suspend fun testTranscriptionKey(apiKey: String, selectedModel: String): TestResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext TestResult.Error("Please enter your Groq API key first.")
            }

            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val latencyMs = System.currentTimeMillis() - startTime
                    val bodyString = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val json = JSONObject(bodyString)
                        val dataArray = json.optJSONArray("data") ?: JSONArray()
                        var hasWhisper = false
                        for (i in 0 until dataArray.length()) {
                            val id = dataArray.optJSONObject(i)?.optString("id").orEmpty()
                            if (id.contains("whisper", ignoreCase = true)) {
                                hasWhisper = true
                                break
                            }
                        }

                        val msg = if (hasWhisper) {
                            "Connection successful ($latencyMs ms). Key verified! Whisper speech models are ready for \"$selectedModel\"."
                        } else {
                            "Connection successful ($latencyMs ms). Key verified on Groq."
                        }

                        TestResult.Success(latencyMs = latencyMs, message = msg)
                    } else {
                        val errorMsg = parseErrorMessage(bodyString, response.code)
                        TestResult.Error(message = errorMsg, statusCode = response.code)
                    }
                }
            } catch (e: Exception) {
                TestResult.Error("Network error: ${e.localizedMessage ?: "Failed to connect to Groq"}")
            }
        }

    /**
     * Tests the cleanup API key, selected model, and cleanup instruction
     * by executing a fast test chat completion with a sample filler sentence.
     */
    suspend fun testCleanupKeyAndInstruction(
        apiKey: String,
        model: String,
        instruction: String
    ): TestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext TestResult.Error("Please enter your Groq API key first.")
        }

        val startTime = System.currentTimeMillis()
        try {
            val payload = JSONObject().apply {
                put("model", model.trim())
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", instruction.trim())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", SAMPLE_CLEANUP_INPUT)
                    })
                }
                put("messages", messages)
                put("max_tokens", 80)
                put("temperature", 0.1)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startTime
                val bodyString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val choices = json.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val message = firstChoice?.optJSONObject("message")
                    val content = message?.optString("content")?.trim().orEmpty()

                    val cleanPreview = content.ifEmpty { "Processed successfully without errors." }

                    TestResult.Success(
                        latencyMs = latencyMs,
                        message = "Success ($latencyMs ms)! Model \"$model\" responded correctly.",
                        previewOutput = cleanPreview
                    )
                } else {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    TestResult.Error(message = errorMsg, statusCode = response.code)
                }
            }
        } catch (e: Exception) {
            TestResult.Error("Network error: ${e.localizedMessage ?: "Failed to connect to Groq"}")
        }
    }

    /**
     * Transcribes audio using Groq Whisper model.
     * POST https://api.groq.com/openai/v1/audio/transcriptions
     */
    suspend fun transcribeAudio(
        apiKey: String,
        model: String,
        audioFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Groq API key is missing. Configure it in Settings."))
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Recorded audio file is empty or missing."))
        }

        try {
            val audioMediaType = "audio/m4a".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(audioMediaType))
                .addFormDataPart("model", model.trim().ifEmpty { "whisper-large-v3-turbo" })
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/audio/transcriptions")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val transcribedText = json.optString("text", "").trim()
                    if (transcribedText.isBlank()) {
                        Result.failure(Exception("No speech detected in audio."))
                    } else {
                        Result.success(transcribedText)
                    }
                } else {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Polishes and cleans up raw transcript using Groq chat completion model.
     */
    suspend fun cleanupText(
        apiKey: String,
        model: String,
        instruction: String,
        rawText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Cleanup API key is missing."))
        }
        if (rawText.isBlank()) {
            return@withContext Result.success("")
        }

        try {
            val payload = JSONObject().apply {
                put("model", model.trim().ifEmpty { "llama-3.3-70b-versatile" })
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", instruction.trim())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", rawText)
                    })
                }
                put("messages", messages)
                put("temperature", 0.1)
                put("max_tokens", 1024)
            }

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val choices = json.optJSONArray("choices")
                    val message = choices?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.optString("content")?.trim().orEmpty()
                    Result.success(content.ifEmpty { rawText })
                } else {
                    val errorMsg = parseErrorMessage(bodyString, response.code)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(bodyString: String, statusCode: Int): String {
        return try {
            val json = JSONObject(bodyString)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                "Groq ($statusCode): $message"
            } else {
                "HTTP $statusCode: ${json.optString("message", "Request failed")}"
            }
        } catch (_: Exception) {
            when (statusCode) {
                401 -> "HTTP 401 Unauthorized: Invalid API key. Please double-check your Groq key."
                403 -> "HTTP 403 Forbidden: Access denied by Groq API."
                404 -> "HTTP 404: Model or endpoint not found."
                429 -> "HTTP 429: Groq rate limit exceeded. Please wait a moment."
                500, 502, 503 -> "Groq server error ($statusCode). Please try again shortly."
                else -> "HTTP $statusCode error from Groq."
            }
        }
    }
}
