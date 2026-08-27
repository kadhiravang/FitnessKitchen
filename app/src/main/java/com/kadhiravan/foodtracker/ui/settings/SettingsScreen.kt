package com.kadhiravan.foodtracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(securePrefs: SecurePrefs, modifier: Modifier = Modifier) {
    var apiKey by remember { mutableStateOf(securePrefs.nvidiaApiKey) }
    var calorieGoal by remember { mutableStateOf(securePrefs.dailyCalorieGoal.takeIf { it > 0 }?.toString().orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "NVIDIA API key",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Used to turn your spoken transcript into a food list (deepseek-ai/deepseek-v4-flash-0731 via integrate.api.nvidia.com). Stored encrypted on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)
            )

            Text("Daily calorie goal (optional)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = calorieGoal,
                onValueChange = { calorieGoal = it; saved = false },
                label = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)
            )

            Button(onClick = {
                securePrefs.nvidiaApiKey = apiKey.trim()
                securePrefs.dailyCalorieGoal = calorieGoal.toIntOrNull() ?: 0
                saved = true
            }) {
                Text("Save")
            }

            if (saved) {
                Text(
                    "Saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
