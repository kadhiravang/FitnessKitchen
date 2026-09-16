package com.kadhiravan.foodtracker.ui.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.prefs.ActivityLevel
import com.kadhiravan.foodtracker.data.prefs.NutritionGoal
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.prefs.Sex
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.ui.components.ProfilePicCrop
import com.kadhiravan.foodtracker.ui.components.ProfilePicturePicker
import com.kadhiravan.foodtracker.util.NutritionCalculator

private fun SecurePrefs.readProfilePicCrop(): ProfilePicCrop? =
    profilePicCropLeft.takeIf { it >= 0f }?.let { ProfilePicCrop(it, profilePicCropTop, profilePicCropSize) }

private fun SecurePrefs.writeProfilePicCrop(crop: ProfilePicCrop?) {
    if (crop != null) {
        profilePicCropLeft = crop.left
        profilePicCropTop = crop.top
        profilePicCropSize = crop.size
    } else {
        profilePicCropLeft = -1f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(securePrefs: SecurePrefs, weightRepository: WeightRepository, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf(securePrefs.name) }
    var profilePicPath by remember { mutableStateOf(securePrefs.profilePicPath) }
    var profilePicCrop by remember { mutableStateOf(securePrefs.readProfilePicCrop()) }
    var calorieGoal by remember { mutableStateOf(securePrefs.dailyCalorieGoal.takeIf { it > 0 }?.toString().orEmpty()) }
    var calorieBuffer by remember { mutableStateOf(securePrefs.calorieBufferKcal.toString()) }
    var targetWeight by remember { mutableStateOf(securePrefs.targetWeightKg.takeIf { it > 0f }?.toString().orEmpty()) }
    var age by remember { mutableStateOf(securePrefs.age.takeIf { it > 0 }?.toString().orEmpty()) }
    var heightCm by remember { mutableStateOf(securePrefs.heightCm.takeIf { it > 0f }?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(securePrefs.sex) }
    var activityLevel by remember { mutableStateOf(securePrefs.activityLevel) }
    var nutritionGoal by remember { mutableStateOf(securePrefs.nutritionGoal) }
    var useCustomMacros by remember { mutableStateOf(securePrefs.useCustomMacros) }
    var customProtein by remember { mutableStateOf(securePrefs.customProteinG.takeIf { it > 0 }?.toString().orEmpty()) }
    var customCarbs by remember { mutableStateOf(securePrefs.customCarbsG.takeIf { it > 0 }?.toString().orEmpty()) }
    var customFat by remember { mutableStateOf(securePrefs.customFatG.takeIf { it > 0 }?.toString().orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    val weightHistory by weightRepository.observeAll().collectAsState(initial = emptyList())

    // The same calorie goal the Diary tab will actually use — computed from the fields
    // currently on screen (not yet-saved securePrefs values) so the preview stays live
    // as you type, and validates the custom macro split against the real budget.
    val effectiveCalorieGoal = remember(weightHistory, age, heightCm, sex, activityLevel, nutritionGoal, calorieGoal) {
        val latestWeightKg = weightHistory.lastOrNull()?.weightKg
        val ageInt = age.toIntOrNull() ?: 0
        val heightFloat = heightCm.toFloatOrNull() ?: 0f
        val currentSex = sex
        if (ageInt > 0 && heightFloat > 0f && currentSex != null && latestWeightKg != null) {
            NutritionCalculator.calculate(
                age = ageInt,
                heightCm = heightFloat,
                weightKg = latestWeightKg,
                sex = currentSex,
                activityLevel = activityLevel,
                goal = nutritionGoal
            ).calorieGoal
        } else {
            calorieGoal.toIntOrNull() ?: 0
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("You") },
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
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfilePicturePicker(
                        picturePath = profilePicPath,
                        cropRect = profilePicCrop,
                        onPictureChanged = { profilePicPath = it; securePrefs.profilePicPath = it },
                        onCropChanged = { profilePicCrop = it; securePrefs.writeProfilePicCrop(it) },
                        size = 112.dp,
                        centerInParent = false
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; saved = false },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional — once age, height, and sex are set (plus a weigh-in on the Diary tab), your calorie and macro targets are calculated from those instead of the flat goal below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it; saved = false },
                            label = { Text("Age") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it; saved = false },
                            label = { Text("Height (cm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sex.entries.forEach { option ->
                            FilterChip(
                                selected = sex == option,
                                onClick = { sex = option; securePrefs.sex = option },
                                label = { Text(option.displayName) }
                            )
                        }
                    }
                    Text(
                        "Activity level",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityLevel.entries.forEach { option ->
                            FilterChip(
                                selected = activityLevel == option,
                                onClick = { activityLevel = option; securePrefs.activityLevel = option },
                                label = { Text(option.displayName) }
                            )
                        }
                    }
                    Text(
                        "Goal",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NutritionGoal.entries.forEach { option ->
                            FilterChip(
                                selected = nutritionGoal == option,
                                onClick = { nutritionGoal = option; securePrefs.nutritionGoal = option },
                                label = { Text(option.displayName) }
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Daily calorie goal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Used only until your profile above is complete — after that, your calculated target takes over automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = calorieGoal,
                        onValueChange = { calorieGoal = it; saved = false },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Optimal range — how far above or below your goal still counts as on target. Shown as two marker lines on the Diary gauge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = calorieBuffer,
                        onValueChange = { calorieBuffer = it; saved = false },
                        label = { Text("± kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Target weight", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional — powers the goal-progress card on the Diary tab (current weight, progress bar, time-to-goal estimate).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = targetWeight,
                        onValueChange = { targetWeight = it; saved = false },
                        label = { Text("kg") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom macros", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = useCustomMacros,
                            onCheckedChange = { useCustomMacros = it; saved = false }
                        )
                    }
                    Text(
                        "Override the calculated protein/carbs/fat split with your own numbers — must add up to no more than your calorie goal above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    if (useCustomMacros) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customProtein,
                                onValueChange = { customProtein = it; saved = false },
                                label = { Text("Protein (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customCarbs,
                                onValueChange = { customCarbs = it; saved = false },
                                label = { Text("Carbs (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customFat,
                                onValueChange = { customFat = it; saved = false },
                                label = { Text("Fat (g)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val customCalories = NutritionCalculator.caloriesFor(
                            customProtein.toIntOrNull() ?: 0,
                            customCarbs.toIntOrNull() ?: 0,
                            customFat.toIntOrNull() ?: 0
                        )
                        val overLimit = effectiveCalorieGoal > 0 && customCalories > effectiveCalorieGoal
                        Text(
                            if (effectiveCalorieGoal > 0) {
                                "= $customCalories kcal of $effectiveCalorieGoal kcal target"
                            } else {
                                "= $customCalories kcal (set a calorie goal above to check this against)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        if (overLimit) {
                            Text(
                                "Over your calorie goal — this won't be saved until it fits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    securePrefs.name = name.trim()
                    securePrefs.dailyCalorieGoal = calorieGoal.toIntOrNull() ?: 0
                    securePrefs.calorieBufferKcal = calorieBuffer.toIntOrNull()?.coerceAtLeast(0) ?: 100
                    securePrefs.targetWeightKg = targetWeight.toFloatOrNull() ?: 0f
                    securePrefs.age = age.toIntOrNull() ?: 0
                    securePrefs.heightCm = heightCm.toFloatOrNull() ?: 0f

                    val proteinInt = customProtein.toIntOrNull() ?: 0
                    val carbsInt = customCarbs.toIntOrNull() ?: 0
                    val fatInt = customFat.toIntOrNull() ?: 0
                    val customCalories = NutritionCalculator.caloriesFor(proteinInt, carbsInt, fatInt)
                    val fitsLimit = effectiveCalorieGoal <= 0 || customCalories <= effectiveCalorieGoal
                    if (useCustomMacros && fitsLimit) {
                        securePrefs.customProteinG = proteinInt
                        securePrefs.customCarbsG = carbsInt
                        securePrefs.customFatG = fatInt
                        securePrefs.useCustomMacros = true
                    } else {
                        securePrefs.useCustomMacros = false
                    }
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
}
