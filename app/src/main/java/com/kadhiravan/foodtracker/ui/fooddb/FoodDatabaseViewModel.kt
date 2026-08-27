package com.kadhiravan.foodtracker.ui.fooddb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.local.FoodItem
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodDatabaseViewModel(private val foodRepository: FoodRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val query: StateFlow<String> = searchQuery

    val foods: StateFlow<List<FoodItem>> = combine(
        foodRepository.observeAll(),
        searchQuery
    ) { items, query ->
        if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) {
        searchQuery.value = value
    }

    fun save(item: FoodItem) {
        viewModelScope.launch { foodRepository.save(item) }
    }

    fun delete(item: FoodItem) {
        viewModelScope.launch { foodRepository.delete(item) }
    }

    class Factory(private val foodRepository: FoodRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FoodDatabaseViewModel(foodRepository) as T
        }
    }
}
