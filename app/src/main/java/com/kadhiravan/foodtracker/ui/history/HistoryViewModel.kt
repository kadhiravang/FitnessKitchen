package com.kadhiravan.foodtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayTotal(val date: String, val totalCalories: Int)

class HistoryViewModel(private val logRepository: LogRepository) : ViewModel() {

    private val _dayTotals = MutableStateFlow<List<DayTotal>>(emptyList())
    val dayTotals: StateFlow<List<DayTotal>> = _dayTotals.asStateFlow()

    init {
        viewModelScope.launch {
            logRepository.observeLoggedDates().collect { dates ->
                val totals = dates.map { date ->
                    DayTotal(date, logRepository.getForDate(date).sumOf { it.calories })
                }
                _dayTotals.value = totals.sortedByDescending { it.date }
            }
        }
    }

    class Factory(private val logRepository: LogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(logRepository) as T
        }
    }
}
