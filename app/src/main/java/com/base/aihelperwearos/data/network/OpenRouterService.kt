package com.base.aihelperwearos.data.network

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

class OpenRouterService(private val apiKey: String) {

    private val client = HttpClient(Android) {
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
        isAnalysisMode: Boolean = false
    ): Result<String> {
        return try {
            val allMessages = if (isAnalysisMode) {
                listOf(
                    Message(
                        role = "system",
                        content = com.base.aihelperwearos.data.Constants.MATH_MODE_PROMPT
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
        previousMessages: List<Message> = emptyList()
    ): Result<com.base.aihelperwearos.data.models.MathSolution> {
        return try {
            Log.d("OpenRouter", "=== MATH PIPELINE START ===")
            Log.d("OpenRouter", "Audio: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")

            Log.d("OpenRouter", "STEP 1: Transcribing with Whisper...")
            val transcription = transcribeAudioWithGemini(audioFile).getOrElse { error ->
                return Result.failure(Exception("Errore trascrizione: ${error.message}"))
            }

            if (transcription.isBlank()) {
                return Result.failure(Exception("Trascrizione vuota. Riprova a dettare più chiaramente."))
            }

            Log.d("OpenRouter", "Trascrizione: $transcription")

            Log.d("OpenRouter", "STEP 2: Solving with $mathModel...")
            val solution = solveMathProblem(transcription, mathModel, previousMessages).getOrElse { error ->
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

    suspend fun transcribeAudioWithGemini(audioFile: File): Result<String> {
        return try {
            Log.d("OpenRouter", "Transcribing with Gemini 2.5 Flash (audio support)")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(Exception("File audio vuoto o mancante"))
            }

            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d("OpenRouter", "Audio size: ${audioBytes.size} bytes → Base64: ${audioBase64.length} chars")
            Log.d("OpenRouter", "Sending to Gemini 2.5 Flash for transcription...")

            val contentArray = kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                    put("text", kotlinx.serialization.json.JsonPrimitive(
                        "Ascolta attentamente questo file audio e trascrivi ESATTAMENTE ciò che viene detto. " +
                        "L'audio contiene un problema di matematica in italiano. " +
                        "Scrivi SOLO la trascrizione letterale delle parole pronunciate, senza aggiungere commenti, spiegazioni o interpretazioni. " +
                        "Esempio: se sento 'calcola x al quadrato' scrivi esattamente 'calcola x al quadrato'."
                    ))
                })
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("input_audio"))
                    put("input_audio", kotlinx.serialization.json.buildJsonObject {
                        put("data", kotlinx.serialization.json.JsonPrimitive(audioBase64))
                        put("format", kotlinx.serialization.json.JsonPrimitive("wav"))
                    })
                })
            }

            val request = com.base.aihelperwearos.data.models.MultimodalRequest(
                model = "google/gemini-2.5-flash",
                messages = listOf(
                    com.base.aihelperwearos.data.models.MultimodalMessage(
                        role = "user",
                        content = contentArray
                    )
                ),
                maxTokens = 1000,
                temperature = 0.1
            )

            val response: OpenRouterResponse = client.post("chat/completions") {
                setBody(request)
            }.body()

            Log.d("OpenRouter", "Transcription response received")

            val text = response.choices.firstOrNull()?.message?.content

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
        previousMessages: List<Message>
    ): Result<String> {
        return try {
            Log.d("OpenRouter", "POST /chat/completions with model: $model")

            val systemPrompt = Message(
                role = "system",
                content = com.base.aihelperwearos.data.Constants.MATH_MODE_PROMPT
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

