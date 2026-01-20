package com.base.aihelperwearos.presentation

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.base.aihelperwearos.R
import com.base.aihelperwearos.presentation.components.MathMarkdownText
import com.base.aihelperwearos.presentation.theme.AIHelperWearOSTheme
import com.base.aihelperwearos.presentation.viewmodel.MainViewModel
import com.base.aihelperwearos.presentation.viewmodel.Screen
import com.base.aihelperwearos.presentation.utils.RemoteInputHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    /**
     * Initializes the activity and sets the Compose content tree.
     *
     * @param savedInstanceState saved instance state bundle.
     * @return `Unit` after content is set.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()

            LaunchedEffect(Unit) {
                viewModel.bindService()
            }

            WearApp(viewModel, this)
        }
    }
}

/**
 * Updates the activity locale and applies configuration changes.
 *
 * @param activity activity whose resources will be updated.
 * @param languageCode ISO language code to apply.
 * @return `Unit` after updating the configuration.
 */
fun setLocale(activity: ComponentActivity, languageCode: String) {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val resources = activity.resources
    val config = resources.configuration
    config.setLocale(locale)
    resources.updateConfiguration(config, resources.displayMetrics)
}

/**
 * Keeps the device screen on while this composable is active.
 *
 * @return `Unit` after applying the keep-screen-on flag.
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * Hosts the main Wear OS UI based on the current screen state.
 *
 * @param viewModel view model providing UI state and actions.
 * @param activity host activity used for locale changes.
 * @return `Unit` after composing the UI.
 */
@Composable
fun WearApp(viewModel: MainViewModel, activity: ComponentActivity) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.currentScreen) {
        android.util.Log.d("MainActivity", "UI recomposed - currentScreen: ${uiState.currentScreen}, isAnalysisMode: ${uiState.isAnalysisMode}")
    }

    AIHelperWearOSTheme {
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
        ) {
            val analysisTitle = stringResource(id = R.string.analysis_mode)
            val newChatTitle = stringResource(id = R.string.new_chat)

            when (uiState.currentScreen) {
                Screen.Home -> HomeScreen(
                    onNewChat = {
                        viewModel.startNewChat(title = newChatTitle, isAnalysisMode = false)
                    },
                    onAnalysis = {
                        viewModel.startNewChat(title = analysisTitle, isAnalysisMode = true)
                    },
                    onHistory = {
                        viewModel.navigateTo(Screen.History)
                    },
                    onSettings = {
                        viewModel.navigateTo(Screen.Settings)
                    }
                )
                Screen.Settings -> SettingsScreen(
                    selectedModel = uiState.selectedModel,
                    onModelSelected = { model ->
                        viewModel.saveModelPreference(model)
                    },
                    onLanguageChange = { languageCode ->
                        setLocale(activity, languageCode)
                        viewModel.saveLanguagePreference(languageCode)
                        activity.recreate()
                    },
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
                Screen.Chat -> ChatScreen(
                    uiState = uiState,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onSynthesizeLocally = { text -> viewModel.synthesizeTextLocally(text) },
                    onPlayAudio = { path -> viewModel.playAudio(path) },
                    onIncreaseFontSize = { viewModel.increaseFontSize() },
                    onDecreaseFontSize = { viewModel.decreaseFontSize() },
                    onBack = { viewModel.navigateTo(Screen.Home) },
                    onConfirmTranscription = { viewModel.confirmTranscription() },
                    onCancelTranscription = { viewModel.cancelTranscription() },
                    onUpdateTranscription = { viewModel.updateTranscription(it) }
                )
                Screen.Analysis -> AnalysisScreen(
                    uiState = uiState,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onPlayAudio = { path -> viewModel.playAudio(path) },
                    onIncreaseFontSize = { viewModel.increaseFontSize() },
                    onDecreaseFontSize = { viewModel.decreaseFontSize() },
                    onBack = { viewModel.navigateTo(Screen.Home) },
                    onConfirmTranscription = { viewModel.confirmTranscription() },
                    onCancelTranscription = { viewModel.cancelTranscription() },
                    onUpdateTranscription = { viewModel.updateTranscription(it) }
                )
                Screen.History -> HistoryScreen(
                    sessions = uiState.chatSessions,
                    onSessionClick = { session ->
                        viewModel.loadSession(session.id)
                    },
                    onDeleteSession = { session ->
                        viewModel.deleteSession(session)
                    },
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
            }
        }
    }
}

/**
 * Renders the home screen with navigation actions.
 *
 * @param onNewChat callback to start a new chat.
 * @param onAnalysis callback to start analysis mode.
 * @param onHistory callback to open chat history.
 * @param onSettings callback to open settings.
 * @return `Unit` after composing the screen.
 */
@Composable
fun HomeScreen(
    onNewChat: () -> Unit,
    onAnalysis: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text(
                stringResource(R.string.ai_helper),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                stringResource(R.string.wear_os),
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(stringResource(R.string.new_chat))
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = onAnalysis,
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.secondary
                )
            ) {
                Text(stringResource(R.string.analysis_mode), color = MaterialTheme.colors.onSecondary)
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = onHistory,
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(stringResource(R.string.history))
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface
                )
            ) {
                Text("⚙️ " + stringResource(R.string.settings))
            }
        }
    }
}

/**
 * Displays the settings screen with model and language selection.
 *
 * @param selectedModel currently selected model id.
 * @param onModelSelected callback with the chosen model id.
 * @param onLanguageChange callback with the selected language code.
 * @param onBack callback to return to the previous screen.
 * @return `Unit` after composing the screen.
 */
@Composable
fun SettingsScreen(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val models = listOf(
        "google/gemini-3-pro-preview" to stringResource(R.string.model_gemini_pro),
        "anthropic/claude-sonnet-4.5" to stringResource(R.string.model_claude_sonnet),
        "openai/gpt-5.2" to stringResource(R.string.model_gpt5)
    )

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(40.dp),
            ) {
                Text(stringResource(R.string.back_arrow))
            }
        }

        item {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                stringResource(R.string.choose_model),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        items(models.size) { index ->
            val (modelId, modelName) = models[index]
            Chip(
                onClick = { onModelSelected(modelId) },
                label = {
                    Text(
                        modelName,
                        style = MaterialTheme.typography.body2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (modelId == selectedModel)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.surface
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Text(
                stringResource(R.string.select_language),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onLanguageChange("it") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    )
                ) {
                    Text("🇮🇹", style = MaterialTheme.typography.title2)
                }

                Button(
                    onClick = { onLanguageChange("en") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    )
                ) {
                    Text("🇬🇧", style = MaterialTheme.typography.title2)
                }
            }
        }
    }
}

/**
 * Displays the chat screen with message list and input controls.
 *
 * @param uiState current chat UI state.
 * @param onSendMessage callback to send text messages.
 * @param onStartRecording callback to begin audio recording.
 * @param onStopRecording callback to stop audio recording.
 * @param onSynthesizeLocally callback to synthesize speech locally.
 * @param onPlayAudio callback to play an audio message.
 * @param onIncreaseFontSize callback to increase message font size.
 * @param onDecreaseFontSize callback to decrease message font size.
 * @param onBack callback to return to the home screen.
 * @param onConfirmTranscription callback to accept a transcription.
 * @param onCancelTranscription callback to discard a transcription.
 * @param onUpdateTranscription callback to edit transcription text.
 * @return `Unit` after composing the screen.
 */
@Composable
fun ChatScreen(
    uiState: com.base.aihelperwearos.presentation.viewmodel.ChatUiState,
    onSendMessage: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSynthesizeLocally: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onBack: () -> Unit,
    onConfirmTranscription: () -> Unit,
    onCancelTranscription: () -> Unit,
    onUpdateTranscription: (String) -> Unit
) {
    val context = LocalContext.current
    var showTtsDialog by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf("") }
    
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(uiState.chatMessages.size, uiState.pendingTranscription) {
        if (uiState.chatMessages.isNotEmpty() || uiState.pendingTranscription != null) {
            coroutineScope.launch {
                val targetIndex = if (uiState.pendingTranscription != null) {
                    uiState.chatMessages.size + 3
                } else {
                    uiState.chatMessages.size
                }
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
            }
        }
    }

    if (uiState.isRecording) {
        KeepScreenOn()
    }

    val microphonePermissionDenied = stringResource(R.string.microphone_permission_denied)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartRecording()
        } else {
            android.util.Log.e("ChatScreen", microphonePermissionDenied)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            RemoteInputHelper.getTextFromIntent(data)?.let { text ->
                if (text.isNotBlank()) {
                    if (uiState.pendingTranscription != null) {
                        onUpdateTranscription(text)
                    } else {
                        pendingText = text
                        showTtsDialog = true
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            state = listState,
            horizontalAlignment = Alignment.Start
    ) {
        item { Spacer(modifier = Modifier.height(40.dp)) }

        items(uiState.chatMessages.size) { index ->
            val message = uiState.chatMessages[index]
            ChatMessageItem(
                role = message.role,
                content = message.content,
                audioPath = message.audioPath,
                onPlayAudio = onPlayAudio,
                fontSize = uiState.fontSize
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isLoading) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 4.dp
                )
            }
        }

        uiState.errorMessage?.let { error ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            val writeMessage = stringResource(R.string.write_message)
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val intent = RemoteInputHelper.createRemoteInputIntent(writeMessage)
                        launcher.launch(intent)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && !uiState.isRecording,
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.title2)
                }

                Button(
                    onClick = {
                        if (uiState.isRecording) {
                            onStopRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading,
                    colors = if (uiState.isRecording)
                        ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    else
                        ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                ) {
                    Text(
                        if (uiState.isRecording) stringResource(R.string.recording_stop) else stringResource(R.string.recording_start),
                        style = MaterialTheme.typography.title2
                    )
                }
            }
        }

        if (uiState.isRecording) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.recording_in_progress),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error
                )
            }
        }

        if (showTtsDialog && pendingText.isNotBlank()) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.choose_input_method),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showTtsDialog = false
                            onSendMessage(pendingText)
                            pendingText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.send_text_only), style = MaterialTheme.typography.caption1)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            showTtsDialog = false
                            onSynthesizeLocally(pendingText)
                            pendingText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(stringResource(R.string.synthesize_locally), style = MaterialTheme.typography.caption1)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            showTtsDialog = false
                            pendingText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.caption1)
                    }
                }
            }
        }

        uiState.pendingTranscription?.let { transcription ->
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.transcription),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        transcription,
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(36.dp)
            ) {
                Text(stringResource(R.string.back_arrow), style = MaterialTheme.typography.caption1)
            }

            Text(
                text = uiState.selectedModel.split("/").lastOrNull() ?: "",
                style = MaterialTheme.typography.caption2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        uiState.pendingTranscription?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onConfirmTranscription,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text("✓", style = MaterialTheme.typography.caption1)
                }

                Button(
                    onClick = onCancelTranscription,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error
                    )
                ) {
                    Text("✕", style = MaterialTheme.typography.caption1)
                }
            }
        }
    }
}

/**
 * Displays the analysis screen with chat and recording controls.
 *
 * @param uiState current chat UI state.
 * @param onSendMessage callback to send text messages.
 * @param onStartRecording callback to begin audio recording.
 * @param onStopRecording callback to stop audio recording.
 * @param onPlayAudio callback to play an audio message.
 * @param onIncreaseFontSize callback to increase message font size.
 * @param onDecreaseFontSize callback to decrease message font size.
 * @param onBack callback to return to the home screen.
 * @param onConfirmTranscription callback to accept a transcription.
 * @param onCancelTranscription callback to discard a transcription.
 * @param onUpdateTranscription callback to edit transcription text.
 * @return `Unit` after composing the screen.
 */
@Composable
fun AnalysisScreen(
    uiState: com.base.aihelperwearos.presentation.viewmodel.ChatUiState,
    onSendMessage: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onBack: () -> Unit,
    onConfirmTranscription: () -> Unit = {},
    onCancelTranscription: () -> Unit = {},
    onUpdateTranscription: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val microphonePermissionDenied = stringResource(R.string.microphone_permission_denied)
    
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(uiState.chatMessages.size, uiState.pendingTranscription) {
        if (uiState.chatMessages.isNotEmpty() || uiState.pendingTranscription != null) {
            coroutineScope.launch {
                val targetIndex = if (uiState.pendingTranscription != null) {
                    uiState.chatMessages.size + 4
                } else {
                    uiState.chatMessages.size + 1
                }
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
            }
        }
    }

    if (uiState.isRecording) {
        KeepScreenOn()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartRecording()
        } else {
            android.util.Log.e("AnalysisScreen", microphonePermissionDenied)
        }
    }

    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            RemoteInputHelper.getTextFromIntent(data)?.let { text ->
                if (text.isNotBlank()) {
                    if (uiState.pendingTranscription != null) {
                        onUpdateTranscription(text)
                    } else {
                        onSendMessage(text)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(40.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colors.secondary,
                            shape = CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.analysis_mode),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSecondary
                    )
                }
            }

            if (uiState.chatMessages.isEmpty() && !uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.use_keyboard_or_mic),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }

            items(uiState.chatMessages.size) { index ->
                val message = uiState.chatMessages[index]
                Spacer(modifier = Modifier.height(8.dp))
                ChatMessageItem(
                    role = message.role,
                    content = message.content,
                    audioPath = message.audioPath,
                    onPlayAudio = onPlayAudio,
                    fontSize = uiState.fontSize
                )
            }

            if (uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 4.dp
                    )
                }
            }

            uiState.errorMessage?.let { error ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val writeProblem = stringResource(R.string.write_problem)
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = RemoteInputHelper.createRemoteInputIntent(writeProblem)
                            textLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading && !uiState.isRecording,
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.title2)
                    }

                    Button(
                        onClick = {
                            if (uiState.isRecording) {
                                onStopRecording()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
                        colors = if (uiState.isRecording)
                            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                        else
                            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                    ) {
                        Text(
                            if (uiState.isRecording) stringResource(R.string.recording_stop) else stringResource(R.string.recording_start),
                            style = MaterialTheme.typography.title2
                        )
                    }
                }
            }

            if (uiState.isRecording) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.recording_in_progress),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error
                    )
                }
            }

            uiState.pendingTranscription?.let { transcription ->
                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .background(
                                color = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.transcription),
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            transcription,
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(36.dp)
            ) {
                Text(stringResource(R.string.back_arrow), style = MaterialTheme.typography.caption1)
            }
        }

        uiState.pendingTranscription?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onConfirmTranscription,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text("✓", style = MaterialTheme.typography.caption1)
                }

                Button(
                    onClick = onCancelTranscription,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error
                    )
                ) {
                    Text("✕", style = MaterialTheme.typography.caption1)
                }
            }
        }
    }
}

/**
 * Renders a single chat message bubble.
 *
 * @param role message role string.
 * @param content message text content.
 * @param audioPath optional audio file path for playback.
 * @param onPlayAudio callback invoked to play audio.
 * @param fontSize font size for message text.
 * @return `Unit` after composing the message row.
 */
@Composable
fun ChatMessageItem(
    role: String,
    content: String,
    audioPath: String? = null,
    onPlayAudio: (String) -> Unit = {},
    fontSize: Int = 11
) {
    val isUser = role == "user"
    val hasAudio = audioPath != null && File(audioPath).exists()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .background(
                    color = if (isUser)
                        MaterialTheme.colors.primary.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colors.surface

                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) stringResource(R.string.you) else stringResource(R.string.ai),
                    style = MaterialTheme.typography.caption2,
                    color = if (isUser)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.secondary
                )

                if (hasAudio) {
                    Button(
                        onClick = { onPlayAudio(audioPath) },
                        modifier = Modifier.size(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text(stringResource(R.string.play), style = MaterialTheme.typography.caption2)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!isUser) {
                MathMarkdownText(
                    markdown = content,
                    fontSize = fontSize
                )
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.body2,
                    fontSize = fontSize.sp
                )
            }
        }
    }
}

/**
 * Displays controls for adjusting chat font size.
 *
 * @param currentSize current font size value.
 * @param onIncrease callback to increase the font size.
 * @param onDecrease callback to decrease the font size.
 * @return `Unit` after composing the controls.
 */
@Composable
fun FontSizeControls(
    currentSize: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = onIncrease,
                modifier = Modifier.size(24.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.7f)
                ),
                enabled = currentSize < 16
            ) {
                Text(stringResource(R.string.increase_font_size), fontSize = 10.sp, color = MaterialTheme.colors.onSurface)
            }

            Text(
                text = "$currentSize",
                fontSize = 8.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )

            Button(
                onClick = onDecrease,
                modifier = Modifier.size(24.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.7f)
                ),
                enabled = currentSize > 8
            ) {
                Text(stringResource(R.string.decrease_font_size), fontSize = 10.sp, color = MaterialTheme.colors.onSurface)
            }
        }
    }
}

/**
 * Displays the chat history list.
 *
 * @param sessions list of stored chat sessions.
 * @param onSessionClick callback when a session is selected.
 * @param onDeleteSession callback to delete a session.
 * @param onBack callback to return to the home screen.
 * @return `Unit` after composing the history screen.
 */
@Composable
fun HistoryScreen(
    sessions: List<com.base.aihelperwearos.data.repository.ChatSession>,
    onSessionClick: (com.base.aihelperwearos.data.repository.ChatSession) -> Unit,
    onDeleteSession: (com.base.aihelperwearos.data.repository.ChatSession) -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(40.dp)
            ) {
                Text(stringResource(R.string.back_arrow))
            }
        }

        item {
            Text(
                stringResource(R.string.history),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        if (sessions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_saved_chats),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        } else {
            items(sessions.size) { index ->
                val session = sessions[index]
                val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(session.timestamp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(
                        onClick = { onSessionClick(session) },
                        label = {
                            Column {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    dateStr,
                                    style = MaterialTheme.typography.caption3,
                                    color = MaterialTheme.colors.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        secondaryLabel = {
                            if (session.isAnalysisMode) {
                                Text(stringResource(R.string.analysis), style = MaterialTheme.typography.caption3)
                            }
                        }
                    )

                    Button(
                        onClick = { onDeleteSession(session) },
                        modifier = Modifier.size(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Text(stringResource(R.string.delete), style = MaterialTheme.typography.caption1)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
