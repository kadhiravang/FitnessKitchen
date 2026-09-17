package com.kadhiravan.foodtracker.data.remote

import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.FoodItem

/** Common shape for whichever chat-completions provider is currently selected in
 * Settings (see [com.kadhiravan.foodtracker.data.prefs.ChatProvider]), lets
 * [com.kadhiravan.foodtracker.data.repository.ChatRepository] switch providers without
 * caring which one is actually backing the conversation. */
interface ChatApiClient {
    /** Returns the raw assistant reply text (may contain a ```log fence, see [LogCardParser]). */
    suspend fun sendMessage(
        history: List<ChatMessage>,
        newUserText: String,
        apiKey: String,
        knownFoods: List<FoodItem>,
        todaysLogSummary: String,
        usdaApiKey: String = "",
        geminiModel: String = ""
    ): String
}
