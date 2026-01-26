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
import com.base.aihelperwearos.AIHelperApplication
import com.base.aihelperwearos.R
import com.base.aihelperwearos.data.Constants
import com.base.aihelperwearos.data.rag.MathContextRetriever
import com.base.aihelperwearos.data.rag.RagRepository
import com.base.aihelperwearos.data.rag.TheoremResult
import com.base.aihelperwearos.data.rag.models.ContentType
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
    Settings,
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
    val selectedModel: String = "openai/gpt-5.2",
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
    val isRecording: Boolean = false,
    val extractedKeywords: List<String>? = null,
    val isTheoryQuery: Boolean? = null
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

    private val ragRepository: RagRepository? by lazy {
        AIHelperApplication.getRagRepository()
    }
    
    private val mathContextRetriever: MathContextRetriever? by lazy {
        ragRepository?.let { MathContextRetriever(it) }
    }

    private var recordingService: AudioRecordingService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        /**
         * Handles successful binding to the recording service.
         *
         * @param name component name of the connected service.
         * @param binder binder provided by the service.
         * @return `Unit` after updating state.
         */
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AudioRecordingService.LocalBinder
            recordingService = localBinder.getService()
            serviceBound = true
            android.util.Log.d("MainViewModel", "Service connected")
        }

        /**
         * Handles unexpected disconnection from the recording service.
         *
         * @param name component name of the disconnected service.
         * @return `Unit` after clearing service state.
         */
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
        loadModelPreference()
    }

    private fun loadModelPreference() {
        viewModelScope.launch {
            userPreferences.modelFlow.collect { modelId ->
                _uiState.update { it.copy(selectedModel = modelId) }
            }
        }
    }

    fun saveModelPreference(modelId: String) {
        viewModelScope.launch {
            userPreferences.setModel(modelId)
            _uiState.update { it.copy(selectedModel = modelId) }
        }
    }

    /**
     * Exports chat history to Downloads folder.
     *
     * @param onResult callback with success message or error.
     * @return `Unit` after export completes.
     */
    fun exportChatHistory(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = chatRepository.exportToFile()
            result.fold(
                onSuccess = { path -> onResult("Exported to: $path") },
                onFailure = { e -> onResult("Export failed: ${e.message}") }
            )
        }
    }

    /**
     * Binds to the audio recording service if not already bound.
     *
     * @return `Unit` after the bind request is issued.
     */
    fun bindService() {
        if (!serviceBound) {
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbinds from the audio recording service if currently bound.
     *
     * @return `Unit` after unbinding or failure handling.
     */
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

    /**
     * Loads user preferences and updates the UI state accordingly.
     *
     * @return `Unit` after launching the preference collection.
     */
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

    /**
     * Navigates to a screen and performs cleanup when leaving chat screens.
     *
     * @param screen destination screen.
     * @return `Unit` after updating UI state.
     */
    fun navigateTo(screen: Screen) {
        val currentScreen = _uiState.value.currentScreen
        if ((currentScreen == Screen.Chat || currentScreen == Screen.Analysis) && 
            screen == Screen.Home) {
            cleanupEmptySession()
        }
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
    }

    /**
     * Removes the current session if it contains no AI responses.
     *
     * @return `Unit` after scheduling cleanup work.
     */
    private fun cleanupEmptySession() {
        val sessionId = _uiState.value.currentSessionId ?: return
        val messages = _uiState.value.chatMessages
        
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

    /**
     * Updates the selected model in UI state.
     *
     * @param modelId model identifier to select.
     * @return `Unit` after updating state.
     */
    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    /**
     * Creates a new chat session and navigates to the chat screen.
     *
     * @param title title used for the new session.
     * @param isAnalysisMode whether to start in analysis mode.
     * @return `Unit` after launching session creation.
     */
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

    /**
     * Loads a stored session and updates UI state.
     *
     * @param sessionId session identifier to load.
     * @return `Unit` after launching load.
     */
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

    /**
     * Observes messages for a session and updates UI state.
     *
     * @param sessionId session identifier to observe.
     * @return `Unit` after launching collection.
     */
    private fun observeMessages(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                _uiState.update { it.copy(chatMessages = messages) }
            }
        }
    }

    /**
     * Sends a user message and handles AI response flow.
     *
     * @param userMessage text message content.
     * @param audioPath optional audio path tied to the message.
     * @return `Unit` after launching send workflow.
     */
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
                
                val extractedKeywords = _uiState.value.extractedKeywords
                val isTheoryFromTranscription = _uiState.value.isTheoryQuery
                
                android.util.Log.d("MainViewModel", "📊 RAG Context - Keywords available: ${extractedKeywords != null}")
                if (extractedKeywords != null) {
                    android.util.Log.d("MainViewModel", "📊 RAG - Extracted keywords (${extractedKeywords.size}): ${extractedKeywords.joinToString(", ")}")
                    android.util.Log.d("MainViewModel", "📊 RAG - Is theory query from transcription: $isTheoryFromTranscription")
                }
                
                if (_uiState.value.isAnalysisMode) {
                    android.util.Log.d("MainViewModel", "🔍 RAG: Analyzing query type")
                    
                    val queryType = if (isTheoryFromTranscription == true) {
                        android.util.Log.d("MainViewModel", "🔍 RAG: Using transcription classification → THEOREM")
                        ContentType.THEOREM
                    } else {
                        val classified = ragRepository?.classifyQueryType(userMessage) ?: ContentType.UNKNOWN
                        android.util.Log.d("MainViewModel", "🔍 RAG: Text-based classification → $classified")
                        classified
                    }
                    
                    android.util.Log.d("MainViewModel", "🔍 RAG: Final query type = $queryType")
                    
                    if (queryType == ContentType.THEOREM) {
                        android.util.Log.d("MainViewModel", "📚 RAG: THEOREM query detected - attempting direct lookup")
                        
                        val enhancedQuery = if (!extractedKeywords.isNullOrEmpty()) {
                            val keywordsText = extractedKeywords.joinToString(" ")
                            android.util.Log.d("MainViewModel", "📚 RAG: Enhancing query with keywords: $keywordsText")
                            "$userMessage $keywordsText"
                        } else {
                            userMessage
                        }
                        
                        android.util.Log.d("MainViewModel", "📚 RAG: Enhanced query: '$enhancedQuery'")
                        val theoremResult = ragRepository?.findTheorem(enhancedQuery)
                        
                        when (theoremResult) {
                            is TheoremResult.Found -> {
                                android.util.Log.d("MainViewModel", "✅ RAG: Theorem FOUND directly → '${theoremResult.theorem.nome}'")
                                android.util.Log.d("MainViewModel", "💰 RAG: Skipping LLM call - returning pre-formatted content")
                                android.util.Log.d("MainViewModel", "📄 RAG: Content length: ${theoremResult.formattedContent.length} chars")
                                
                                chatRepository.addMessage(
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = theoremResult.formattedContent
                                )
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false,
                                        extractedKeywords = null,
                                        isTheoryQuery = null
                                    ) 
                                }
                                android.util.Log.d("MainViewModel", "✅ RAG: Theorem response saved - NO LLM COST!")
                                return@launch
                            }
                            is TheoremResult.NotFound -> {
                                android.util.Log.d("MainViewModel", "⚠️ RAG: Theorem NOT in database: ${theoremResult.reason}")
                                android.util.Log.d("MainViewModel", "🔄 RAG: Falling back to LLM for answer")
                            }
                            null -> {
                                android.util.Log.d("MainViewModel", "⚠️ RAG: Repository not available")
                            }
                        }
                    } else {
                        android.util.Log.d("MainViewModel", "📝 RAG: EXERCISE query - will use RAG context + LLM")
                    }
                }
                
                var ragContext: String? = null
                if (_uiState.value.isAnalysisMode) {
                    android.util.Log.d("MainViewModel", "sendMessage - RAG: Retrieving context for Analysis Mode")
                    ragContext = try {
                        mathContextRetriever?.retrieveContext(userMessage)
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "sendMessage - RAG failed, using base prompt", e)
                        null
                    }
                    
                    if (ragContext != null) {
                        android.util.Log.d("MainViewModel", "sendMessage - RAG: Found relevant context (${ragContext.length} chars)")
                    } else {
                        android.util.Log.d("MainViewModel", "sendMessage - RAG: No relevant context found, using base prompt")
                    }
                }
                
                android.util.Log.d("MainViewModel", "sendMessage - Chiamata API in corso, modello: ${_uiState.value.selectedModel}, lingua: $currentLanguage")

                val result = openRouterService.sendMessage(
                    modelId = _uiState.value.selectedModel,
                    messages = messages,
                    isAnalysisMode = _uiState.value.isAnalysisMode,
                    languageCode = currentLanguage,
                    ragContext = ragContext
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
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                extractedKeywords = null,
                                isTheoryQuery = null
                            ) 
                        }
                        android.util.Log.d("MainViewModel", "sendMessage - Messaggio AI salvato, keywords cleared")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "sendMessage - ERRORE API: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                extractedKeywords = null,
                                isTheoryQuery = null,
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
                        extractedKeywords = null,
                        isTheoryQuery = null,
                        errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message)
                    )
                }
            }
        }
    }

    /**
     * Starts audio recording via the bound service.
     *
     * @return `Unit` after initiating recording.
     */
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

    /**
     * Performs the actual recording start once the service is bound.
     *
     * @return `Unit` after requesting recording start.
     */
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

    /**
     * Stops audio recording and sends the recorded audio for processing.
     *
     * @return `Unit` after initiating stop workflow.
     */
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

    /**
     * Sends an audio file for transcription and updates UI state.
     *
     * @param audioFile audio file to process.
     * @return `Unit` after launching transcription flow.
     */
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

                android.util.Log.d("MainViewModel", "sendAudioMessage - Using AI, language: $currentLanguage")
                android.util.Log.d("MainViewModel", "📡 Starting transcription request...")
                
                val transcriptionResult = openRouterService.transcribeAudioWithGemini(
                    audioFile = audioFile,
                    languageCode = currentLanguage
                )

                transcriptionResult.fold(
                    onSuccess = { result ->
                        android.util.Log.d("MainViewModel", "✅ Transcription received successfully")
                        android.util.Log.d("MainViewModel", "📝 Raw transcription: '${result.transcription}'")
                        android.util.Log.d("MainViewModel", "📌 Extracted keywords (${result.keywords.size}): ${result.keywords.joinToString(", ")}")
                        android.util.Log.d("MainViewModel", "🔍 Is theory query: ${result.isTheoryQuery}")
                        android.util.Log.d("MainViewModel", "👁️ Display text: '${result.displayText}'")
                        
                        android.util.Log.d("MainViewModel", "💾 Storing transcription result in UI state")

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingTranscription = result.displayText,
                                pendingAudioPath = audioPath,
                                extractedKeywords = result.keywords,
                                isTheoryQuery = result.isTheoryQuery,
                                errorMessage = null
                            )
                        }
                        
                        android.util.Log.d("MainViewModel", "✅ UI state updated with transcription and keywords")
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

    /**
     * Synthesizes text to speech locally and stores the result.
     *
     * @param text text content to synthesize.
     * @return `Unit` after launching synthesis.
     */
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

    /**
     * Deletes a chat session from storage.
     *
     * @param session session to delete.
     * @return `Unit` after deletion attempt.
     */
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

    /**
     * Clears the current error message in UI state.
     *
     * @return `Unit` after updating state.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Plays an audio file at the given path.
     *
     * @param audioPath path to the audio file.
     * @return `Unit` after playback starts or fails.
     */
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

    /**
     * Stops audio playback if active.
     *
     * @return `Unit` after stopping playback.
     */
    fun stopAudio() {
        audioPlayer.stopAudio()
    }

    /**
     * Confirms the pending transcription and sends it as a message.
     *
     * @return `Unit` after confirmation handling.
     */
    fun confirmTranscription() {
        val transcription = _uiState.value.pendingTranscription ?: return
        val audioPath = _uiState.value.pendingAudioPath

        android.util.Log.d("MainViewModel", "✅ User confirmed transcription")
        android.util.Log.d("MainViewModel", "📝 Transcription: '$transcription'")
        android.util.Log.d("MainViewModel", "📌 Keywords preserved: ${_uiState.value.extractedKeywords?.joinToString(", ") ?: "none"}")
        
        _uiState.update { it.copy(pendingTranscription = null, pendingAudioPath = null) }

        sendMessage(transcription, audioPath)
    }

    /**
     * Cancels the pending transcription and clears related state.
     *
     * @return `Unit` after clearing state.
     */
    fun cancelTranscription() {
        android.util.Log.d("MainViewModel", "❌ User cancelled transcription - clearing keywords")
        _uiState.update {
            it.copy(
                pendingTranscription = null,
                pendingAudioPath = null,
                extractedKeywords = null,
                isTheoryQuery = null,
                errorMessage = null
            )
        }
    }

    /**
     * Updates the pending transcription text.
     *
     * @param newText updated transcription text.
     * @return `Unit` after updating state.
     */
    fun updateTranscription(newText: String) {
        _uiState.update { it.copy(pendingTranscription = newText) }
    }

    /**
     * Increases the chat font size within allowed bounds.
     *
     * @return `Unit` after updating state.
     */
    fun increaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize + 1).coerceAtMost(16)
            android.util.Log.d("MainViewModel", "Font size: ${state.fontSize} → $newSize")
            state.copy(fontSize = newSize)
        }
    }

    /**
     * Decreases the chat font size within allowed bounds.
     *
     * @return `Unit` after updating state.
     */
    fun decreaseFontSize() {
        _uiState.update { state ->
            val newSize = (state.fontSize - 1).coerceAtLeast(8)
            android.util.Log.d("MainViewModel", "Font size: ${state.fontSize} → $newSize")
            state.copy(fontSize = newSize)
        }
    }

    /**
     * Saves the selected language to user preferences.
     *
     * @param language language selection.
     * @return `Unit` after persisting the preference.
     */
    fun setLanguage(language: Language) {
        android.util.Log.d("MainViewModel", "User changed language to: ${language.displayName} (${language.code})")
        viewModelScope.launch {
            userPreferences.setLanguage(language.code)
        }
    }

    /**
     * Persists a language code and updates UI state.
     *
     * @param languageCode language code to save.
     * @return `Unit` after saving the preference.
     */
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


    /**
     * Releases resources when the ViewModel is cleared.
     *
     * @return `Unit` after cleanup.
     */
    override fun onCleared() {
        super.onCleared()
        ttsHelper.release()
        audioPlayer.release()
        openRouterService.close()
        unbindService()
    }
}
