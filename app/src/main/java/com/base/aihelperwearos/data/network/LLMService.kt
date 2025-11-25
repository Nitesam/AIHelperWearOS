package com.base.aihelperwearos.data.network

import com.base.aihelperwearos.data.models.Message
import java.io.File

interface LLMService {
    suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean,
        languageCode: String
    ): Result<String>

    suspend fun transcribeAudio(
        audioFile: File,
        languageCode: String
    ): Result<String>
    
    fun close()
}