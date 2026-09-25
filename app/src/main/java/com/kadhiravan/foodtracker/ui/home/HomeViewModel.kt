package com.kadhiravan.foodtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.local.DailyTotal
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.local.ProgressPhoto
import com.kadhiravan.foodtracker.data.local.WeightEntry
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.ProgressPhotoRepository
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.util.DateUtils
import com.kadhiravan.foodtracker.util.NutritionCalculator
import com.kadhiravan.foodtracker.util.NutritionTargets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val date: String = DateUtils.today(),
    val entriesByMeal: Map<MealType, List<LogEntry>> = emptyMap(),
    val totalCalories: Int = 0,
    val calorieGoal: Int = 0,
    val calorieBufferKcal: Int = 100,
    val proteinGoalG: Int = 0,
    val carbsGoalG: Int = 0,
    val fatGoalG: Int = 0,
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val logRepository: LogRepository,
    private val securePrefs: SecurePrefs,
    private val weightRepository: WeightRepository,
    private val progressPhotoRepository: ProgressPhotoRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(DateUtils.today())

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDate.flatMapLatest { date -> logRepository.observeForDate(date).map { date to it } },
        weightRepository.observeAll(),
        securePrefs.changes.onStart { emit(Unit) }
    ) { (date, entries), weightHistory, _ ->
        val targets = computeTargets(weightHistory.lastOrNull()?.weightKg)
        HomeUiState(
            date = date,
            entriesByMeal = entries.groupBy { it.mealType },
            totalCalories = entries.sumOf { it.calories },
            calorieGoal = targets.calorieGoal,
            calorieBufferKcal = securePrefs.calorieBufferKcal,
            proteinGoalG = targets.proteinG,
            carbsGoalG = targets.carbsG,
            fatGoalG = targets.fatG,
            totalProteinG = entries.sumOf { it.proteinG },
            totalCarbsG = entries.sumOf { it.carbsG },
            totalFatG = entries.sumOf { it.fatG }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Uses the full BMR-based calculator once age/height/sex/weight are all known;
     * otherwise falls back to a flat macro split of the manually-set calorie goal. A
     * user-set custom macro split (see [SecurePrefs.useCustomMacros]) overrides whichever
     * of those produced the split, as long as it still fits the calorie goal. */
    private fun computeTargets(latestWeightKg: Double?): NutritionTargets {
        val sex = securePrefs.sex
        val base = if (securePrefs.hasProfileBasics() && sex != null && latestWeightKg != null) {
            NutritionCalculator.calculate(
                age = securePrefs.age,
                heightCm = securePrefs.heightCm,
                weightKg = latestWeightKg,
                sex = sex,
                activityLevel = securePrefs.activityLevel,
                goal = securePrefs.nutritionGoal
            )
        } else {
            NutritionCalculator.fromCalorieGoalOnly(securePrefs.dailyCalorieGoal)
        }
        val withGoal = NutritionCalculator.withManualGoal(base, securePrefs.dailyCalorieGoal)
        return if (securePrefs.useCustomMacros) {
            NutritionCalculator.applyCustomMacros(withGoal, securePrefs.customProteinG, securePrefs.customCarbsG, securePrefs.customFatG)
        } else {
            withGoal
        }
    }

    private val weekOffset = MutableStateFlow(0)

    /** Sun-Sat week for the current [weekOffset], gap-filled so days with no entries show as
     * zero, powers the week-dot day picker at the top of the Diary tab. Offset 0 is the
     * week containing today; negative/positive page backward/forward through other weeks. */
    val weeklyTrend: StateFlow<List<DailyTotal>> = weekOffset
        .flatMapLatest { offset ->
            val weekStart = DateUtils.offsetDate(DateUtils.startOfWeek(DateUtils.today()), offset * 7)
            val weekEnd = DateUtils.offsetDate(weekStart, 6)
            logRepository.observeDailyTotalsBetween(weekStart, weekEnd).map { totals ->
                val byDate = totals.associateBy { it.date }
                (0..6).map { i ->
                    val date = DateUtils.offsetDate(weekStart, i)
                    byDate[date] ?: DailyTotal(date, 0, 0.0, 0.0, 0.0)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun goToPreviousWeek() {
        weekOffset.value -= 1
    }

    fun goToNextWeek() {
        weekOffset.value += 1
    }

    fun selectDate(date: String) {
        selectedDate.value = date
        // Keep the week picker's visible week in sync, jumping here from the History
        // calendar (or anywhere else) used to leave the old week on screen, with no dot
        // highlighted for the day you actually landed on.
        weekOffset.value = DateUtils.daysBetween(DateUtils.startOfWeek(DateUtils.today()), DateUtils.startOfWeek(date)) / 7
    }

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.delete(entry) }
    }

    fun updateEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.update(entry) }
    }

    /** Direct add, bypassing the Chat tab's AI parsing entirely, a fallback for when the
     * chat model's rate limit is exhausted (or you just don't want to type it out). */
    fun addManualEntry(
        name: String,
        quantity: Double,
        unit: String,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealType: MealType
    ) {
        val entry = LogEntry(
            foodName = name,
            quantity = quantity,
            unit = unit,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            mealType = mealType,
            logDate = uiState.value.date
        )
        viewModelScope.launch { logRepository.addEntries(listOf(entry)) }
    }

    val weightHistory: StateFlow<List<WeightEntry>> = weightRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logWeight(weightKg: Double) {
        viewModelScope.launch { weightRepository.logWeight(weightKg) }
    }

    val progressPhotos: StateFlow<List<ProgressPhoto>> = progressPhotoRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The one photo (if any) belonging to the currently selected Diary date, a progress
     * photo is a per-day thing, so the Diary card should reflect the day being viewed
     * instead of always showing whichever photo is most recent overall. */
    val selectedDayPhoto: StateFlow<ProgressPhoto?> = combine(
        selectedDate,
        progressPhotoRepository.observeAll()
    ) { date, photos -> photos.firstOrNull { it.date == date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveProgressPhoto(filePath: String) {
        viewModelScope.launch { progressPhotoRepository.save(filePath, selectedDate.value) }
    }

    fun deleteProgressPhoto(photo: ProgressPhoto) {
        viewModelScope.launch { progressPhotoRepository.delete(photo) }
    }

    class Factory(
        private val logRepository: LogRepository,
        private val securePrefs: SecurePrefs,
        private val weightRepository: WeightRepository,
        private val progressPhotoRepository: ProgressPhotoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(logRepository, securePrefs, weightRepository, progressPhotoRepository) as T
        }
    }
}
