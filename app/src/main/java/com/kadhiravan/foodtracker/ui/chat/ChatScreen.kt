package com.kadhiravan.foodtracker.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.util.Log
import com.kadhiravan.foodtracker.FoodTrackerApp
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.CloudWhisperClient
import com.kadhiravan.foodtracker.data.remote.LocalWhisperClient
import com.kadhiravan.foodtracker.data.remote.LogCardParser
import com.kadhiravan.foodtracker.ui.voice.AudioRecorder
import com.kadhiravan.foodtracker.ui.voice.SpeechRecognizerController
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, securePrefs: SecurePrefs, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val streak by viewModel.streak.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var useWhisperPath by remember { mutableStateOf(false) }
    var useOnDevicePath by remember { mutableStateOf(false) }
    var micAmplitude by remember { mutableFloatStateOf(0f) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }

    val coroutineScope = rememberCoroutineScope()
    val whisperClient = remember { LocalWhisperClient() }
    val cloudWhisperClient = remember { CloudWhisperClient() }
    val onDeviceWhisper = remember { (context.applicationContext as FoodTrackerApp).onDeviceWhisper }

    // Load the model in the background as soon as the chat opens so the first mic tap
    // doesn't wait on it.
    LaunchedEffect(securePrefs.useOnDeviceWhisper, securePrefs.onDeviceWhisperModel) {
        if (securePrefs.useOnDeviceWhisper && onDeviceWhisper.isModelAvailable(securePrefs.onDeviceWhisperModel)) {
            onDeviceWhisper.warmUp(securePrefs.onDeviceWhisperModel)
        }
    }

    val controller = remember {
        SpeechRecognizerController(
            context = context,
            onTranscriptUpdate = { inputText = it },
            onAmplitudeChanged = { micAmplitude = it },
            onError = { isListening = false },
            onListeningStopped = { finalText ->
                isListening = false
                inputText = finalText
            }
        )
    }
    val audioRecorder = remember { AudioRecorder(onAmplitude = { micAmplitude = it }) }
    DisposableEffect(Unit) {
        onDispose {
            controller.destroy()
            if (isListening && useWhisperPath) audioRecorder.cancel()
        }
    }

    fun startListening() {
        inputText = ""
        isListening = true
        coroutineScope.launch {
            useOnDevicePath = securePrefs.useOnDeviceWhisper &&
                onDeviceWhisper.isModelAvailable(securePrefs.onDeviceWhisperModel)
            useWhisperPath = if (useOnDevicePath) {
                true
            } else if (securePrefs.useCloudWhisper && securePrefs.whisperApiKey.isNotBlank()) {
                true
            } else {
                whisperClient.isReachable(securePrefs.whisperServerUrl)
            }
            if (useWhisperPath) {
                audioRecorder.start()
            } else {
                controller.startListening(securePrefs.recognitionLanguage)
            }
        }
    }

    fun stopListening() {
        if (useWhisperPath) {
            isListening = false
            isTranscribing = true
            val wav = audioRecorder.stopAndGetWav()
            val useCloud = securePrefs.useCloudWhisper && securePrefs.whisperApiKey.isNotBlank()
            coroutineScope.launch {
                try {
                    inputText = if (useOnDevicePath) {
                        val model = securePrefs.onDeviceWhisperModel
                        onDeviceWhisper.samplesDirIfPresent()?.let { dir ->
                            runCatching { java.io.File(dir, "${System.currentTimeMillis()}.wav").writeBytes(wav) }
                        }
                        val result = onDeviceWhisper.transcribe(wav, model)
                        Log.d(
                            "OnDeviceWhisper",
                            "model=$model audio=${"%.1f".format(result.audioSeconds)}s load=${result.loadMs}ms " +
                                "decode=${result.decodeMs}ms text=${result.text}"
                        )
                        result.text
                    } else if (useCloud) {
                        cloudWhisperClient.transcribe(securePrefs.whisperApiKey, wav, securePrefs.recognitionLanguage)
                    } else {
                        whisperClient.transcribe(securePrefs.whisperServerUrl, wav)
                    }
                } catch (e: Exception) {
                    // Best-effort, the server was reachable moments ago but the actual
                    // transcription call failed; nothing more useful to do than drop it.
                } finally {
                    isTranscribing = false
                }
            }
        } else {
            controller.stopListening()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty() || isSending) {
            listState.animateScrollToItem((messages.size - 1 + if (isSending) 1 else 0).coerceAtLeast(0))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatHeader(
                date = currentDate,
                onPreviousDay = viewModel::goToPreviousDay,
                onNextDay = viewModel::goToNextDay,
                onDateSelected = viewModel::selectDate,
                streak = streak
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                isListening = isListening,
                isTranscribing = isTranscribing,
                useWhisperPath = useWhisperPath,
                micAmplitude = micAmplitude,
                micAvailable = controller.isAvailable,
                onMicTap = {
                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (isListening) {
                        stopListening()
                    } else {
                        startListening()
                    }
                },
                onSend = {
                    if (isListening) {
                        stopListening()
                    } else if (inputText.isNotBlank()) {
                        viewModel.sendText(inputText)
                        inputText = ""
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyState() }
            }
            itemsIndexed(messages, key = { _, m -> m.id }) { _, message ->
                MessageWithCard(
                    message = message,
                    viewModel = viewModel,
                    modifier = Modifier.animateItemPlacement()
                )
            }
            if (isSending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TypingIndicator()
                        TextButton(onClick = { viewModel.cancelSending() }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageWithCard(
    message: ChatMessage,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val isUser = roleIsUser(message.role)
    val displayText = if (isUser) message.content else LogCardParser.textWithoutCard(message.content)
    val card = if (!isUser) LogCardParser.parse(message.content) else null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChatBubble(
            isUser = isUser,
            text = displayText,
            onRetry = if (isUser) { { viewModel.sendText(message.content) } } else null
        )
        if (card != null) {
            FoodLogCard(
                card = card,
                status = message.cardStatus,
                onConfirm = { mealGroups -> viewModel.confirmCard(message, mealGroups) },
                onDismiss = { viewModel.dismissCard(message) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHeader(
    date: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateSelected: (String) -> Unit,
    streak: Int,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) { append("Fitness") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Kitchen") }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            if (streak > 0) {
                StreakBadge(streak)
            }
        }
        DateNavRow(
            date = date,
            onPreviousDay = onPreviousDay,
            onNextDay = onNextDay,
            onOpenDatePicker = { showDatePicker = true },
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = DateUtils.isoToEpochMillisUtc(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(DateUtils.epochMillisUtcToIso(it)) }
                    showDatePicker = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            Icons.Default.LocalFireDepartment,
            contentDescription = "Logging streak",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "$streak",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun DateNavRow(
    date: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPreviousDay, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            if (date == DateUtils.today()) "Today" else DateUtils.isoToDisplay(date),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onNextDay, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenDatePicker, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Jump to date",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Tell me what you ate",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Type or tap the mic, e.g. \"two idlis and a dosa with sambar\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    isTranscribing: Boolean,
    useWhisperPath: Boolean,
    micAmplitude: Float,
    micAvailable: Boolean,
    onMicTap: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth().imePadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !isTranscribing,
                    placeholder = {
                        val engine = if (useWhisperPath) "Whisper" else "device mic"
                        Text(
                            when {
                                isTranscribing -> "Transcribing ($engine)…"
                                isListening -> "Listening ($engine)…"
                                else -> "Message FitnessKitchen…"
                            }
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                if (micAvailable) {
                    VoiceInputButton(isListening = isListening, amplitude = micAmplitude, onClick = onMicTap)
                }
                FilledTonalIconButton(
                    onClick = onSend,
                    enabled = !isTranscribing && (text.isNotBlank() || isListening)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
