package com.kadhiravan.foodtracker.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.backup.BackupManager
import com.kadhiravan.foodtracker.data.prefs.ChatProvider
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.prefs.SpeechLanguage
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(securePrefs: SecurePrefs, backupManager: BackupManager, modifier: Modifier = Modifier) {
    var chatProvider by remember { mutableStateOf(securePrefs.chatProvider) }
    var geminiApiKey by remember { mutableStateOf(securePrefs.geminiApiKey) }
    var nvidiaApiKey by remember { mutableStateOf(securePrefs.nvidiaApiKey) }
    var recognitionLanguage by remember { mutableStateOf(securePrefs.recognitionLanguage) }
    var whisperServerUrl by remember { mutableStateOf(securePrefs.whisperServerUrl) }
    var saved by remember { mutableStateOf(false) }

    var backupIncludePhotos by remember { mutableStateOf(securePrefs.backupIncludePhotos) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            backupInProgress = true
            backupStatus = null
            scope.launch {
                try {
                    backupManager.createBackup(uri, backupIncludePhotos)
                    backupStatus = "Backup saved."
                } catch (e: Exception) {
                    backupStatus = "Backup failed: ${e.message}"
                } finally {
                    backupInProgress = false
                }
            }
        }
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) showRestoreConfirm = uri
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Chat model", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Which AI powers the Chat tab's nutrition conversation. Both need their own API key below — stored encrypted on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChatProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = chatProvider == provider,
                                onClick = {
                                    chatProvider = provider
                                    securePrefs.chatProvider = provider
                                },
                                label = { Text(provider.displayName) }
                            )
                        }
                    }

                    when (chatProvider) {
                        ChatProvider.GOOGLE -> {
                            Text(
                                "Gemini 2.5 Flash — free tier, no billing needed. Get a free key at aistudio.google.com/apikey.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                            )
                            OutlinedTextField(
                                value = geminiApiKey,
                                onValueChange = { geminiApiKey = it; saved = false },
                                label = { Text("Gemini API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ChatProvider.NVIDIA -> {
                            Text(
                                "deepseek-ai/deepseek-v4-flash-0731 via integrate.api.nvidia.com.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                            )
                            OutlinedTextField(
                                value = nvidiaApiKey,
                                onValueChange = { nvidiaApiKey = it; saved = false },
                                label = { Text("NVIDIA API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Voice recognition language", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "On Android 14+, the mic already auto-switches between English and Tamil mid-sentence — this sets which one it defaults to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeechLanguage.options.forEach { (tag, label) ->
                            FilterChip(
                                selected = recognitionLanguage == tag,
                                onClick = {
                                    recognitionLanguage = tag
                                    securePrefs.recognitionLanguage = tag
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Local Whisper server", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional — for much higher-accuracy transcription than the on-device mic, run the Whisper server on your laptop (see whisper-server/ setup) and enter its address. Only reachable on the same Wi-Fi with the server running; the app falls back to the on-device recognizer automatically otherwise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = whisperServerUrl,
                        onValueChange = { whisperServerUrl = it; saved = false },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://10.0.0.250:8765") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Save everything — meals, chat history, weigh-ins, and your profile — to a zip file you keep, and restore it later or on a new device. The file includes your API keys, so keep it somewhere private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include progress photos", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = backupIncludePhotos,
                            onCheckedChange = {
                                backupIncludePhotos = it
                                securePrefs.backupIncludePhotos = it
                            }
                        )
                    }
                    Text(
                        "Off keeps the backup small by skipping photo files — everything else is still included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val fileName = "fitnesskitchen_backup_${DateUtils.today()}.zip"
                                createBackupLauncher.launch(fileName)
                            },
                            enabled = !backupInProgress && !restoreInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create Backup")
                        }
                        OutlinedButton(
                            onClick = { openBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
                            enabled = !backupInProgress && !restoreInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore")
                        }
                    }
                    if (backupInProgress || restoreInProgress) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                if (backupInProgress) "Backing up…" else "Restoring…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (backupStatus != null) {
                        Text(
                            backupStatus.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    securePrefs.geminiApiKey = geminiApiKey.trim()
                    securePrefs.nvidiaApiKey = nvidiaApiKey.trim()
                    securePrefs.whisperServerUrl = whisperServerUrl.trim()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            AnimatedVisibility(visible = saved) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(0.dp)
                    )
                    Text(
                        "Saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    val pendingRestoreUri = showRestoreConfirm
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces all current data on this device — meals, chat history, weigh-ins, photos, and settings. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingRestoreUri
                    showRestoreConfirm = null
                    restoreInProgress = true
                    backupStatus = null
                    scope.launch {
                        try {
                            backupManager.restoreBackup(uri)
                            backupStatus = "Restored. Close and reopen the app to see everything."
                        } catch (e: Exception) {
                            backupStatus = "Restore failed: ${e.message}"
                        } finally {
                            restoreInProgress = false
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") }
            }
        )
    }
}
