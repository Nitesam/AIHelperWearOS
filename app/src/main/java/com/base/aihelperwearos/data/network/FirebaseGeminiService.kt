package com.base.aihelperwearos.data.network

import com.base.aihelperwearos.data.models.Message
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.File
import android.util.Log

class FirebaseGeminiService : LLMService {
    private fun getModel(modelName: String, systemInstruction: String? = null) =
        Firebase.ai().generativeModel(
            modelName = modelName,
            generationConfig = config,
            systemInstruction = if (systemInstruction != null) content { text(systemInstruction) } else null
        )
    
    private val config = generationConfig {
        temperature = 0.7f
        topK = 32
        topP = 1f
        maxOutputTokens = 4096
    }

    override suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean,
        languageCode: String
    ): Result<String> {
        return try {
            val modelName = "gemini-3-pro-preview"
            
            Log.d("FirebaseGemini", "Using model: $modelName")

            val mathPrompt = com.base.aihelperwearos.data.Constants.getMathPrompt(languageCode)
            val systemInstruction = if (isAnalysisMode) mathPrompt else null
            
            val generativeModel = getModel(modelName, systemInstruction)

            val history = messages.dropLast(1).map { msg ->
                content(role = if (msg.role == "user") "user" else "model") {
                    text(msg.content)
                }
            }
            
            val lastMessage = messages.lastOrNull()?.content ?: return Result.failure(Exception("Empty message"))

            val chat = generativeModel.startChat(history)
            val response = chat.sendMessage(lastMessage)
            
            val text = response.text ?: return Result.failure(Exception("No response text"))
            Result.success(text)

        } catch (e: Exception) {
            Log.e("FirebaseGemini", "Error sending message", e)
            Result.failure(e)
        }
    }

    override suspend fun transcribeAudio(
        audioFile: File,
        languageCode: String
    ): Result<String> {
        return try {
             val model = getModel("gemini-2.5-flash")
            
            val transcriptionPrompt = com.base.aihelperwearos.data.Constants.getTranscriptionPrompt(languageCode)
            
            if (!audioFile.exists()) return Result.failure(Exception("File not found"))
            val audioBytes = audioFile.readBytes()

            val inputContent = content {
                text(transcriptionPrompt)
                inlineData(mimeType = "audio/wav", bytes = audioBytes)
            }

            val response = model.generateContent(inputContent)
            val text = response.text
            
            if (text.isNullOrBlank()) {
                 Result.failure(Exception("Empty transcription"))
            } else {
                 Result.success(text)
            }
        } catch (e: Exception) {
             Log.e("FirebaseGemini", "Error transcribing", e)
             Result.failure(e)
        }
    }

    override fun close() {
        // Firebase SDK doesn't need explicit close
    }
}