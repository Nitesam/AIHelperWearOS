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
import com.base.aihelperwearos.utils.getCurrentLanguageCode

class OpenRouterService(
    private val apiKey: String,
    private val context: Context
) {
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

    suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean = false,
        languageCode: String = context.getCurrentLanguageCode()
    ): Result<String> {
        return try {
            val allMessages = if (isAnalysisMode) {
                listOf(
                    Message(
                        role = "system",
                        content = com.base.aihelperwearos.data.Constants.getMathPrompt(languageCode)
                    )
                ) + messages
            } else {
                messages
            }

            val request = OpenRouterRequest(
                model = modelId,
                messages = allMessages,
                maxTokens = if (isAnalysisMode) 3000 else 1000,
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

    suspend fun solveAudioMathProblem(
        audioFile: File,
        mathModel: String = "anthropic/claude-3.5-sonnet",
        previousMessages: List<Message> = emptyList(),
        languageCode: String = context.getCurrentLanguageCode()
    ): Result<com.base.aihelperwearos.data.models.MathSolution> {
        return try {
            Log.d("OpenRouter", "=== MATH PIPELINE START ===")
            Log.d("OpenRouter", "Audio: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")

            Log.d("OpenRouter", "STEP 1: Transcribing with Whisper...")
            val transcription = transcribeAudioWithGemini(audioFile, languageCode).getOrElse { error ->
                return Result.failure(Exception("Errore trascrizione: ${error.message}"))
            }

            if (transcription.isBlank()) {
                return Result.failure(Exception("Trascrizione vuota. Riprova a dettare più chiaramente."))
            }

            Log.d("OpenRouter", "Trascrizione: $transcription")

            Log.d("OpenRouter", "STEP 2: Solving with $mathModel...")
            val solution = solveMathProblem(transcription, mathModel, previousMessages, languageCode).getOrElse { error ->
                return Result.failure(Exception("Errore risoluzione: ${error.message}"))
            }

            Log.d("OpenRouter", "Soluzione ricevuta: ${solution.take(100)}...")

            val result = com.base.aihelperwearos.data.models.MathSolution(
                transcription = transcription,
                solution = solution,
                model = mathModel
            )

            Log.d("OpenRouter", "=== MATH PIPELINE SUCCESS ===")
            Result.success(result)

        } catch (e: Exception) {
            Log.e("OpenRouter", "=== MATH PIPELINE FAILED ===", e)
            Result.failure(e)
        }
    }

    suspend fun transcribeAudioWithGemini(audioFile: File, languageCode: String = context.getCurrentLanguageCode()): Result<String> {
        return try {
            Log.d("OpenRouter", "Transcribing with Gemini 2.5 Flash (audio support)")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(Exception("File audio vuoto o mancante"))
            }

            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d("OpenRouter", "Audio size: ${audioBytes.size} bytes → Base64: ${audioBase64.length} chars")

            val transcriptionPrompt = com.base.aihelperwearos.data.Constants.getTranscriptionPrompt(languageCode)

            val requestBody = buildString {
                append("{")
                append("\"model\":\"google/gemini-2.5-flash\",")
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

            Log.d("OpenRouter", "Sending to Gemini 2.5 Flash for transcription...")

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
            Result.failure(Exception("Errore trascrizione: ${e.message}"))
        } finally {
            try {
                audioFile.delete()
                Log.d("OpenRouter", "Audio file deleted")
            } catch (e: Exception) {
                Log.w("OpenRouter", "Failed to delete audio file", e)
            }
        }
    }

    private suspend fun solveMathProblem(
        problem: String,
        model: String,
        previousMessages: List<Message>,
        languageCode: String = context.getCurrentLanguageCode()
    ): Result<String> {
        return try {
            Log.d("OpenRouter", "POST /chat/completions with model: $model")
            Log.d("OpenRouter", "Using language: $languageCode")

            val systemPrompt = Message(
                role = "system",
                content = com.base.aihelperwearos.data.Constants.getMathPrompt(languageCode)
            )

            val allMessages = listOf(systemPrompt) + previousMessages + Message(
                role = "user",
                content = "Problema: $problem"
            )

            val request = OpenRouterRequest(
                model = model,
                messages = allMessages,
                temperature = 0.3,
                maxTokens = 4000
            )

            val response: OpenRouterResponse = client.post("chat/completions") {
                setBody(request)
            }.body()

            val solution = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Nessuna soluzione dall'AI"))

            Result.success(solution)

        } catch (e: Exception) {
            Log.e("OpenRouter", "Math solving failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendAudioMessage(
        audioFile: File,
        modelId: String,
        previousMessages: List<Message>,
        isAnalysisMode: Boolean = false
    ): Result<String> {
        return if (isAnalysisMode) {
            solveAudioMathProblem(audioFile, modelId, previousMessages).map { it.solution }
        } else {
            transcribeAudioWithGemini(audioFile)
        }
    }

    fun close() {
        client.close()
    }
}

