package com.base.aihelperwearos.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.base.aihelperwearos.R
import com.base.aihelperwearos.data.repository.ChatRepository
import com.base.aihelperwearos.data.repository.ChatSession
import com.base.aihelperwearos.data.repository.ChatMessage
import com.base.aihelperwearos.data.models.Message
import com.base.aihelperwearos.data.network.OpenRouterService
import com.base.aihelperwearos.presentation.utils.AudioPlayer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    Home,
    ModelSelection,
    Chat,
    Analysis,
    History
}

data class ChatUiState(
    val currentScreen: Screen = Screen.Home,
    val selectedModel: String = "openrouter/polaris-alpha",
    val currentSessionId: Long? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAnalysisMode: Boolean = false,
    val chatSessions: List<ChatSession> = emptyList(),
    val fontSize: Int = 11,
    val pendingTranscription: String? = null,
    val pendingAudioPath: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository(application)

    private val openRouterService = OpenRouterService(
        apiKey = com.base.aihelperwearos.BuildConfig.OPENROUTER_API_KEY
    )

    private val audioPlayer = AudioPlayer(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(chatSessions = sessions) }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun startNewChat(isAnalysisMode: Boolean = false) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "startNewChat - START - isAnalysisMode: $isAnalysisMode")
                android.util.Log.d("MainViewModel", "Selected model: ${_uiState.value.selectedModel}")

                val title = if (isAnalysisMode) getApplication<Application>().getString(R.string.analysis_mode) else getApplication<Application>().getString(R.string.new_chat)
                android.util.Log.d("MainViewModel", "Creating session with title: $title")

                val sessionId = chatRepository.createSession(
                    modelId = _uiState.value.selectedModel,
                    title = title,
                    isAnalysisMode = isAnalysisMode
                )

                android.util.Log.d("MainViewModel", "Session created - ID: $sessionId")

                val newScreen = if (isAnalysisMode) Screen.Analysis else Screen.Chat
                android.util.Log.d("MainViewModel", "Navigating to: $newScreen")

                _uiState.update {
                    it.copy(
                        currentSessionId = sessionId,
                        currentScreen = newScreen,
                        isAnalysisMode = isAnalysisMode,
                        chatMessages = emptyList()
                    )
                }

                android.util.Log.d("MainViewModel", "State updated - currentScreen: ${_uiState.value.currentScreen}")

                observeMessages(sessionId)

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "ERROR in startNewChat: ${e.message}", e)
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Errore: ${e.message}") }
            }
        }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                val session = chatRepository.getSession(sessionId)
                if (session != null) {
                    _uiState.update {
                        it.copy(
                            currentSessionId = sessionId,
                            selectedModel = session.modelId,
                            isAnalysisMode = session.isAnalysisMode,
                            currentScreen = if (session.isAnalysisMode) Screen.Analysis else Screen.Chat
                        )
                    }
                    observeMessages(sessionId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore: ${e.message}") }
            }
        }
    }

    private fun observeMessages(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                _uiState.update { it.copy(chatMessages = messages) }
            }
        }
    }

    fun sendMessage(userMessage: String, audioPath: String? = null) {
        android.util.Log.d("MainViewModel", "sendMessage - CHIAMATO con messaggio: $userMessage")

        val sessionId = _uiState.value.currentSessionId
        if (sessionId == null) {
            android.util.Log.e("MainViewModel", "sendMessage - ERRORE: sessionId è null!")
            _uiState.update { it.copy(errorMessage = "Errore: sessione non trovata") }
            return
        }

        android.util.Log.d("MainViewModel", "sendMessage - sessionId: $sessionId, isAnalysisMode: ${_uiState.value.isAnalysisMode}")

        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "sendMessage - Inizio elaborazione")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                android.util.Log.d("MainViewModel", "sendMessage - Salvataggio messaggio utente")
                chatRepository.addMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = userMessage,
                    audioPath = audioPath
                )

                val session = chatRepository.getSession(sessionId)
                if (session != null && (session.title == getApplication<Application>().getString(R.string.new_chat) || session.title == getApplication<Application>().getString(R.string.analysis_mode))) {
                    val newTitle = userMessage.take(30) + if (userMessage.length > 30) "..." else ""
                    chatRepository.updateSessionTitle(sessionId, newTitle)
                }

                val messages = _uiState.value.chatMessages.map { msg ->
                    Message(role = msg.role, content = msg.content)
                } + Message(role = "user", content = userMessage)

                android.util.Log.d("MainViewModel", "sendMessage - Chiamata API in corso, modello: ${_uiState.value.selectedModel}")

                val result = openRouterService.sendMessage(
                    modelId = _uiState.value.selectedModel,
                    messages = messages,
                    isAnalysisMode = _uiState.value.isAnalysisMode
                )

                android.util.Log.d("MainViewModel", "sendMessage - API risposta ricevuta")

                result.fold(
                    onSuccess = { response ->
                        android.util.Log.d("MainViewModel", "sendMessage - SUCCESS: ${response.take(100)}")
                        chatRepository.addMessage(
                            sessionId = sessionId,
                            role = "assistant",
                            content = response
                        )
                        _uiState.update { it.copy(isLoading = false) }
                        android.util.Log.d("MainViewModel", "sendMessage - Messaggio AI salvato")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "sendMessage - ERRORE API: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Errore API: ${error.message}"
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendMessage - EXCEPTION: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${e.message}"
                    )
                }
            }
        }
    }

    fun sendAudioMessage(audioFile: File) {
        android.util.Log.d("MainViewModel", "sendAudioMessage - CHIAMATO: ${audioFile.absolutePath}")

        val sessionId = _uiState.value.currentSessionId
        if (sessionId == null) {
            android.util.Log.e("MainViewModel", "sendAudioMessage - ERRORE: sessionId è null!")
            _uiState.update { it.copy(errorMessage = "Errore: sessione non trovata") }
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "sendAudioMessage - Inizio elaborazione")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }


                android.util.Log.d("MainViewModel", "sendAudioMessage - Chiamata trascrizione audio")

                val audioPath = audioFile.absolutePath

                val transcriptionResult = openRouterService.transcribeAudioWithGemini(audioFile)

                transcriptionResult.fold(
                    onSuccess = { transcription ->
                        android.util.Log.d("MainViewModel", "Trascrizione ricevuta: $transcription")

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingTranscription = transcription,
                                pendingAudioPath = audioPath,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "sendAudioMessage - ERRORE API: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Errore audio: ${error.message}"
                            )
                        }
                    }
                )


            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendAudioMessage - EXCEPTION: ${e.message}", e)
                try { audioFile.delete() } catch (_: Exception) {}
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Errore: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            try {
                chatRepository.deleteSession(session.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore eliminazione: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun playAudio(audioPath: String) {
        try {
            val audioFile = File(audioPath)
            if (audioFile.exists()) {
                android.util.Log.d("MainViewModel", "Playing audio: $audioPath")
                audioPlayer.playAudio(audioFile) {
                    android.util.Log.d("MainViewModel", "Audio playback completed")
                }
            } else {
                android.util.Log.e("MainViewModel", "Audio file not found: $audioPath")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error playing audio", e)
        }
    }

    fun stopAudio() {
        audioPlayer.stopAudio()
    }

    fun confirmTranscription() {
        val transcription = _uiState.value.pendingTranscription ?: return
        val audioPath = _uiState.value.pendingAudioPath

        _uiState.update { it.copy(pendingTranscription = null, pendingAudioPath = null) }

        sendMessage(transcription, audioPath)
    }

    fun cancelTranscription() {
        _uiState.update {
            it.copy(
                pendingTranscription = null,
                pendingAudioPath = null,
                errorMessage = null
            )
        }
    }

    fun updateTranscription(newText: String) {
        _uiState.update { it.copy(pendingTranscription = newText) }
    }

    fun increaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize + 1).coerceAtMost(16)
            android.util.Log.d("MainViewModel", "Font size: ${state.fontSize} → $newSize")
            state.copy(fontSize = newSize)
        }
    }

    fun decreaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize - 1).coerceAtLeast(8)
            android.util.Log.d("MainViewModel", "Font size: ${state.fontSize} → $newSize")
            state.copy(fontSize = newSize)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        openRouterService.close()
    }
}