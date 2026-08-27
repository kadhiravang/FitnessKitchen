package com.kadhiravan.foodtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val date: String = DateUtils.today(),
    val entriesByMeal: Map<MealType, List<LogEntry>> = emptyMap(),
    val totalCalories: Int = 0,
    val calorieGoal: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val logRepository: LogRepository,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val selectedDate = MutableStateFlow(DateUtils.today())

    val uiState: StateFlow<HomeUiState> = selectedDate
        .flatMapLatest { date ->
            logRepository.observeForDate(date).map { entries ->
                HomeUiState(
                    date = date,
                    entriesByMeal = entries.groupBy { it.mealType },
                    totalCalories = entries.sumOf { it.calories },
                    calorieGoal = securePrefs.dailyCalorieGoal
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun goToPreviousDay() {
        selectedDate.value = DateUtils.offsetFromToday(daysBetweenTodayAnd(selectedDate.value) - 1)
    }

    fun goToNextDay() {
        selectedDate.value = DateUtils.offsetFromToday(daysBetweenTodayAnd(selectedDate.value) + 1)
    }

    private fun daysBetweenTodayAnd(iso: String): Int {
        // Simple approximation good enough for day-stepping in the UI.
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(DateUtils.today())!!
            val target = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso)!!
            ((target.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { logRepository.delete(entry) }
    }

    class Factory(
        private val logRepository: LogRepository,
        private val securePrefs: SecurePrefs
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(logRepository, securePrefs) as T
        }
    }
}
