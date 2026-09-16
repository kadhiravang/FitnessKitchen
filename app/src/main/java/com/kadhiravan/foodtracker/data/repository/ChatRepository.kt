package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.CardStatus
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.ChatMessageDao
import com.kadhiravan.foodtracker.data.local.ChatRole
import com.kadhiravan.foodtracker.data.local.FoodItem
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.prefs.ChatProvider
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.ChatApiClient
import com.kadhiravan.foodtracker.data.remote.LogCardParser
import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem
import com.kadhiravan.foodtracker.util.DateUtils
import com.kadhiravan.foodtracker.util.FoodMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private const val HISTORY_WINDOW = 20

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val googleApiClient: ChatApiClient,
    private val nvidiaApiClient: ChatApiClient,
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository,
    private val securePrefs: SecurePrefs
) {
    /** Chat is scoped per day (like the Diary), a long-running single thread was diluting
     * the model's context with days-old, unrelated messages. */
    fun observeMessages(date: String): Flow<List<ChatMessage>> = chatMessageDao.observeForDate(date)

    /** Consecutive days (ending today/yesterday) with at least one logged entry. */
    fun observeStreak(): Flow<Int> = logRepository.observeLoggedDates().map { DateUtils.computeStreak(it) }

    suspend fun sendUserMessage(text: String, date: String) {
        val history = chatMessageDao.getRecentForDate(date, HISTORY_WINDOW).reversed()
        chatMessageDao.insert(ChatMessage(role = ChatRole.USER, content = text, chatDate = date))

        val (apiClient, apiKey) = when (securePrefs.chatProvider) {
            ChatProvider.NVIDIA -> nvidiaApiClient to securePrefs.nvidiaApiKey
            ChatProvider.GOOGLE -> googleApiClient to securePrefs.geminiApiKey
        }

        val knownFoods = foodRepository.getAll()

        val replyContent = try {
            apiClient.sendMessage(
                history = history,
                newUserText = text,
                apiKey = apiKey,
                knownFoods = knownFoods,
                todaysLogSummary = buildLogSummary(logRepository.getForDate(date)),
                usdaApiKey = securePrefs.usdaApiKey
            )
        } catch (e: CancellationException) {
            // The user cancelled the send, their message stays in the thread, but we
            // don't want a "⚠️ cancelled" bubble; just stop, no assistant reply.
            throw e
        } catch (e: Exception) {
            "⚠️ ${e.message ?: "Something went wrong talking to the AI."}"
        }

        val card = LogCardParser.parse(replyContent)
        // The model decides for itself whether an item "matches" something in the known-foods
        // list it was shown, then re-estimates calories/macros either way, so the same dish
        // worded slightly differently can silently get a different number each time. Redoing
        // the match deterministically here and overwriting with the catalog's own stored
        // values (only when the units actually line up) makes repeat dishes consistent
        // regardless of what the model guessed.
        val finalContent = if (card != null) {
            val correctedMeals = card.meals.map { meal ->
                meal.mealType to meal.items.map { item -> applyKnownFoodMatch(item, knownFoods) }
            }
            LogCardParser.withUpdatedFence(replyContent, correctedMeals)
        } else {
            replyContent
        }

        chatMessageDao.insert(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = finalContent,
                chatDate = date,
                cardStatus = if (card != null) CardStatus.PENDING else null
            )
        )
    }

    private fun applyKnownFoodMatch(item: ParsedFoodItem, knownFoods: List<FoodItem>): ParsedFoodItem {
        val match = FoodMatcher.findBestMatch(item.name, knownFoods) ?: return item
        if (!FoodMatcher.unitsCompatible(item.unit, match.servingUnit)) return item
        return item.copy(
            calories = (match.caloriesPerServing * item.quantity).roundToInt(),
            proteinG = (match.proteinG ?: 0.0) * item.quantity,
            carbsG = (match.carbsG ?: 0.0) * item.quantity,
            fatG = (match.fatG ?: 0.0) * item.quantity,
            matchedKnownFood = true
        )
    }

    private fun buildLogSummary(entries: List<LogEntry>): String {
        if (entries.isEmpty()) return "Nothing logged yet today."
        val totalCalories = entries.sumOf { it.calories }
        val totalProtein = entries.sumOf { it.proteinG }.roundToInt()
        val totalCarbs = entries.sumOf { it.carbsG }.roundToInt()
        val totalFat = entries.sumOf { it.fatG }.roundToInt()
        val byMeal = entries.groupBy { it.mealType }.entries.joinToString(" | ") { (meal, items) ->
            "$meal: " + items.joinToString(", ") { "${it.foodName} (${it.calories} kcal)" }
        }
        return "$totalCalories kcal so far today (${totalProtein}g protein, ${totalCarbs}g carbs, ${totalFat}g fat). $byMeal"
    }

    suspend fun confirmCard(message: ChatMessage, mealGroups: List<Pair<MealType, List<ParsedFoodItem>>>) {
        val entries = mealGroups.flatMap { (mealType, items) ->
            items.filter { it.name.isNotBlank() && it.quantity > 0 }.map { item ->
                LogEntry(
                    foodName = item.name.trim(),
                    quantity = item.quantity,
                    unit = item.unit.trim().ifBlank { "serving" },
                    calories = item.calories,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    mealType = mealType,
                    logDate = message.chatDate
                )
            }
        }
        logRepository.addEntries(entries)

        mealGroups.flatMap { it.second }.filterNot { it.matchedKnownFood }.forEach { item ->
            if (item.name.isBlank() || item.quantity <= 0) return@forEach
            foodRepository.upsertFromVoiceEntry(
                name = item.name.trim(),
                unit = item.unit.trim().ifBlank { "serving" },
                caloriesPerServing = (item.calories / item.quantity).toInt(),
                proteinG = item.proteinG / item.quantity,
                carbsG = item.carbsG / item.quantity,
                fatG = item.fatG / item.quantity
            )
        }

        // Splice any edits made in the card UI back into the stored content, otherwise the
        // CONFIRMED chip re-parses the original, unedited fence and shows the AI's initial
        // estimate instead of what was actually logged.
        val updatedContent = LogCardParser.withUpdatedFence(message.content, mealGroups)
        chatMessageDao.updateContentAndStatus(message.id, updatedContent, CardStatus.CONFIRMED)
    }

    suspend fun dismissCard(message: ChatMessage) {
        chatMessageDao.updateCardStatus(message.id, CardStatus.DISMISSED)
    }
}
