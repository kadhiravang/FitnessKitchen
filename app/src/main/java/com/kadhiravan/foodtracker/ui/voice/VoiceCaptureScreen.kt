package com.kadhiravan.foodtracker.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.ui.components.EditableFoodRow
import com.kadhiravan.foodtracker.ui.components.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureScreen(
    viewModel: VoiceCaptureViewModel,
    date: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (!granted) viewModel.onSpeechError("Microphone permission was denied.")
    }

    val controller = remember {
        SpeechRecognizerController(
            context = context,
            onPartialResult = viewModel::onPartialTranscript,
            onFinalResult = viewModel::onFinalTranscript,
            onError = viewModel::onSpeechError,
            onListeningStopped = {}
        )
    }

    DisposableEffect(Unit) {
        onDispose { controller.destroy() }
    }

    LaunchedEffect(uiState) {
        if (uiState is VoiceUiState.Saved) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Log by voice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is VoiceUiState.Idle -> IdleContent(
                    micAvailable = controller.isAvailable,
                    onMicTap = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.onListeningStarted()
                            controller.startListening()
                        }
                    }
                )

                is VoiceUiState.Listening -> ListeningContent(
                    partialTranscript = state.partialTranscript,
                    onStop = { controller.stopListening() }
                )

                is VoiceUiState.Processing -> ProcessingContent(transcript = state.transcript)

                is VoiceUiState.Confirming -> ConfirmingContent(
                    state = state,
                    onChangeItem = viewModel::updateItem,
                    onRemoveItem = viewModel::removeItem,
                    onAddItem = viewModel::addBlankItem,
                    onMealTypeChange = viewModel::setMealType,
                    onCancel = { viewModel.reset() },
                    onConfirm = { viewModel.confirmAndSave(date) }
                )

                is VoiceUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.reset() }
                )

                is VoiceUiState.Saved -> Unit
            }
        }
    }
}

@Composable
private fun IdleContent(micAvailable: Boolean, onMicTap: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!micAvailable) {
            Text(
                "Speech recognition isn't available on this device.",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            FilledIconButton(
                onClick = onMicTap,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Start listening", modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Tap and say what you ate,\ne.g. \"two idlis and a dosa with sambar\"",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ListeningContent(partialTranscript: String, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FilledIconButton(
            onClick = onStop,
            modifier = Modifier.size(96.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Stop listening", modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Listening… tap to stop", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            partialTranscript.ifBlank { "…" },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ProcessingContent(transcript: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("\"$transcript\"", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Working out the calories…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmingContent(
    state: VoiceUiState.Confirming,
    onChangeItem: (String, EditableFoodEntry) -> Unit,
    onRemoveItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        var mealMenuExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = mealMenuExpanded,
            onExpandedChange = { mealMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = state.mealType.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Meal") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = mealMenuExpanded,
                onDismissRequest = { mealMenuExpanded = false }
            ) {
                MealType.entries.forEach { meal ->
                    DropdownMenuItem(
                        text = { Text(meal.displayName()) },
                        onClick = {
                            onMealTypeChange(meal)
                            mealMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val totalCalories = state.items.sumOf { it.calories.toIntOrNull() ?: 0 }
        Text(
            "Total: $totalCalories kcal",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.localId }) { item ->
                EditableFoodRow(
                    entry = item,
                    onChange = { onChangeItem(item.localId, it) },
                    onRemove = { onRemoveItem(item.localId) }
                )
            }
            item {
                TextButton(onClick = onAddItem) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add item")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Discard") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f), enabled = state.items.isNotEmpty()) {
                Text("Save")
            }
        }
    }
}
