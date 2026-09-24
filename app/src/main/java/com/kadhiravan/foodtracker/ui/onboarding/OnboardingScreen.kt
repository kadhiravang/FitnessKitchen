package com.kadhiravan.foodtracker.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.backup.BackupManager
import com.kadhiravan.foodtracker.data.prefs.ActivityLevel
import com.kadhiravan.foodtracker.data.prefs.ChatProvider
import com.kadhiravan.foodtracker.data.prefs.NutritionGoal
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.prefs.Sex
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.ui.components.ProfilePicCrop
import com.kadhiravan.foodtracker.ui.components.ProfilePicturePicker
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 4

/** First-launch setup wizard, collects the same profile fields the You/Settings tabs let
 * you edit later, so the app has real metadata (and a calculated calorie/macro target)
 * from the very first day instead of starting empty. Everything past the name is
 * skippable; anything already filled in is still saved when you skip. */
@Composable
fun OnboardingScreen(
    securePrefs: SecurePrefs,
    weightRepository: WeightRepository,
    backupManager: BackupManager,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf(securePrefs.name) }
    var profilePicPath by remember { mutableStateOf(securePrefs.profilePicPath) }
    var profilePicCrop by remember {
        mutableStateOf(
            securePrefs.profilePicCropLeft.takeIf { it >= 0f }
                ?.let { ProfilePicCrop(it, securePrefs.profilePicCropTop, securePrefs.profilePicCropSize) }
        )
    }

    var age by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf<Sex?>(null) }
    var weightKg by remember { mutableStateOf("") }
    var targetWeightKg by remember { mutableStateOf("") }

    var activityLevel by remember { mutableStateOf(ActivityLevel.MODERATE) }
    var nutritionGoal by remember { mutableStateOf(NutritionGoal.MAINTAIN) }

    var chatProvider by remember { mutableStateOf(securePrefs.chatProvider) }
    var geminiApiKey by remember { mutableStateOf(securePrefs.geminiApiKey) }
    var nvidiaApiKey by remember { mutableStateOf(securePrefs.nvidiaApiKey) }
    var ollamaServerUrl by remember { mutableStateOf(securePrefs.ollamaServerUrl) }
    var ollamaModel by remember { mutableStateOf(securePrefs.ollamaModel) }
    var ollamaCloudApiKey by remember { mutableStateOf(securePrefs.ollamaCloudApiKey) }
    var ollamaCloudModel by remember { mutableStateOf(securePrefs.ollamaCloudModel) }
    var claudeApiKey by remember { mutableStateOf(securePrefs.claudeApiKey) }
    var openaiApiKey by remember { mutableStateOf(securePrefs.openaiApiKey) }
    var openaiModel by remember { mutableStateOf(securePrefs.openaiModel) }

    val scope = rememberCoroutineScope()

    var restoreInProgress by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }
    var restoreDone by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            restoreInProgress = true
            restoreStatus = null
            scope.launch {
                try {
                    backupManager.restoreBackup(uri)
                    restoreDone = true
                    restoreStatus = "Restored. Close and reopen the app to continue with your data."
                } catch (e: Exception) {
                    restoreStatus = "Restore failed: ${e.message}"
                } finally {
                    restoreInProgress = false
                }
            }
        }
    }

    fun persistAndFinish() {
        securePrefs.name = name.trim()
        securePrefs.profilePicPath = profilePicPath
        val crop = profilePicCrop
        securePrefs.profilePicCropLeft = crop?.left ?: -1f
        if (crop != null) {
            securePrefs.profilePicCropTop = crop.top
            securePrefs.profilePicCropSize = crop.size
        }
        age.toIntOrNull()?.let { securePrefs.age = it }
        heightCm.toFloatOrNull()?.let { securePrefs.heightCm = it }
        sex?.let { securePrefs.sex = it }
        securePrefs.activityLevel = activityLevel
        securePrefs.nutritionGoal = nutritionGoal
        targetWeightKg.toFloatOrNull()?.let { securePrefs.targetWeightKg = it }
        securePrefs.chatProvider = chatProvider
        securePrefs.geminiApiKey = geminiApiKey.trim()
        securePrefs.nvidiaApiKey = nvidiaApiKey.trim()
        securePrefs.ollamaServerUrl = ollamaServerUrl.trim()
        securePrefs.ollamaModel = ollamaModel.trim()
        securePrefs.ollamaCloudApiKey = ollamaCloudApiKey.trim()
        securePrefs.ollamaCloudModel = ollamaCloudModel.trim()
        securePrefs.claudeApiKey = claudeApiKey.trim()
        securePrefs.openaiApiKey = openaiApiKey.trim()
        securePrefs.openaiModel = openaiModel.trim()
        securePrefs.onboardingComplete = true

        val weight = weightKg.toDoubleOrNull()
        if (weight != null && weight > 0) {
            scope.launch {
                weightRepository.logWeight(weight)
                onFinished()
            }
        } else {
            onFinished()
        }
    }

    val step1Valid = (age.toIntOrNull() ?: 0) > 0 &&
        (heightCm.toFloatOrNull() ?: 0f) > 0f &&
        sex != null &&
        (weightKg.toDoubleOrNull() ?: 0.0) > 0.0
    val nextEnabled = when (step) {
        0 -> name.isNotBlank()
        1 -> step1Valid
        else -> true
    }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LinearProgressIndicator(
                    progress = { (step + 1) / TOTAL_STEPS.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp, top = 6.dp)
                        .clip(RoundedCornerShape(50))
                )
                TextButton(onClick = { persistAndFinish() }) { Text("Skip") }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp)
            ) {
                when (step) {
                    0 -> WelcomeStep(
                        name = name,
                        onNameChange = { name = it },
                        profilePicPath = profilePicPath,
                        profilePicCrop = profilePicCrop,
                        onPictureChanged = { profilePicPath = it },
                        onCropChanged = { profilePicCrop = it }
                    )
                    1 -> AboutYouStep(
                        age = age, onAgeChange = { age = it },
                        heightCm = heightCm, onHeightChange = { heightCm = it },
                        sex = sex, onSexChange = { sex = it },
                        weightKg = weightKg, onWeightChange = { weightKg = it },
                        targetWeightKg = targetWeightKg, onTargetWeightChange = { targetWeightKg = it }
                    )
                    2 -> ActivityGoalStep(
                        activityLevel = activityLevel, onActivityChange = { activityLevel = it },
                        nutritionGoal = nutritionGoal, onGoalChange = { nutritionGoal = it }
                    )
                    3 -> ChatSetupStep(
                        chatProvider = chatProvider, onProviderChange = { chatProvider = it },
                        geminiApiKey = geminiApiKey, onGeminiKeyChange = { geminiApiKey = it },
                        nvidiaApiKey = nvidiaApiKey, onNvidiaKeyChange = { nvidiaApiKey = it },
                        ollamaServerUrl = ollamaServerUrl, onOllamaServerUrlChange = { ollamaServerUrl = it },
                        ollamaModel = ollamaModel, onOllamaModelChange = { ollamaModel = it },
                        ollamaCloudApiKey = ollamaCloudApiKey, onOllamaCloudKeyChange = { ollamaCloudApiKey = it },
                        ollamaCloudModel = ollamaCloudModel, onOllamaCloudModelChange = { ollamaCloudModel = it },
                        claudeApiKey = claudeApiKey, onClaudeKeyChange = { claudeApiKey = it },
                        openaiApiKey = openaiApiKey, onOpenaiKeyChange = { openaiApiKey = it },
                        openaiModel = openaiModel, onOpenaiModelChange = { openaiModel = it }
                    )
                }
                if (step == 0) {
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) },
                        enabled = !restoreInProgress && !restoreDone,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Text(if (restoreInProgress) "Restoring..." else "Restore from a backup instead")
                    }
                    restoreStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = { if (step == TOTAL_STEPS - 1) persistAndFinish() else step++ },
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (step == TOTAL_STEPS - 1) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    name: String,
    onNameChange: (String) -> Unit,
    profilePicPath: String,
    profilePicCrop: ProfilePicCrop?,
    onPictureChanged: (String) -> Unit,
    onCropChanged: (ProfilePicCrop?) -> Unit
) {
    Text("Welcome to FitnessKitchen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Let's set up your profile so your calorie and macro targets are right from day one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
    )
    ProfilePicturePicker(
        picturePath = profilePicPath,
        cropRect = profilePicCrop,
        onPictureChanged = onPictureChanged,
        onCropChanged = onCropChanged,
        modifier = Modifier.padding(bottom = 24.dp)
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AboutYouStep(
    age: String, onAgeChange: (String) -> Unit,
    heightCm: String, onHeightChange: (String) -> Unit,
    sex: Sex?, onSexChange: (Sex) -> Unit,
    weightKg: String, onWeightChange: (String) -> Unit,
    targetWeightKg: String, onTargetWeightChange: (String) -> Unit
) {
    Text("About you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Powers the calorie and macro calculator on the Diary tab.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = age,
            onValueChange = onAgeChange,
            label = { Text("Age") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = heightCm,
            onValueChange = onHeightChange,
            label = { Text("Height (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }
    Text("Sex", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { option ->
            FilterChip(
                selected = sex == option,
                onClick = { onSexChange(option) },
                label = { Text(option.displayName) }
            )
        }
    }
    Row(modifier = Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = weightKg,
            onValueChange = onWeightChange,
            label = { Text("Current weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = targetWeightKg,
            onValueChange = onTargetWeightChange,
            label = { Text("Target (kg, optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActivityGoalStep(
    activityLevel: ActivityLevel, onActivityChange: (ActivityLevel) -> Unit,
    nutritionGoal: NutritionGoal, onGoalChange: (NutritionGoal) -> Unit
) {
    Text("Activity & goal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "How active you are day to day, and what you're aiming for.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    )
    Text("Activity level", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActivityLevel.entries.forEach { option ->
            FilterChip(
                selected = activityLevel == option,
                onClick = { onActivityChange(option) },
                label = { Text(option.displayName) }
            )
        }
    }
    Text("Goal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutritionGoal.entries.forEach { option ->
            FilterChip(
                selected = nutritionGoal == option,
                onClick = { onGoalChange(option) },
                label = { Text(option.displayName) }
            )
        }
    }
}

@Composable
private fun ChatSetupStep(
    chatProvider: ChatProvider, onProviderChange: (ChatProvider) -> Unit,
    geminiApiKey: String, onGeminiKeyChange: (String) -> Unit,
    nvidiaApiKey: String, onNvidiaKeyChange: (String) -> Unit,
    ollamaServerUrl: String, onOllamaServerUrlChange: (String) -> Unit,
    ollamaModel: String, onOllamaModelChange: (String) -> Unit,
    ollamaCloudApiKey: String, onOllamaCloudKeyChange: (String) -> Unit,
    ollamaCloudModel: String, onOllamaCloudModelChange: (String) -> Unit,
    claudeApiKey: String, onClaudeKeyChange: (String) -> Unit,
    openaiApiKey: String, onOpenaiKeyChange: (String) -> Unit,
    openaiModel: String, onOpenaiModelChange: (String) -> Unit
) {
    Text("Chat assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Optional, powers the Chat tab's AI food logging. Skip this and add a key later in " +
            "Settings if you'd rather; manual entry on the Diary tab always works without one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        ChatProvider.entries.forEach { provider ->
            FilterChip(
                selected = chatProvider == provider,
                onClick = { onProviderChange(provider) },
                label = { Text(provider.displayName) }
            )
        }
    }
    when (chatProvider) {
        ChatProvider.GOOGLE -> {
            Text(
                "Gemini, free tier, no billing needed. Get a free key at aistudio.google.com/apikey.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
            )
            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = onGeminiKeyChange,
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
                onValueChange = onNvidiaKeyChange,
                label = { Text("NVIDIA API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ChatProvider.OLLAMA -> {
            Text(
                "Runs on your own computer, no API key. Install from ollama.com, then \"ollama pull\" whichever model you want to use. Enter that computer's Wi-Fi IP address below, not \"localhost\", that would mean this phone instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
            )
            OutlinedTextField(
                value = ollamaServerUrl,
                onValueChange = onOllamaServerUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("http://10.0.0.250:11434") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ollamaModel,
                onValueChange = onOllamaModelChange,
                label = { Text("Model") },
                placeholder = { Text("llama3.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        ChatProvider.OLLAMA_CLOUD -> {
            Text(
                "Ollama's hosted inference, run much larger models than a local machine could handle. Get a key at ollama.com/settings/keys.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
            )
            OutlinedTextField(
                value = ollamaCloudApiKey,
                onValueChange = onOllamaCloudKeyChange,
                label = { Text("Ollama Cloud API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ollamaCloudModel,
                onValueChange = onOllamaCloudModelChange,
                label = { Text("Model") },
                placeholder = { Text("gpt-oss:120b-cloud") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        ChatProvider.CLAUDE -> {
            Text(
                "Get a key at console.anthropic.com.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
            )
            OutlinedTextField(
                value = claudeApiKey,
                onValueChange = onClaudeKeyChange,
                label = { Text("Claude API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ChatProvider.OPENAI -> {
            Text(
                "Get a key at platform.openai.com/api-keys.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
            )
            OutlinedTextField(
                value = openaiApiKey,
                onValueChange = onOpenaiKeyChange,
                label = { Text("OpenAI API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = openaiModel,
                onValueChange = onOpenaiModelChange,
                label = { Text("Model") },
                placeholder = { Text("gpt-4.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }
}
