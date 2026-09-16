package com.kadhiravan.foodtracker.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.ui.components.MealSection
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    securePrefs: SecurePrefs,
    onOpenProgressGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val selectedDayPhoto by viewModel.selectedDayPhoto.collectAsState()

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoFile?.let { file -> viewModel.saveProgressPhoto(file.absolutePath) }
        }
        pendingPhotoFile = null
    }
    fun launchCamera() {
        val photosDir = File(context.filesDir, "progress_photos").apply { mkdirs() }
        val file = File(photosDir, "progress_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val photosDir = File(context.filesDir, "progress_photos").apply { mkdirs() }
            val file = File(photosDir, "progress_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.saveProgressPhoto(file.absolutePath)
        }
    }

    var showManualEntryDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Diary has no TopAppBar of its own to consume the status bar inset (unlike the
        // other tabs), so it needs to ask for it explicitly now that the outer nav Scaffold
        // no longer applies it automatically.
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            FloatingActionButton(onClick = { showManualEntryDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add food manually")
            }
        }
    ) { padding ->
        val mealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        val nonEmptyMeals = mealOrder.filter { uiState.entriesByMeal[it]?.isNotEmpty() == true }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WeekDayPicker(
                    days = weeklyTrend,
                    selectedDate = uiState.date,
                    calorieGoal = uiState.calorieGoal,
                    calorieBufferKcal = uiState.calorieBufferKcal,
                    onSelectDate = viewModel::selectDate,
                    onPreviousWeek = viewModel::goToPreviousWeek,
                    onNextWeek = viewModel::goToNextWeek,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item {
                DiarySummaryCard(
                    totalCalories = uiState.totalCalories,
                    calorieGoal = uiState.calorieGoal,
                    calorieBufferKcal = uiState.calorieBufferKcal,
                    proteinG = uiState.totalProteinG,
                    carbsG = uiState.totalCarbsG,
                    fatG = uiState.totalFatG,
                    proteinGoalG = uiState.proteinGoalG,
                    carbsGoalG = uiState.carbsGoalG,
                    fatGoalG = uiState.fatGoalG
                )
            }

            item {
                WeightProgressCard(
                    history = weightHistory,
                    targetWeightKg = securePrefs.targetWeightKg,
                    onLogWeight = viewModel::logWeight
                )
            }

            item {
                ProgressPhotoCard(
                    photo = selectedDayPhoto,
                    onTakePhoto = { launchCamera() },
                    onPickFromGallery = {
                        pickImageLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onDelete = viewModel::deleteProgressPhoto,
                    onOpenGallery = onOpenProgressGallery
                )
            }

            if (nonEmptyMeals.isEmpty()) {
                item {
                    Text(
                        "Nothing logged yet — head to Chat and tell me what you ate, or tap + to add it yourself.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)
                    )
                }
            } else {
                items(nonEmptyMeals) { meal ->
                    MealSection(
                        mealType = meal,
                        entries = uiState.entriesByMeal[meal].orEmpty(),
                        onDeleteEntry = viewModel::deleteEntry
                    )
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showManualEntryDialog) {
        ManualEntryDialog(
            onDismiss = { showManualEntryDialog = false },
            onSave = { name, quantity, unit, calories, proteinG, carbsG, fatG, mealType ->
                viewModel.addManualEntry(name, quantity, unit, calories, proteinG, carbsG, fatG, mealType)
                showManualEntryDialog = false
            }
        )
    }
}
