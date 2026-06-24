package com.base.aihelperwearos.presentation.viewmodel
import com.base.aihelperwearos.BuildConfig

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.base.aihelperwearos.AIHelperApplication
import com.base.aihelperwearos.R
import com.base.aihelperwearos.data.metodi.MetodiRepository
import com.base.aihelperwearos.data.models.ChatModeIds
import com.base.aihelperwearos.data.models.ChatModeSpec
import com.base.aihelperwearos.data.models.ContextToolType
import com.base.aihelperwearos.data.models.SpecializedChatRegistry
import com.base.aihelperwearos.data.models.TranscriptionModels
import com.base.aihelperwearos.data.rag.MathContextRetriever
import com.base.aihelperwearos.data.rag.RagRepository
import com.base.aihelperwearos.data.specialized.ChatContextTool
import com.base.aihelperwearos.data.specialized.ExerciseRagTool
import com.base.aihelperwearos.data.specialized.MetodiCodeTool
import com.base.aihelperwearos.data.specialized.MetodiTheoryTool
import com.base.aihelperwearos.data.specialized.logContextResult

import com.base.aihelperwearos.data.repository.ChatRepository
import com.base.aihelperwearos.data.repository.ChatSession
import com.base.aihelperwearos.data.repository.ChatMessage
import com.base.aihelperwearos.data.models.Message
import com.base.aihelperwearos.data.network.OpenRouterService
import com.base.aihelperwearos.presentation.utils.AudioPlayer
import com.base.aihelperwearos.presentation.utils.TextToSpeechHelper
import com.base.aihelperwearos.presentation.services.AudioRecordingService
import com.base.aihelperwearos.utils.getCurrentLanguageCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

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

private enum class NetworkUse {
    Chat,
    AudioUpload
}

private data class NetworkSnapshot(
    val isAvailable: Boolean,
    val isWeak: Boolean,
    val uploadKbps: Int,
    val downloadKbps: Int
)

private sealed class PendingRetryAction {
    data class ChatResponse(val sessionId: Long) : PendingRetryAction()
    data class AudioTranscription(val audioPath: String) : PendingRetryAction()
}

data class ChatUiState(
    val currentScreen: Screen = Screen.Home,
    val selectedModel: String = "openai/gpt-5.5",
    val selectedTranscriptionModel: String = TranscriptionModels.DEFAULT_ID,
    val currentSessionId: Long? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAnalysisMode: Boolean = false,
    val chatModeId: String = ChatModeIds.GENERAL,
    val isCurrentModeEnabled: Boolean = true,
    val chatSessions: List<ChatSession> = emptyList(),
    val fontSize: Int = 11,
    val pendingTranscription: String? = null,
    val pendingAudioPath: String? = null,
    val selectedLanguage: Language = Language.ITALIAN,
    val recordedAudioFile: File? = null,
    val isRecording: Boolean = false,
    val extractedKeywords: List<String>? = null,
    val isTheoryQuery: Boolean? = null,
    val networkWarningMessage: String? = null,
    val canRetryLastAction: Boolean = false,
    val retryMessage: String? = null
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
    private val metodiRepository = MetodiRepository(application)

    private val cachedRagRepositories = mutableMapOf<String, RagRepository>()
    private val cachedContextTools = mutableMapOf<String, ChatContextTool>()
    private var pendingRetryAction: PendingRetryAction? = null

    private fun appString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun getNetworkSnapshot(use: NetworkUse): NetworkSnapshot {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkSnapshot(isAvailable = true, isWeak = false, uploadKbps = 0, downloadKbps = 0)

        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkSnapshot(isAvailable = false, isWeak = false, uploadKbps = 0, downloadKbps = 0)

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkSnapshot(isAvailable = false, isWeak = false, uploadKbps = 0, downloadKbps = 0)

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) {
            return NetworkSnapshot(isAvailable = false, isWeak = false, uploadKbps = 0, downloadKbps = 0)
        }

        val uploadKbps = capabilities.linkUpstreamBandwidthKbps
        val downloadKbps = capabilities.linkDownstreamBandwidthKbps
        val minUploadKbps = if (use == NetworkUse.AudioUpload) 512 else 128
        val minDownloadKbps = if (use == NetworkUse.AudioUpload) 512 else 384

        val weakUpload = uploadKbps in 1 until minUploadKbps
        val weakDownload = downloadKbps in 1 until minDownloadKbps
        val notValidated = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return NetworkSnapshot(
            isAvailable = true,
            isWeak = weakUpload || weakDownload || notValidated,
            uploadKbps = uploadKbps,
            downloadKbps = downloadKbps
        )
    }

    private fun bandwidthLabel(kbps: Int): String {
        return if (kbps > 0) "$kbps kbps" else "--"
    }

    private fun networkWarning(snapshot: NetworkSnapshot): String? {
        if (!snapshot.isAvailable) return appString(R.string.network_unavailable)
        if (!snapshot.isWeak) return null
        return appString(
            R.string.network_weak,
            bandwidthLabel(snapshot.uploadKbps),
            bandwidthLabel(snapshot.downloadKbps)
        )
    }

    private fun setRetryableFailure(message: String, action: PendingRetryAction) {
        pendingRetryAction = action
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                canRetryLastAction = true,
                retryMessage = appString(R.string.retry_question)
            )
        }
    }

    private fun deleteFileQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun Throwable.isTimeoutLike(): Boolean {
        return generateSequence(this) { it.cause }.any { throwable ->
            val typeName = throwable::class.java.simpleName.lowercase(Locale.ROOT)
            val text = throwable.message.orEmpty().lowercase(Locale.ROOT)
            "timeout" in typeName || "timed out" in text || "timeout" in text
        }
    }

    private fun apiFailureMessage(error: Throwable): String {
        return if (error.isTimeoutLike()) {
            appString(R.string.error_timeout)
        } else {
            appString(R.string.error_api, error.message ?: "errore sconosciuto")
        }
    }

    private fun transcriptionFailureMessage(error: Throwable): String {
        return if (error.isTimeoutLike()) {
            appString(R.string.error_timeout)
        } else {
            appString(R.string.error_transcription, error.message ?: "errore sconosciuto")
        }
    }

    private fun resolveNewSessionMode(modeId: String): ChatModeSpec {
        val normalized = SpecializedChatRegistry.normalizeModeId(modeId)
        val spec = SpecializedChatRegistry.get(normalized)
        return if (spec.enabled) spec else SpecializedChatRegistry.general
    }

    private fun screenForMode(@Suppress("UNUSED_PARAMETER") modeId: String): Screen = Screen.Chat

    private fun isDefaultSessionTitle(title: String): Boolean {
        val app = getApplication<Application>()
        return SpecializedChatRegistry.all().any { title == app.getString(it.titleRes) } ||
            title == app.getString(R.string.analysis_mode)
    }

    private fun buildModelMessages(messages: List<ChatMessage>, modeSpec: ChatModeSpec): List<Message> {
        val modelMessages = messages.map { msg -> Message(role = msg.role, content = msg.content) }
        return modeSpec.historyLimit?.let { modelMessages.takeLast(it) } ?: modelMessages
    }

    private fun getContextTools(modeSpec: ChatModeSpec): List<ChatContextTool> {
        return modeSpec.contextTools.mapNotNull { toolType ->
            when (toolType) {
                ContextToolType.EXERCISE_ANALYSIS,
                ContextToolType.EXERCISE_PHYSICS,
                ContextToolType.SOFTWARE_ENGINEERING -> getExerciseRagTool(modeSpec)
                ContextToolType.METODI_THEORY -> cachedContextTools.getOrPut(toolType.name) {
                    MetodiTheoryTool(metodiRepository)
                }
                ContextToolType.METODI_CODE -> cachedContextTools.getOrPut(toolType.name) {
                    MetodiCodeTool(metodiRepository)
                }
            }
        }
    }

    private fun getExerciseRagTool(modeSpec: ChatModeSpec): ChatContextTool? {
        val repository = AIHelperApplication.getRagRepository(modeSpec.id) ?: return null
        val cachedRepository = cachedRagRepositories[modeSpec.id]
        val cachedTool = cachedContextTools[modeSpec.id]
        if (cachedRepository === repository && cachedTool != null) {
            return cachedTool
        }

        val isPhysics = modeSpec.id == ChatModeIds.PHYSICS
        val isSoftwareEngineering = modeSpec.id == ChatModeIds.SOFTWARE_ENGINEERING
        val retriever = MathContextRetriever(
            ragRepository = repository,
            maxExercises = when {
                isSoftwareEngineering -> 4
                isPhysics -> 3
                else -> 2
            },
            maxPromptLength = when {
                isSoftwareEngineering -> 6500
                isPhysics -> 5200
                else -> 4096
            },
            contextHeader = when {
                isSoftwareEngineering -> "CONTESTO INGEGNERIA DEL SOFTWARE RILEVANTE:"
                isPhysics -> "ESEMPI DI FISICA RILEVANTI:"
                else -> "ESEMPI RILEVANTI DALLA PROFESSORESSA:"
            },
            solutionLabel = when {
                isSoftwareEngineering -> "Appunti/esempio del professore"
                isPhysics -> "Svolgimento di riferimento"
                else -> "Svolgimento della professoressa"
            },
            itemLabel = when {
                isSoftwareEngineering -> "Riferimento"
                else -> "Esercizio"
            }
        )
        val tool = ExerciseRagTool(retriever, getApplication<Application>().getString(modeSpec.titleRes))
        cachedRagRepositories[modeSpec.id] = repository
        cachedContextTools[modeSpec.id] = tool
        return tool
    }

    private var recordingService: AudioRecordingService? = null
    private var serviceBound = false
    private var recordingResultDeferred: CompletableDeferred<Result<File>>? = null
    private var messageObservationJob: Job? = null
    private var isStoppingRecording = false

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
        loadTranscriptionModelPreference()
        restoreResumeSession()
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

    private fun loadTranscriptionModelPreference() {
        viewModelScope.launch {
            userPreferences.transcriptionModelFlow.collect { modelId ->
                _uiState.update { it.copy(selectedTranscriptionModel = modelId) }
            }
        }
    }

    fun saveTranscriptionModelPreference(modelId: String) {
        viewModelScope.launch {
            val normalizedModelId = TranscriptionModels.normalizeId(modelId)
            userPreferences.setTranscriptionModel(normalizedModelId)
            _uiState.update { it.copy(selectedTranscriptionModel = normalizedModelId) }
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
        val destination = if (screen == Screen.Analysis) Screen.Chat else screen
        if ((currentScreen == Screen.Chat || currentScreen == Screen.Analysis) && 
            destination == Screen.Home) {
            cleanupEmptySession()
            stopObservingMessages()
            val action = pendingRetryAction
            if (action is PendingRetryAction.AudioTranscription) {
                deleteFileQuietly(action.audioPath)
            }
            pendingRetryAction = null
            clearResumeSession()
        }
        _uiState.update {
            it.copy(
                currentScreen = destination,
                currentSessionId = if (destination == Screen.Home) null else it.currentSessionId,
                chatMessages = if (destination == Screen.Home) emptyList() else it.chatMessages,
                errorMessage = null,
                networkWarningMessage = null,
                canRetryLastAction = false,
                retryMessage = null
            )
        }
    }

    /**
     * Removes the current session if it contains no saved reply.
     *
     * @return `Unit` after scheduling cleanup work.
     */
    private fun cleanupEmptySession() {
        val sessionId = _uiState.value.currentSessionId ?: return
        val messages = _uiState.value.chatMessages
        
        val hasAssistantResponse = messages.any { it.role == "assistant" }
        
        if (!hasAssistantResponse) {
            android.util.Log.d("MainViewModel", "Cleaning up empty session $sessionId - no saved replies")
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
     * @param modeId specialized mode id to start.
     * @return `Unit` after launching session creation.
     */
    fun startNewChat(
        title: String,
        modeId: String = ChatModeIds.GENERAL
    ) {
        viewModelScope.launch {
            try {
                val modeSpec = resolveNewSessionMode(modeId)
                val effectiveTitle = if (modeSpec.id == modeId) title else getApplication<Application>().getString(modeSpec.titleRes)

                android.util.Log.d("MainViewModel", "startNewChat - START - modeId: ${modeSpec.id}")
                android.util.Log.d("MainViewModel", "Selected model: ${_uiState.value.selectedModel}")

                android.util.Log.d("MainViewModel", "Creating session with title: $effectiveTitle")

                val sessionId = chatRepository.createSession(
                    modelId = _uiState.value.selectedModel,
                    title = effectiveTitle,
                    isAnalysisMode = modeSpec.id == ChatModeIds.ANALYSIS,
                    modeId = modeSpec.id
                )

                android.util.Log.d("MainViewModel", "Session created - ID: $sessionId")

                val newScreen = screenForMode(modeSpec.id)
                android.util.Log.d("MainViewModel", "Navigating to: $newScreen")

                stopObservingMessages()
                _uiState.update {
                    it.copy(
                        currentSessionId = sessionId,
                        currentScreen = newScreen,
                        isAnalysisMode = modeSpec.id == ChatModeIds.ANALYSIS,
                        chatModeId = modeSpec.id,
                        isCurrentModeEnabled = modeSpec.enabled,
                        chatMessages = emptyList()
                    )
                }

                android.util.Log.d("MainViewModel", "State updated - currentScreen: ${_uiState.value.currentScreen}")

                saveResumeSession(sessionId)
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
            loadSessionInternal(sessionId, keepForResume = true)
        }
    }

    private suspend fun loadSessionInternal(sessionId: Long, keepForResume: Boolean): Boolean {
        return try {
            val session = chatRepository.getSession(sessionId)
            if (session != null) {
                val modeId = session.effectiveModeId()
                val modeSpec = SpecializedChatRegistry.get(modeId)
                stopObservingMessages()
                _uiState.update {
                    it.copy(
                        currentSessionId = sessionId,
                        selectedModel = session.modelId,
                        isAnalysisMode = modeSpec.id == ChatModeIds.ANALYSIS,
                        chatModeId = modeSpec.id,
                        isCurrentModeEnabled = modeSpec.enabled,
                        currentScreen = screenForMode(modeSpec.id)
                    )
                }
                if (keepForResume) {
                    saveResumeSession(sessionId)
                }
                observeMessages(sessionId)
                true
            } else {
                stopObservingMessages()
                clearResumeSession()
                false
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.error_generic, e.message))
            }
            false
        }
    }

    private fun restoreResumeSession() {
        viewModelScope.launch {
            val sessionId = userPreferences.getResumeSessionId() ?: return@launch
            val restored = loadSessionInternal(sessionId, keepForResume = false)
            if (!restored) {
                clearResumeSession()
            }
        }
    }

    private fun saveResumeSession(sessionId: Long) {
        viewModelScope.launch {
            userPreferences.setResumeSession(sessionId)
        }
    }

    private fun clearResumeSession() {
        viewModelScope.launch {
            userPreferences.clearResumeSession()
        }
    }

    fun clearResumeSessionBlocking() {
        runBlocking {
            userPreferences.clearResumeSession()
        }
    }

    /**
     * Observes messages for a session and updates UI state.
     *
     * @param sessionId session identifier to observe.
     * @return `Unit` after launching collection.
     */
    private fun observeMessages(sessionId: Long) {
        messageObservationJob?.cancel()
        messageObservationJob = viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messages ->
                _uiState.update { state ->
                    if (state.currentSessionId == sessionId) {
                        state.copy(chatMessages = messages)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun stopObservingMessages() {
        messageObservationJob?.cancel()
        messageObservationJob = null
    }

    private fun isActiveSession(sessionId: Long): Boolean {
        return _uiState.value.currentSessionId == sessionId
    }

    private fun updateActiveSession(
        sessionId: Long,
        transform: (ChatUiState) -> ChatUiState
    ) {
        _uiState.update { state ->
            if (state.currentSessionId == sessionId) transform(state) else state
        }
    }

    /**
     * Sends a user message and handles the response flow.
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

        val modeSpec = SpecializedChatRegistry.get(_uiState.value.chatModeId)
        android.util.Log.d("MainViewModel", "sendMessage - sessionId: $sessionId, modeId: ${modeSpec.id}")

        if (!modeSpec.enabled) {
            _uiState.update {
                it.copy(errorMessage = getApplication<Application>().getString(R.string.mode_disabled_read_only))
            }
            return
        }

        viewModelScope.launch {
            var messageSaved = false
            try {
                if (!isActiveSession(sessionId)) return@launch
                android.util.Log.d("MainViewModel", "sendMessage - Inizio elaborazione")
                pendingRetryAction = null
                updateActiveSession(sessionId) {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        canRetryLastAction = false,
                        retryMessage = null
                    )
                }

                android.util.Log.d("MainViewModel", "sendMessage - Salvataggio messaggio utente")
                chatRepository.addMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = userMessage,
                    audioPath = audioPath
                )
                messageSaved = true

                val session = chatRepository.getSession(sessionId)
                if (session != null && isDefaultSessionTitle(session.title)) {
                    val newTitle = userMessage.take(30) + if (userMessage.length > 30) "..." else ""
                    chatRepository.updateSessionTitle(sessionId, newTitle)
                }

                requestAssistantResponse(sessionId, modeSpec)

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendMessage - EXCEPTION: ${e.message}", e)
                if (messageSaved) {
                    if (isActiveSession(sessionId)) {
                        setRetryableFailure(
                            message = appString(R.string.error_generic, e.message ?: "errore sconosciuto"),
                            action = PendingRetryAction.ChatResponse(sessionId)
                        )
                    }
                } else {
                    updateActiveSession(sessionId) {
                        it.copy(
                            isLoading = false,
                            errorMessage = appString(R.string.error_generic, e.message ?: "errore sconosciuto")
                        )
                    }
                }
            }
        }
    }

    private suspend fun requestAssistantResponse(sessionId: Long, modeSpec: ChatModeSpec) {
        val retryAction = PendingRetryAction.ChatResponse(sessionId)
        val networkSnapshot = getNetworkSnapshot(NetworkUse.Chat)
        val warning = networkWarning(networkSnapshot)
        if (!networkSnapshot.isAvailable) {
            if (isActiveSession(sessionId)) {
                setRetryableFailure(
                    message = warning ?: appString(R.string.network_unavailable),
                    action = retryAction
                )
            }
            return
        }

        updateActiveSession(sessionId) {
            it.copy(
                isLoading = true,
                errorMessage = null,
                canRetryLastAction = false,
                retryMessage = null,
                networkWarningMessage = warning
            )
        }

        try {
                val storedMessages = chatRepository.getMessagesForSession(sessionId).first()
                val messages = buildModelMessages(
                    messages = storedMessages,
                    modeSpec = modeSpec
                )
                val lastUserMessage = storedMessages.lastOrNull { it.role == "user" }?.content.orEmpty()

                val currentLanguage = _uiState.value.selectedLanguage.code
                val extractedKeywords = _uiState.value.extractedKeywords
                val ragQuery = if (!extractedKeywords.isNullOrEmpty()) {
                    "$lastUserMessage ${extractedKeywords.joinToString(" ")}"
                } else {
                    lastUserMessage
                }

                val ragContext = getContextTools(modeSpec).mapNotNull { tool ->
                    val result = try {
                        tool.retrieve(ragQuery)
                    } catch (e: Exception) {
                        android.util.Log.w("MainViewModel", "sendMessage - ${tool.logLabel} context failed", e)
                        null
                    }
                    if (result != null) {
                        logContextResult("MainViewModel", tool, result)
                    }
                    result?.context
                }.joinToString("\n\n").ifBlank { null }

                android.util.Log.d("MainViewModel", "sendMessage - Chiamata API in corso, modello: ${_uiState.value.selectedModel}, lingua: $currentLanguage")

                val result = openRouterService.sendMessage(
                    modelId = _uiState.value.selectedModel,
                    messages = messages,
                    languageCode = currentLanguage,
                    ragContext = ragContext,
                    modeId = modeSpec.id
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
                        updateActiveSession(sessionId) {
                            it.copy(
                                isLoading = false,
                                extractedKeywords = null,
                                isTheoryQuery = null,
                                networkWarningMessage = null,
                                canRetryLastAction = false,
                                retryMessage = null
                            ) 
                        }
                        if (isActiveSession(sessionId)) {
                            pendingRetryAction = null
                        }
                        android.util.Log.d("MainViewModel", "sendMessage - risposta salvata, keywords cleared")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "sendMessage - ERRORE API: ${error.message}", error)
                        if (isActiveSession(sessionId)) {
                            setRetryableFailure(apiFailureMessage(error), retryAction)
                        }
                    }
                )

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendMessage - EXCEPTION: ${e.message}", e)
                if (isActiveSession(sessionId)) {
                    setRetryableFailure(apiFailureMessage(e), retryAction)
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
            if (_uiState.value.isRecording) return@launch
            isStoppingRecording = false
            _uiState.update { it.copy(isRecording = true) }
            recordingResultDeferred = CompletableDeferred()
            recordingService?.startRecording { result ->
                val deferred = recordingResultDeferred
                if (deferred?.isCompleted == false) {
                    deferred.complete(result)
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
        if (isStoppingRecording) return
        isStoppingRecording = true
        viewModelScope.launch {
            val stopResultDeferred = recordingResultDeferred
            recordingService?.stopRecording()

            val stopResult = withTimeoutOrNull(3000) {
                stopResultDeferred?.await()
            }

            val recordedFile = stopResult?.getOrNull()
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 44L) {
                android.util.Log.d(
                    "MainViewModel",
                    "Using recorder callback file: ${recordedFile.absolutePath}, size=${recordedFile.length()}"
                )
                _uiState.update { it.copy(isRecording = false) }
                recordingResultDeferred = null
                isStoppingRecording = false
                sendAudioMessage(recordedFile)
            } else {
                val errorMessage = stopResult?.exceptionOrNull()?.message ?: "File audio non trovato"
                android.util.Log.e("MainViewModel", "Audio file not available from recorder callback: $errorMessage")
                recordingResultDeferred = null
                _uiState.update { it.copy(
                    isRecording = false,
                    errorMessage = getApplication<Application>().getString(R.string.error_generic, errorMessage)
                )}
                isStoppingRecording = false
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
                if (!isActiveSession(sessionId)) return@launch
                android.util.Log.d("MainViewModel", "sendAudioMessage - Inizio elaborazione")
                pendingRetryAction = null
                updateActiveSession(sessionId) {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        canRetryLastAction = false,
                        retryMessage = null
                    )
                }

                val audioPath = audioFile.absolutePath
                val currentLanguage = _uiState.value.selectedLanguage.code
                val transcriptionModelId = _uiState.value.selectedTranscriptionModel
                val modeSpec = SpecializedChatRegistry.get(_uiState.value.chatModeId)
                val retryAction = PendingRetryAction.AudioTranscription(audioPath)

                if (!modeSpec.enabled) {
                    try { audioFile.delete() } catch (_: Exception) {}
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getApplication<Application>().getString(R.string.mode_disabled_read_only)
                        )
                    }
                    return@launch
                }

                val networkSnapshot = getNetworkSnapshot(NetworkUse.AudioUpload)
                val warning = networkWarning(networkSnapshot)
                if (!networkSnapshot.isAvailable) {
                    if (isActiveSession(sessionId)) {
                        setRetryableFailure(
                            message = warning ?: appString(R.string.network_unavailable),
                            action = retryAction
                        )
                    }
                    return@launch
                }

                updateActiveSession(sessionId) { it.copy(networkWarningMessage = warning) }

                android.util.Log.d("MainViewModel", "sendAudioMessage - cloud transcription, language: $currentLanguage, modeId: ${modeSpec.id}, transcriptionModel: $transcriptionModelId")
                android.util.Log.d("MainViewModel", "Starting transcription request")
                
                val transcriptionResult = openRouterService.transcribeAudioWithGemini(
                    audioFile = audioFile,
                    languageCode = currentLanguage,
                    modeId = modeSpec.id,
                    transcriptionModelId = transcriptionModelId,
                    deleteOnCompletion = false
                )

                transcriptionResult.fold(
                    onSuccess = { result ->
                        android.util.Log.d("MainViewModel", "Transcription received successfully")
                        android.util.Log.d("MainViewModel", "Raw transcription: '${result.transcription}'")
                        android.util.Log.d("MainViewModel", "Extracted keywords (${result.keywords.size}): ${result.keywords.joinToString(", ")}")
                        android.util.Log.d("MainViewModel", "Is theory query: ${result.isTheoryQuery}")
                        android.util.Log.d("MainViewModel", "Display text: '${result.displayText}'")
                        
                        android.util.Log.d("MainViewModel", "Storing transcription result in UI state")

                        updateActiveSession(sessionId) {
                            it.copy(
                                isLoading = false,
                                pendingTranscription = result.displayText,
                                pendingAudioPath = audioPath,
                                extractedKeywords = result.keywords,
                                isTheoryQuery = result.isTheoryQuery,
                                errorMessage = null,
                                networkWarningMessage = null,
                                canRetryLastAction = false,
                                retryMessage = null
                            )
                        }
                        if (isActiveSession(sessionId)) {
                            pendingRetryAction = null
                        }
                        
                        android.util.Log.d("MainViewModel", "UI state updated with transcription and keywords")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "sendAudioMessage - ERROR API: ${error.message}", error)
                        if (isActiveSession(sessionId)) {
                            setRetryableFailure(transcriptionFailureMessage(error), retryAction)
                        }
                    }
                )


            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "sendAudioMessage - EXCEPTION: ${e.message}", e)
                if (isActiveSession(sessionId)) {
                    setRetryableFailure(
                        message = transcriptionFailureMessage(e),
                        action = PendingRetryAction.AudioTranscription(audioFile.absolutePath)
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

    fun retryLastAction() {
        val action = pendingRetryAction ?: return
        when (action) {
            is PendingRetryAction.ChatResponse -> {
                viewModelScope.launch {
                    val session = chatRepository.getSession(action.sessionId)
                    if (session == null) {
                        pendingRetryAction = null
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                canRetryLastAction = false,
                                retryMessage = null,
                                errorMessage = appString(R.string.error_session_not_found)
                            )
                        }
                        return@launch
                    }

                    val modeSpec = SpecializedChatRegistry.get(session.effectiveModeId())
                    if (!modeSpec.enabled) {
                        pendingRetryAction = null
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                canRetryLastAction = false,
                                retryMessage = null,
                                errorMessage = appString(R.string.mode_disabled_read_only)
                            )
                        }
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            currentSessionId = action.sessionId,
                            chatModeId = modeSpec.id,
                            isCurrentModeEnabled = modeSpec.enabled
                        )
                    }
                    requestAssistantResponse(action.sessionId, modeSpec)
                }
            }
            is PendingRetryAction.AudioTranscription -> {
                val audioFile = File(action.audioPath)
                if (audioFile.exists() && audioFile.length() > 0L) {
                    sendAudioMessage(audioFile)
                } else {
                    pendingRetryAction = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canRetryLastAction = false,
                            retryMessage = null,
                            errorMessage = appString(R.string.audio_retry_file_missing)
                        )
                    }
                }
            }
        }
    }

    fun dismissRetry() {
        val action = pendingRetryAction
        if (action is PendingRetryAction.AudioTranscription) {
            deleteFileQuietly(action.audioPath)
        }
        pendingRetryAction = null
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = null,
                canRetryLastAction = false,
                retryMessage = null,
                networkWarningMessage = null,
                extractedKeywords = null,
                isTheoryQuery = null
            )
        }
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

        android.util.Log.d("MainViewModel", "User confirmed transcription")
        android.util.Log.d("MainViewModel", "Transcription: '$transcription'")
        android.util.Log.d("MainViewModel", "Keywords preserved: ${_uiState.value.extractedKeywords?.joinToString(", ") ?: "none"}")
        
        _uiState.update { it.copy(pendingTranscription = null, pendingAudioPath = null) }

        sendMessage(transcription, audioPath)
    }

    /**
     * Cancels the pending transcription and clears related state.
     *
     * @return `Unit` after clearing state.
     */
    fun cancelTranscription() {
        android.util.Log.d("MainViewModel", "User cancelled transcription - clearing keywords")
        deleteFileQuietly(_uiState.value.pendingAudioPath)
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
        stopObservingMessages()
        ttsHelper.release()
        audioPlayer.release()
        openRouterService.close()
        unbindService()
    }
}
