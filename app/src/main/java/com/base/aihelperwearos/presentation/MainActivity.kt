package com.base.aihelperwearos.presentation

import android.Manifest
import android.os.Bundle
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
import com.base.aihelperwearos.presentation.utils.AudioRecorder
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            val viewModel: MainViewModel = viewModel()
            WearApp(viewModel, this)
        }
    }
}

fun setLocale(activity: ComponentActivity, languageCode: String) {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val resources = activity.resources
    val config = resources.configuration
    config.setLocale(locale)
    resources.updateConfiguration(config, resources.displayMetrics)
}

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
            when (uiState.currentScreen) {
                Screen.Home -> HomeScreen(
                    onNewChat = {
                        android.util.Log.d("MainActivity", "Nuova Chat clicked")
                        viewModel.navigateTo(Screen.ModelSelection)
                    },
                    onAnalysis = {
                        android.util.Log.d("MainActivity", "Modalità Analisi clicked")
                        viewModel.selectModel("anthropic/claude-sonnet-4.5")
                        viewModel.startNewChat(isAnalysisMode = true)
                    },
                    onHistory = {
                        android.util.Log.d("MainActivity", "Cronologia clicked")
                        viewModel.navigateTo(Screen.History)
                    },
                    onLanguageChange = { action ->
                        setLocale(activity, action)
                        activity.recreate()
                    }
                )
                Screen.ModelSelection -> ModelSelectionScreen(
                    selectedModel = uiState.selectedModel,
                    onModelSelected = { model ->
                        viewModel.selectModel(model)
                        viewModel.startNewChat(isAnalysisMode = false)
                    },
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
                Screen.Chat -> ChatScreen(
                    uiState = uiState,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onSendAudio = { audioFile -> viewModel.sendAudioMessage(audioFile) },
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
                    onSendAudio = { audioFile -> viewModel.sendAudioMessage(audioFile) },
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

@Composable
fun HomeScreen(
    onNewChat: () -> Unit,
    onAnalysis: () -> Unit,
    onHistory: () -> Unit,
    onLanguageChange: (String) -> Unit
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

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
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

@Composable
fun ModelSelectionScreen(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val models = listOf(
        "google/gemini-2.5-pro" to stringResource(R.string.model_gemini_pro),
        "anthropic/claude-sonnet-4.5" to stringResource(R.string.model_claude_sonnet),
        "openai/gpt-5" to stringResource(R.string.model_gpt5)
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
                stringResource(R.string.choose_model),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

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
    }
}

@Composable
fun ChatScreen(
    uiState: com.base.aihelperwearos.presentation.viewmodel.ChatUiState,
    onSendMessage: (String) -> Unit,
    onSendAudio: (File) -> Unit,
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
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf("") }

    val microphonePermissionDenied = stringResource(R.string.microphone_permission_denied)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
            audioRecorder = AudioRecorder(context)
            coroutineScope.launch {
                audioRecorder?.startRecording()
            }
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
            horizontalAlignment = Alignment.Start
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

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
                    enabled = !uiState.isLoading && !isRecording,
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.title2)
                }

                Button(
                    onClick = {
                        if (isRecording) {
                            isRecording = false
                            coroutineScope.launch {
                                audioRecorder?.stopRecording()?.fold(
                                    onSuccess = { file -> onSendAudio(file) },
                                    onFailure = { }
                                )
                                audioRecorder = null
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading,
                    colors = if (isRecording)
                        ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    else
                        ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                ) {
                    Text(
                        if (isRecording) stringResource(R.string.recording_stop) else stringResource(R.string.recording_start),
                        style = MaterialTheme.typography.title2
                    )
                }
            }
        }

        if (isRecording) {
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

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                val editText = stringResource(R.string.edit_transcription)
                Row(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onCancelTranscription,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.title3)
                    }

                    Button(
                        onClick = {
                            val intent = RemoteInputHelper.createRemoteInputIntent(editText)
                            launcher.launch(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(stringResource(R.string.edit), style = MaterialTheme.typography.title3)
                    }

                    Button(
                        onClick = onConfirmTranscription,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.confirm), style = MaterialTheme.typography.title3)
                    }
                }
            }
        }
    }

        /*FontSizeControls(
            currentSize = uiState.fontSize,
            onIncrease = onIncreaseFontSize,
            onDecrease = onDecreaseFontSize
        )*/
    }
}

@Composable
fun AnalysisScreen(
    uiState: com.base.aihelperwearos.presentation.viewmodel.ChatUiState,
    onSendMessage: (String) -> Unit,
    onSendAudio: (File) -> Unit,
    onPlayAudio: (String) -> Unit,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onBack: () -> Unit,
    onConfirmTranscription: () -> Unit = {},
    onCancelTranscription: () -> Unit = {},
    onUpdateTranscription: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<AudioRecorder?>(null) }
    val microphonePermissionDenied = stringResource(R.string.microphone_permission_denied)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
            audioRecorder = AudioRecorder(context)
            coroutineScope.launch {
                audioRecorder?.startRecording()
            }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
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
                        enabled = !uiState.isLoading && !isRecording,
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.title2)
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                isRecording = false
                                android.util.Log.d("AnalysisScreen", "Stopping AudioRecorder")
                                coroutineScope.launch {
                                    audioRecorder?.stopRecording()?.fold(
                                        onSuccess = { file -> onSendAudio(file) },
                                        onFailure = { }
                                    )
                                    audioRecorder = null
                                }
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
                        colors = if (isRecording)
                            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                        else
                            ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                    ) {
                        Text(
                            if (isRecording) stringResource(R.string.recording_stop) else stringResource(R.string.recording_start),
                            style = MaterialTheme.typography.title2
                        )
                    }
                }
            }

            if (isRecording) {
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

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    val editTranscription = stringResource(R.string.edit_transcription)
                    Row(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onCancelTranscription,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.error
                            )
                        ) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.title3)
                        }

                        Button(
                            onClick = {
                                val intent = RemoteInputHelper.createRemoteInputIntent(
                                    label = editTranscription
                                )
                                textLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text(stringResource(R.string.edit), style = MaterialTheme.typography.title3)
                        }

                        Button(
                            onClick = onConfirmTranscription,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.primaryButtonColors()
                        ) {
                            Text(stringResource(R.string.confirm), style = MaterialTheme.typography.title3)
                        }
                    }
                }
            }
        }

        /*FontSizeControls(
            currentSize = uiState.fontSize,
            onIncrease = onIncreaseFontSize,
            onDecrease = onDecreaseFontSize
        )*/
    }
}

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

