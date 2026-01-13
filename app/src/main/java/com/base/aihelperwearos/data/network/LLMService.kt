package com.base.aihelperwearos.data.network

import com.base.aihelperwearos.data.models.Message
import java.io.File

interface LLMService {
    /**
     * Sends a chat message batch to the LLM backend.
     *
     * @param modelId model identifier to route the request.
     * @param messages ordered conversation messages to send.
     * @param isAnalysisMode whether analysis-mode prompting is enabled.
     * @param languageCode locale code to select prompts and formatting.
     * @return `Result<String>` containing the assistant reply or an error.
     */
    suspend fun sendMessage(
        modelId: String,
        messages: List<Message>,
        isAnalysisMode: Boolean,
        languageCode: String
    ): Result<String>

    /**
     * Transcribes an audio file using the configured speech model.
     *
     * @param audioFile audio file to transcribe.
     * @param languageCode locale code to guide transcription language.
     * @return `Result<String>` containing the transcript or an error.
     */
    suspend fun transcribeAudio(
        audioFile: File,
        languageCode: String
    ): Result<String>
    
    /**
     * Releases any network or client resources held by the service.
     *
     * @return `Unit` when cleanup completes.
     */
    fun close()
}
