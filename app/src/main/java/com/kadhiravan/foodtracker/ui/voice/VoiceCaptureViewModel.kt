package com.kadhiravan.foodtracker.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.VoiceParsingRepository
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class EditableFoodEntry(
    val localId: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String,
    val unit: String,
    val calories: String,
    val matchedKnownFood: Boolean
)

sealed class VoiceUiState {
    data object Idle : VoiceUiState()
    data class Listening(val partialTranscript: String) : VoiceUiState()
    data class Processing(val transcript: String) : VoiceUiState()
    data class Confirming(
        val items: List<EditableFoodEntry>,
        val mealType: MealType
    ) : VoiceUiState()
    data class Error(val message: String) : VoiceUiState()
    data object Saved : VoiceUiState()
}

class VoiceCaptureViewModel(
    private val voiceParsingRepository: VoiceParsingRepository,
    private val logRepository: LogRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    fun onListeningStarted() {
        _uiState.value = VoiceUiState.Listening("")
    }

    fun onPartialTranscript(text: String) {
        _uiState.value = VoiceUiState.Listening(text)
    }

    fun onSpeechError(message: String) {
        _uiState.value = VoiceUiState.Error(message)
    }

    fun onFinalTranscript(transcript: String) {
        if (transcript.isBlank()) {
            _uiState.value = VoiceUiState.Error("Didn't catch that — try again.")
            return
        }
        _uiState.value = VoiceUiState.Processing(transcript)
        viewModelScope.launch {
            voiceParsingRepository.parseTranscript(transcript)
                .onSuccess { parsed ->
                    _uiState.value = VoiceUiState.Confirming(
                        items = parsed.map { it.toEditable() },
                        mealType = DateUtils.defaultMealTypeForNow()
                    )
                }
                .onFailure { error ->
                    _uiState.value = VoiceUiState.Error(error.message ?: "Something went wrong.")
                }
        }
    }

    fun updateItem(localId: String, updated: EditableFoodEntry) {
        val state = _uiState.value
        if (state is VoiceUiState.Confirming) {
            _uiState.value = state.copy(items = state.items.map { if (it.localId == localId) updated else it })
        }
    }

    fun removeItem(localId: String) {
        val state = _uiState.value
        if (state is VoiceUiState.Confirming) {
            _uiState.value = state.copy(items = state.items.filterNot { it.localId == localId })
        }
    }

    fun addBlankItem() {
        val state = _uiState.value
        if (state is VoiceUiState.Confirming) {
            val blank = EditableFoodEntry(name = "", quantity = "1", unit = "serving", calories = "0", matchedKnownFood = false)
            _uiState.value = state.copy(items = state.items + blank)
        }
    }

    fun setMealType(mealType: MealType) {
        val state = _uiState.value
        if (state is VoiceUiState.Confirming) {
            _uiState.value = state.copy(mealType = mealType)
        }
    }

    fun confirmAndSave(date: String) {
        val state = _uiState.value
        if (state !is VoiceUiState.Confirming) return

        viewModelScope.launch {
            val entries = state.items.mapNotNull { item ->
                val quantity = item.quantity.toDoubleOrNull() ?: return@mapNotNull null
                val calories = item.calories.toIntOrNull() ?: return@mapNotNull null
                if (item.name.isBlank()) return@mapNotNull null
                LogEntry(
                    foodName = item.name.trim(),
                    quantity = quantity,
                    unit = item.unit.trim().ifBlank { "serving" },
                    calories = calories,
                    mealType = state.mealType,
                    logDate = date
                )
            }
            logRepository.addEntries(entries)

            state.items.filterNot { it.matchedKnownFood }.forEach { item ->
                val quantity = item.quantity.toDoubleOrNull() ?: return@forEach
                val calories = item.calories.toIntOrNull() ?: return@forEach
                if (item.name.isBlank() || quantity <= 0) return@forEach
                foodRepository.upsertFromVoiceEntry(
                    name = item.name.trim(),
                    unit = item.unit.trim().ifBlank { "serving" },
                    caloriesPerServing = (calories / quantity).toInt()
                )
            }

            _uiState.value = VoiceUiState.Saved
        }
    }

    fun reset() {
        _uiState.value = VoiceUiState.Idle
    }

    private fun ParsedFoodItem.toEditable() = EditableFoodEntry(
        name = name,
        quantity = formatQuantityInput(quantity),
        unit = unit,
        calories = calories.toString(),
        matchedKnownFood = matchedKnownFood
    )

    private fun formatQuantityInput(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else quantity.toString()

    class Factory(
        private val voiceParsingRepository: VoiceParsingRepository,
        private val logRepository: LogRepository,
        private val foodRepository: FoodRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoiceCaptureViewModel(voiceParsingRepository, logRepository, foodRepository) as T
        }
    }
}
