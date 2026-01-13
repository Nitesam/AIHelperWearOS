package com.base.aihelperwearos.presentation.viewmodel
import com.base.aihelperwearos.BuildConfig

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.base.aihelperwearos.R
import com.base.aihelperwearos.data.repository.ChatRepository
import com.base.aihelperwearos.data.repository.ChatSession
import com.base.aihelperwearos.data.repository.ChatMessage
import com.base.aihelperwearos.data.models.Message
import com.base.aihelperwearos.data.network.OpenRouterService
import com.base.aihelperwearos.presentation.utils.AudioPlayer
import com.base.aihelperwearos.presentation.utils.TextToSpeechHelper
import com.base.aihelperwearos.presentation.services.AudioRecordingService
import com.base.aihelperwearos.utils.getCurrentLanguageCode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File

enum class Screen {
    Home,
    ModelSelection,
    Chat,
    Analysis,
    History
}

enum class Language(val code: String, val displayName: String) {
    ITALIAN("it", "Italiano"),
    ENGLISH("en", "English")
}


data class ChatUiState(
    val currentScreen: Screen = Screen.Home,
    val selectedModel: String = "anthropic/claude-sonnet-4.5",
    val currentSessionId: Long? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAnalysisMode: Boolean = false,
    val chatSessions: List<ChatSession> = emptyList(),
    val fontSize: Int = 11,
    val pendingTranscription: String? = null,
    val pendingAudioPath: String? = null,
    val selectedLanguage: Language = Language.ITALIAN,
    val recordedAudioFile: File? = null,
    val isRecording: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository(application)

    private val openRouterService = OpenRouterService(
        apiKey = BuildConfig.OPENROUTER_API_KEY,
        context = application
    )

    private val ttsHelper = TextToSpeechHelper(application)
    private val audioPlayer = AudioPlayer(application)
    private val userPreferences = com.base.aihelperwearos.data.preferences.UserPreferences(application)

    private var recordingService: AudioRecordingService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AudioRecordingService.LocalBinder
            recordingService = localBinder.getService()
            serviceBound = true
            android.util.Log.d("MainViewModel", "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            serviceBound = false
            android.util.Log.d("MainViewModel", "Service disconnected")
        }
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(chatSessions = sessions) }
            }
        }

        loadUserPreferences()
    }

    fun bindService() {
        if (!serviceBound) {
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        if (serviceBound) {
            val context = getApplication<Application>().applicationContext
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error unbinding service", e)
            }
        }
    }

    private fun loadUserPreferences() {
        viewModelScope.launch {
            userPreferences.languageFlow.collect { savedLanguageCode ->
                val effectiveLanguageCode = if (savedLanguageCode.isEmpty()) {
                    val systemLanguage = getApplication<Application>().applicationContext.getCurrentLanguageCode()
                    android.util.Log.d("MainViewModel", "First run - Using system language: $systemLanguage")
                    userPreferences.setLanguage(systemLanguage)
                    systemLanguage
                } else {
                    android.util.Log.d("MainViewModel", "Using saved language: $savedLanguageCode")
                    savedLanguageCode
                }

                val language = when (effectiveLanguageCode) {
                    "it" -> Language.ITALIAN
                    else -> Language.ENGLISH
                }
                android.util.Log.d("MainViewModel", "Language set to: ${language.displayName} (${language.code})")
                _uiState.update { it.copy(selectedLanguage = language) }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        // Se stiamo uscendo dalla chat/analysis, controlla se la sessione è vuota
        val currentScreen = _uiState.value.currentScreen
        if ((currentScreen == Screen.Chat || currentScreen == Screen.Analysis) && 
            screen == Screen.Home) {
            cleanupEmptySession()
        }
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
    }

    /**
     * Elimina la sessione corrente se non contiene risposte dell'AI.
     * Questo evita di intasare la cronologia con chat vuote o incomplete.
     */
    private fun cleanupEmptySession() {
        val sessionId = _uiState.value.currentSessionId ?: return
        val messages = _uiState.value.chatMessages
        
        // Controlla se c'è almeno una risposta dell'AI
        val hasAiResponse = messages.any { it.role == "assistant" }
        
        if (!hasAiResponse) {
            android.util.Log.d("MainViewModel", "Cleaning up empty session $sessionId - no AI responses")
            viewModelScope.launch {
                try {
                    chatRepository.deleteSession(sessionId)
                    android.util.Log.d("MainViewModel", "Empty session $sessionId deleted successfully")
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error deleting empty session", e)
                }
            }
        }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun startNewChat(title: String, isAnalysisMode: Boolean = false) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "startNewChat - START - isAnalysisMode: $isAnalysisMode")
                android.util.Log.d("MainViewModel", "Selected model: ${_uiState.value.selectedModel}")

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
                _uiState.update {
                    it.copy(errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message))
                }
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
                _uiState.update {
                    it.copy(errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message))
                }
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
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.error_session_not_found))
            }
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
                }

                val currentLanguage = _uiState.value.selectedLanguage.code
                android.util.Log.d("MainViewModel", "sendMessage - Chiamata API in corso, modello: ${_uiState.value.selectedModel}, lingua: $currentLanguage")

                val result = openRouterService.sendMessage(
                    modelId = _uiState.value.selectedModel,
                    messages = messages,
                    isAnalysisMode = _uiState.value.isAnalysisMode,
                    languageCode = currentLanguage
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
                                errorMessage = getApplication<Application>().getString(R.string.error_api, error.message)
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendMessage - EXCEPTION: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message)
                    )
                }
            }
        }
    }

    fun startRecording() {
        if (!serviceBound) {
            bindService()

            viewModelScope.launch {
                delay(300)
                startRecordingInternal()
            }
        } else {
            startRecordingInternal()
        }
    }

    private fun startRecordingInternal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true) }
            recordingService?.startRecording { result ->
                result.onSuccess {
                    android.util.Log.d("MainViewModel", "Recording started successfully")
                }.onFailure { error ->
                    android.util.Log.e("MainViewModel", "Recording failed", error)
                    _uiState.update { it.copy(isRecording = false) }
                }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            recordingService?.stopRecording()

            delay(500)

            val audioDir = File(getApplication<Application>().filesDir, "audio_messages")
            val latestFile = audioDir.listFiles()
                ?.filter { it.name.startsWith("voice_") && it.name.endsWith(".wav") }
                ?.maxByOrNull { it.lastModified() }

            if (latestFile != null && latestFile.exists()) {
                _uiState.update { it.copy(isRecording = false) }
                sendAudioMessage(latestFile)
            } else {
                android.util.Log.e("MainViewModel", "Audio file not found")
                _uiState.update { it.copy(
                    isRecording = false,
                    errorMessage = getApplication<Application>().getString(R.string.error_generic, "File audio non trovato")
                )}
            }
        }
    }

    fun sendAudioMessage(audioFile: File) {
        android.util.Log.d("MainViewModel", "sendAudioMessage - CHIAMATO: ${audioFile.absolutePath}")

        val sessionId = _uiState.value.currentSessionId
        if (sessionId == null) {
            android.util.Log.e("MainViewModel", "sendAudioMessage - ERRORE: sessionId è null!")
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.error_session_not_found))
            }
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "sendAudioMessage - Inizio elaborazione")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val audioPath = audioFile.absolutePath
                val currentLanguage = _uiState.value.selectedLanguage.code

                android.util.Log.d("MainViewModel", "sendAudioMessage - Using AI, languange: $currentLanguage")
                val transcriptionResult = openRouterService.transcribeAudioWithGemini(
                    audioFile = audioFile,
                    languageCode = currentLanguage
                )

                transcriptionResult.fold(
                    onSuccess = { transcription ->
                        android.util.Log.d("MainViewModel", "transcription received: $transcription")

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
                        android.util.Log.e("MainViewModel", "sendAudioMessage - ERROR API: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = getApplication<Application>().getString(R.string.error_transcription, error.message)
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
                        errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message)
                    )
                }
            }
        }
    }

    fun synthesizeTextLocally(text: String) {
        android.util.Log.d("MainViewModel", "synthesizeTextLocally - CHIAMATO: $text")

        if (text.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.error_empty_text))
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                ttsHelper.synthesizeToFile(
                    text = text,
                    onSuccess = { audioFile ->
                        android.util.Log.d("MainViewModel", "TTS success: ${audioFile.absolutePath}")

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingTranscription = text,
                                pendingAudioPath = audioFile.absolutePath,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "TTS error: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = getApplication<Application>().getString(R.string.error_tts, error.message)
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "synthesizeTextLocally - EXCEPTION: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message)
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
                _uiState.update {
                    it.copy(errorMessage = getApplication<Application>().getString(R.string.error_delete, e.message))
                }
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

    fun setLanguage(language: Language) {
        android.util.Log.d("MainViewModel", "User changed language to: ${language.displayName} (${language.code})")
        viewModelScope.launch {
            userPreferences.setLanguage(language.code)
        }
    }

    fun saveLanguagePreference(languageCode: String) {
        android.util.Log.d("MainViewModel", "Saving language preference: $languageCode")
        viewModelScope.launch {
            userPreferences.setLanguage(languageCode)
            val language = when (languageCode) {
                "it" -> Language.ITALIAN
                else -> Language.ENGLISH
            }
            _uiState.update { it.copy(selectedLanguage = language) }
            android.util.Log.d("MainViewModel", "Language preference saved and state updated")
        }
    }


    override fun onCleared() {
        super.onCleared()
        ttsHelper.release()
        audioPlayer.release()
        openRouterService.close()
        unbindService()
    }
}