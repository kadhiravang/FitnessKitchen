package com.kadhiravan.foodtracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.repository.ChatRepository
import com.kadhiravan.foodtracker.ui.components.toParsedOrNull
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chat is scoped per day, like the Diary — one continuous thread across weeks was
 * diluting the model's context with old, unrelated messages. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val chatRepository: ChatRepository) : ViewModel() {

    private val selectedDate = MutableStateFlow(DateUtils.today())
    val currentDate: StateFlow<String> = selectedDate

    val messages: StateFlow<List<ChatMessage>> = selectedDate
        .flatMapLatest { date -> chatRepository.observeMessages(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    val streak: StateFlow<Int> = chatRepository.observeStreak()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var sendJob: Job? = null

    fun sendText(text: String) {
        if (text.isBlank() || _isSending.value) return
        val date = selectedDate.value
        sendJob = viewModelScope.launch {
            _isSending.value = true
            try {
                chatRepository.sendUserMessage(text.trim(), date)
            } finally {
                _isSending.value = false
            }
        }
    }

    /** Aborts a hanging send — the in-flight HTTP call is cancelled too, not just abandoned. */
    fun cancelSending() {
        sendJob?.cancel()
        sendJob = null
        _isSending.value = false
    }

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun goToPreviousDay() {
        selectedDate.value = DateUtils.offsetDate(selectedDate.value, -1)
    }

    fun goToNextDay() {
        selectedDate.value = DateUtils.offsetDate(selectedDate.value, 1)
    }

    fun confirmCard(message: ChatMessage, mealGroups: List<EditableMealGroup>) {
        viewModelScope.launch {
            val parsed = mealGroups.map { group -> group.mealType to group.items.mapNotNull { it.toParsedOrNull() } }
            chatRepository.confirmCard(message, parsed)
        }
    }

    fun dismissCard(message: ChatMessage) {
        viewModelScope.launch { chatRepository.dismissCard(message) }
    }

    class Factory(private val chatRepository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository) as T
        }
    }
}
