package com.base.aihelperwearos.data.network

import android.content.Context
import android.util.Log
import com.base.aihelperwearos.data.models.Message
import com.base.aihelperwearos.data.models.OpenRouterRequest
import com.base.aihelperwearos.data.models.OpenRouterResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import android.util.Base64
import java.io.File
import io.ktor.client.engine.android.Android
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import com.base.aihelperwearos.data.preferences.UserPreferences

class OpenRouterService(
    private val apiKey: String,
    context: Context
) : LLMService {
    private val userPreferences = UserPreferences(context)
    private val trustAllCertificates = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val client = HttpClient(Android) {
        engine {
            sslManager = { httpsURLConnection ->
                httpsURLConnection.setSSLSocketFactory(
                    javax.net.ssl.SSLContext.getInstance("TLS").apply {
                        init(null, arrayOf(trustAllCertificates), java.security.SecureRandom())
                    }.socketFactory
                )
                httpsURLConnection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("OpenRouter", message)
                }
            }
            level = LogLevel.BODY
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }

        defaultRequest {
            url("https://openrouter.ai/api/v1/")
            contentType(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $apiKey")
                append("HTTP-Referer", "com.base.aihelperwearos")
                append("X-Title", "AI Helper Wear OS")
            }
        }
    }

    override suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean,
        languageCode: String
    ): Result<String> {
        return try {
            Log.d("OpenRouter", "=== SEND MESSAGE START ===")
            Log.d("OpenRouter", "Language parameter received: '$languageCode'")
            Log.d("OpenRouter", "sendMessage - Using language: $languageCode, isAnalysisMode: $isAnalysisMode")

            val allMessages = if (isAnalysisMode) {
                val mathPrompt = com.base.aihelperwearos.data.Constants.getMathPrompt(languageCode)
                Log.d("OpenRouter", "sendMessage - Math prompt language: ${if (languageCode == "en") "ENGLISH" else "ITALIANO"}")
                listOf(
                    Message(
                        role = "system",
                        content = mathPrompt
                    )
                ) + messages
            } else {
                messages
            }

            val request = OpenRouterRequest(
                model = modelId,
                messages = allMessages,
                maxTokens = if (isAnalysisMode) 4096 else 2048,
                temperature = if (isAnalysisMode) 0.2 else 0.7
            )

            val response: OpenRouterResponse = client.post("chat/completions") {
                setBody(request)
            }.body()

            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Nessuna risposta dall'AI"))

            Result.success(content)

        } catch (e: Exception) {
            Log.e("OpenRouter", "Errore chiamata API", e)
            Result.failure(e)
        }
    }

    override suspend fun transcribeAudio(
        audioFile: File,
        languageCode: String
    ): Result<String> {
        return transcribeAudioWithGemini(audioFile, languageCode)
    }

    suspend fun transcribeAudioWithGemini(audioFile: File, languageCode: String): Result<String> {
        return try {
            Log.d("OpenRouter", "=== TRANSCRIPTION START ===")
            Log.d("OpenRouter", "Transcribing with Gemini 2.5 Flash (audio support)")
            Log.d("OpenRouter", "Language parameter received: '$languageCode'")
            Log.d("OpenRouter", "Transcription language: ${if (languageCode == "en") "ENGLISH" else "ITALIANO"} (code: $languageCode)")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(Exception("File audio missing."))
            }

            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d("OpenRouter", "Audio size: ${audioBytes.size} bytes → Base64: ${audioBase64.length} chars")

            val transcriptionPrompt = com.base.aihelperwearos.data.Constants.getTranscriptionPrompt(languageCode)

            val modelName = "google/gemini-2.5-flash-preview-09-2025"

            val requestBody = buildString {
                append("{")
                append("\"model\":\"$modelName\",")
                append("\"messages\":[{")
                append("\"role\":\"user\",")
                append("\"content\":[")

                append("{")
                append("\"type\":\"text\",")
                append("\"text\":\"${transcriptionPrompt.replace("\"", "\\\"")}\"")
                append("},")

                append("{")
                append("\"type\":\"input_audio\",")
                append("\"input_audio\":{")
                append("\"data\":\"$audioBase64\",")
                append("\"format\":\"wav\"")
                append("}")
                append("}")

                append("]")
                append("}]")
                append("}")
            }

            Log.d("OpenRouter", "Sending to $modelName for transcription...")

            val response: String = client.post("chat/completions") {
                setBody(requestBody)
                header("Content-Type", "application/json")
            }.body()

            Log.d("OpenRouter", "Transcription response: ${response.take(200)}")

            val jsonResponse = Json.parseToJsonElement(response) as kotlinx.serialization.json.JsonObject

            val error = jsonResponse["error"] as? kotlinx.serialization.json.JsonObject
            if (error != null) {
                val errorMsg = (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: "Errore sconosciuto"
                Log.e("OpenRouter", "API Error: $errorMsg")
                return Result.failure(Exception("Errore API: $errorMsg"))
            }

            val choices = jsonResponse["choices"] as? kotlinx.serialization.json.JsonArray
            val choice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
            val message = choice?.get("message") as? kotlinx.serialization.json.JsonObject
            val text = (message?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content

            if (text.isNullOrBlank()) {
                Log.e("OpenRouter", "Empty transcription from Gemini")
                return Result.failure(Exception("Trascrizione vuota - riprova parlando più chiaramente"))
            }

            Log.d("OpenRouter", "✓ Transcription SUCCESS: $text")
            Result.success(text.trim())

        } catch (e: Exception) {
            Log.e("OpenRouter", "Audio transcription failed", e)
            Result.failure(e)
        } finally {
            try {
                audioFile.delete()
                Log.d("OpenRouter", "Audio file deleted")
            } catch (e: Exception) {
                Log.w("OpenRouter", "Failed to delete audio file", e)
            }
        }
    }

    override fun close() {
        client.close()
    }
}
