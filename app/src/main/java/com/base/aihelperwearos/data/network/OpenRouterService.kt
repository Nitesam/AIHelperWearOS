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
) {
    private val userPreferences = UserPreferences(context)
    private val trustAllCertificates = object : X509TrustManager {
        /**
         * Accepts any client certificate without validation.
         *
         * @param chain certificate chain presented by the client.
         * @param authType authentication type used.
         * @return `Unit` after accepting the certificate.
         */
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        /**
         * Accepts any server certificate without validation.
         *
         * @param chain certificate chain presented by the server.
         * @param authType authentication type used.
         * @return `Unit` after accepting the certificate.
         */
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        /**
         * Returns the list of accepted issuers.
         *
         * @return empty `Array<X509Certificate>`.
         */
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


        install(HttpTimeout) {
            requestTimeoutMillis = 180000
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

    /**
     * Sends chat messages to the OpenRouter API.
     *
     * @param modelId model identifier to use.
     * @param messages conversation messages to send.
     * @param isAnalysisMode whether analysis-mode prompting is enabled.
     * @param languageCode language code for prompts.
     * @param ragContext optional RAG context to enrich prompts.
     * @return `Result<String>` with the assistant response or error.
     */
    suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean = false,
        languageCode: String,
        ragContext: String? = null
    ): Result<String> {
        return try {
            Log.d("OpenRouter", "=== SEND MESSAGE START ===")
            Log.d("OpenRouter", "Language parameter received: '$languageCode'")
            Log.d("OpenRouter", "sendMessage - Using language: $languageCode, isAnalysisMode: $isAnalysisMode")
            Log.d("OpenRouter", "sendMessage - RAG context provided: ${ragContext != null}")

            val allMessages = if (isAnalysisMode) {
                val mathPrompt = com.base.aihelperwearos.data.Constants.getEnrichedMathPrompt(languageCode, ragContext)
                Log.d("OpenRouter", "sendMessage - Math prompt language: ${if (languageCode == "en") "ENGLISH" else "ITALIANO"}")
                Log.d("OpenRouter", "sendMessage - Prompt length: ${mathPrompt.length} chars (RAG: ${ragContext?.length ?: 0} chars)")
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

    /**
     * Transcribes audio and solves the math problem using the selected model.
     *
     * @param audioFile audio file to transcribe.
     * @param mathModel model identifier used for solving.
     * @param previousMessages previous chat context.
     * @param languageCode language code for prompts.
     * @return `Result<MathSolution>` with transcription and solution.
     */
    suspend fun solveAudioMathProblem(
        audioFile: File,
        mathModel: String = "openai/gpt-5.2",
        previousMessages: List<Message> = emptyList(),
        languageCode: String
    ): Result<com.base.aihelperwearos.data.models.MathSolution> {
        return try {
            Log.d("OpenRouter", "=== MATH PIPELINE START ===")
            Log.d("OpenRouter", "Audio: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")

            Log.d("OpenRouter", "STEP 1: Transcribing with Whisper...")
            val transcriptionResult = transcribeAudioWithGemini(audioFile, languageCode).getOrElse { error ->
                return Result.failure(Exception("Error during Transcription: ${error.message}"))
            }

            if (transcriptionResult.transcription.isBlank()) {
                return Result.failure(Exception("Empty transcription, retry."))
            }

            val transcription = transcriptionResult.displayText
            Log.d("OpenRouter", "Transcription: $transcription")
            Log.d("OpenRouter", "Keywords: ${transcriptionResult.keywords.joinToString(", ")}")

            Log.d("OpenRouter", "STEP 2: Solving with $mathModel...")
            val solution = solveMathProblem(transcription, mathModel, previousMessages, languageCode).getOrElse { error ->
                return Result.failure(Exception("Error: ${error.message}"))
            }

            Log.d("OpenRouter", "Solution: ${solution.take(100)}...")

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

    /**
     * Transcribes audio using the Gemini model with keyword extraction.
     *
     * @param audioFile audio file to transcribe.
     * @param languageCode language code for transcription prompt.
     * @return `Result<TranscriptionResult>` with parsed transcript data.
     */
    suspend fun transcribeAudioWithGemini(audioFile: File, languageCode: String): Result<com.base.aihelperwearos.data.models.TranscriptionResult> {
        return try {
            Log.d("OpenRouter", "=== TRANSCRIPTION START ===")
            Log.d("OpenRouter", "Transcribing with Gemini 3.0 Flash (audio support)")
            Log.d("OpenRouter", "Language parameter received: '$languageCode'")
            Log.d("OpenRouter", "Transcription language: ${if (languageCode == "en") "ENGLISH" else "ITALIANO"} (code: $languageCode)")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(Exception("File audio missing."))
            }

            val audioBytes = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d("OpenRouter", "Audio size: ${audioBytes.size} bytes → Base64: ${audioBase64.length} chars")

            val transcriptionPrompt = com.base.aihelperwearos.data.Constants.getTranscriptionPrompt(languageCode)
            Log.d("OpenRouter", "📋 Transcription prompt length: ${transcriptionPrompt.length} chars")
            Log.d("OpenRouter", "📋 Prompt includes keyword extraction: ${transcriptionPrompt.contains("KEYWORDS")}")

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

            Log.d("OpenRouter", "🚀 Sending to Gemini 3.0 Flash for transcription...")

            val response: String = client.post("chat/completions") {
                setBody(requestBody)
                header("Content-Type", "application/json")
            }.body()

            Log.d("OpenRouter", "📥 Transcription raw response received (${response.length} chars)")
            Log.d("OpenRouter", "📥 Response preview: ${response.take(300)}")

            val jsonResponse = Json.parseToJsonElement(response) as kotlinx.serialization.json.JsonObject

            val error = jsonResponse["error"] as? kotlinx.serialization.json.JsonObject
            if (error != null) {
                val errorMsg = (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: "Errore sconosciuto"
                Log.e("OpenRouter", "❌ API Error: $errorMsg")
                return Result.failure(Exception("Errore API: $errorMsg"))
            }

            val choices = jsonResponse["choices"] as? kotlinx.serialization.json.JsonArray
            val choice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
            val message = choice?.get("message") as? kotlinx.serialization.json.JsonObject
            val rawText = (message?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content

            if (rawText.isNullOrBlank()) {
                Log.e("OpenRouter", "❌ Empty transcription from Gemini")
                return Result.failure(Exception("Trascrizione vuota - riprova parlando più chiaramente"))
            }

            Log.d("OpenRouter", "📝 Raw transcription text received: $rawText")
            Log.d("OpenRouter", "🔍 Starting keyword extraction...")
            
            val result = parseTranscriptionWithKeywords(rawText, languageCode)
            
            Log.d("OpenRouter", "✅ Transcription SUCCESS")
            Log.d("OpenRouter", "📌 Keywords extracted: ${result.keywords.joinToString(", ")}")
            Log.d("OpenRouter", "📌 Theory query: ${result.isTheoryQuery}")
            Log.d("OpenRouter", "📌 Display text: ${result.displayText}")
            Log.d("OpenRouter", "=== TRANSCRIPTION END ===")
            
            Result.success(result)

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

    /**
     * Sends a math problem to the model and returns the solution text.
     *
     * @param problem problem statement text.
     * @param model model identifier to use.
     * @param previousMessages prior chat context.
     * @param languageCode language code for prompts.
     * @return `Result<String>` with the solution or error.
     */
    private suspend fun solveMathProblem(
        problem: String,
        model: String,
        previousMessages: List<Message>,
        languageCode: String
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

    /**
     * Sends an audio message for transcription or full analysis pipeline.
     *
     * @param audioFile audio file to process.
     * @param modelId model identifier used for analysis mode.
     * @param previousMessages prior chat context.
     * @param isAnalysisMode whether to solve the math problem.
     * @param languageCode language code for prompts.
     * @return `Result<TranscriptionResult>` with transcription output.
     */
    suspend fun sendAudioMessage(
        audioFile: File,
        modelId: String,
        previousMessages: List<Message>,
        isAnalysisMode: Boolean = false,
        languageCode: String
    ): Result<com.base.aihelperwearos.data.models.TranscriptionResult> {
        return if (isAnalysisMode) {
            solveAudioMathProblem(audioFile, modelId, previousMessages, languageCode).map { 
                com.base.aihelperwearos.data.models.TranscriptionResult(
                    transcription = it.solution,
                    keywords = emptyList(),
                    displayText = it.solution
                )
            }
        } else {
            transcribeAudioWithGemini(audioFile, languageCode)
        }
    }

    /**
     * Parses a transcription response and extracts keywords.
     *
     * @param rawText raw response content from the model.
     * @param languageCode language code to guide fallback extraction.
     * @return `TranscriptionResult` with transcription and keywords.
     */
    private fun parseTranscriptionWithKeywords(rawText: String, languageCode: String): com.base.aihelperwearos.data.models.TranscriptionResult {
        Log.d("OpenRouter", "🔍 parseTranscriptionWithKeywords - START")
        Log.d("OpenRouter", "🔍 Input text length: ${rawText.length} chars")
        Log.d("OpenRouter", "🔍 Input text: $rawText")
        
        val keywordPattern = Regex("""\[KEYWORDS?:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        val transcriptionPattern = Regex("""\[(TRASCRIZIONE|TRANSCRIPTION):\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        
        val keywordMatch = keywordPattern.find(rawText)
        val transcriptionMatch = transcriptionPattern.find(rawText)
        
        Log.d("OpenRouter", "🔍 Keyword match found: ${keywordMatch != null}")
        Log.d("OpenRouter", "🔍 Transcription match found: ${transcriptionMatch != null}")
        
        val keywords = if (keywordMatch != null) {
            val keywordString = keywordMatch.groupValues[1].trim()
            Log.d("OpenRouter", "🔍 Raw keyword string: '$keywordString'")
            val parsedKeywords = keywordString.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            Log.d("OpenRouter", "🔍 Parsed ${parsedKeywords.size} keywords: ${parsedKeywords.joinToString(", ")}")
            parsedKeywords
        } else {
            Log.w("OpenRouter", "⚠️ No keywords found in response - using fallback")
            extractFallbackKeywords(rawText, languageCode)
        }
        
        val transcription = if (transcriptionMatch != null) {
            val extracted = transcriptionMatch.groupValues[2].trim()
            Log.d("OpenRouter", "🔍 Extracted transcription: '$extracted'")
            extracted
        } else {
            Log.w("OpenRouter", "⚠️ No transcription marker found - using cleaned raw text")
            rawText.replace(keywordPattern, "").trim()
        }
        
        val isTheory = keywords.firstOrNull() in listOf("teoria", "theory", "teorema", "theorem", "definizione", "definition")
        Log.d("OpenRouter", "🔍 Is theory query: $isTheory (first keyword: ${keywords.firstOrNull()})")
        
        val result = com.base.aihelperwearos.data.models.TranscriptionResult(
            transcription = transcription,
            keywords = keywords,
            displayText = transcription,
            isTheoryQuery = isTheory
        )
        
        Log.d("OpenRouter", "🔍 parseTranscriptionWithKeywords - END")
        return result
    }
    
    /**
     * Extracts fallback keywords when the model response lacks markers.
     *
     * @param text raw transcription text.
     * @param languageCode language code for keyword labeling.
     * @return `List<String>` of extracted keywords.
     */
    private fun extractFallbackKeywords(text: String, languageCode: String): List<String> {
        Log.d("OpenRouter", "🔍 extractFallbackKeywords - attempting pattern detection")
        val lowerText = text.lowercase()
        val keywords = mutableListOf<String>()
        
        val theoryIndicators = listOf("enuncia", "definizione", "cos'è", "cosa è", "teorema", "lemma", "corollario", "define", "state", "what is")
        val exerciseIndicators = listOf("calcola", "risolvi", "trova", "determina", "compute", "calculate", "solve", "find")
        
        val hasTheory = theoryIndicators.any { lowerText.contains(it) }
        val hasExercise = exerciseIndicators.any { lowerText.contains(it) }
        
        if (hasTheory) {
            keywords.add(if (languageCode == "en") "theory" else "teoria")
        } else if (hasExercise) {
            keywords.add(if (languageCode == "en") "exercise" else "esercizio")
        }
        
        val topics = mapOf(
            "integrale" to "integrale",
            "integral" to "integral",
            "derivata" to "derivata",
            "derivative" to "derivative",
            "limite" to "limite",
            "limit" to "limit",
            "gauss" to "gauss",
            "stokes" to "stokes",
            "green" to "green",
            "equazione" to "equazione",
            "equation" to "equation"
        )
        
        topics.forEach { (pattern, keyword) ->
            if (lowerText.contains(pattern)) {
                keywords.add(keyword)
            }
        }
        
        Log.d("OpenRouter", "🔍 Fallback extracted ${keywords.size} keywords: ${keywords.joinToString(", ")}")
        return keywords.take(5)
    }

    /**
     * Closes the underlying HTTP client.
     *
     * @return `Unit` after the client is closed.
     */
    fun close() {
        client.close()
    }
}
