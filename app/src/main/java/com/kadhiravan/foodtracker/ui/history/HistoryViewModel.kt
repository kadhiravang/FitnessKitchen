package com.kadhiravan.foodtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CalendarDay(val date: String, val dayOfMonth: Int, val totalCalories: Int, val hasData: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val logRepository: LogRepository) : ViewModel() {

    private val monthAnchor = MutableStateFlow(DateUtils.firstDayOfMonth(DateUtils.today()))

    val monthLabel: StateFlow<String> = monthAnchor
        .map { DateUtils.monthYearLabel(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateUtils.monthYearLabel(monthAnchor.value))

    val leadingBlanks: StateFlow<Int> = monthAnchor
        .map { DateUtils.dayOfWeekIndex(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val monthDays: StateFlow<List<CalendarDay>> = monthAnchor
        .flatMapLatest { start ->
            val daysCount = DateUtils.daysInMonth(start)
            val end = DateUtils.offsetDate(start, daysCount - 1)
            logRepository.observeDailyTotalsBetween(start, end).map { totals ->
                val byDate = totals.associateBy { it.date }
                (0 until daysCount).map { offset ->
                    val date = DateUtils.offsetDate(start, offset)
                    val total = byDate[date]
                    CalendarDay(date = date, dayOfMonth = offset + 1, totalCalories = total?.totalCalories ?: 0, hasData = total != null)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun goToPreviousMonth() {
        monthAnchor.value = DateUtils.offsetMonths(monthAnchor.value, -1)
    }

    fun goToNextMonth() {
        monthAnchor.value = DateUtils.offsetMonths(monthAnchor.value, 1)
    }

    class Factory(private val logRepository: LogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(logRepository) as T
        }
    }
}
