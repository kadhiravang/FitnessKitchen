package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.CardStatus
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.ChatMessageDao
import com.kadhiravan.foodtracker.data.local.ChatRole
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.prefs.ChatProvider
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.ChatApiClient
import com.kadhiravan.foodtracker.data.remote.LogCardParser
import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem
import com.kadhiravan.foodtracker.util.DateUtils
import com.kadhiravan.foodtracker.util.NutritionCalculator
import com.kadhiravan.foodtracker.util.NutritionTargets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private const val HISTORY_WINDOW = 20

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val googleApiClient: ChatApiClient,
    private val nvidiaApiClient: ChatApiClient,
    private val ollamaApiClient: ChatApiClient,
    private val ollamaCloudApiClient: ChatApiClient,
    private val claudeApiClient: ChatApiClient,
    private val openaiApiClient: ChatApiClient,
    private val foodRepository: FoodRepository,
    private val logRepository: LogRepository,
    private val weightRepository: WeightRepository,
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
            ChatProvider.OLLAMA -> ollamaApiClient to securePrefs.ollamaServerUrl
            ChatProvider.OLLAMA_CLOUD -> ollamaCloudApiClient to securePrefs.ollamaCloudApiKey
            ChatProvider.CLAUDE -> claudeApiClient to securePrefs.claudeApiKey
            ChatProvider.OPENAI -> openaiApiClient to securePrefs.openaiApiKey
        }

        val knownFoods = foodRepository.getAll()

        val replyContent = try {
            apiClient.sendMessage(
                history = history,
                newUserText = text,
                apiKey = apiKey,
                knownFoods = knownFoods,
                todaysLogSummary = buildLogSummary(logRepository.getForDate(date), computeTargets()),
                usdaApiKey = securePrefs.usdaApiKey,
                geminiModel = securePrefs.geminiModel,
                ollamaModel = securePrefs.ollamaModel,
                openaiModel = securePrefs.openaiModel,
                ollamaCloudModel = securePrefs.ollamaCloudModel
            )
        } catch (e: CancellationException) {
            // The user cancelled the send, their message stays in the thread, but we
            // don't want a "⚠️ cancelled" bubble; just stop, no assistant reply.
            throw e
        } catch (e: Exception) {
            "⚠️ ${e.message ?: "Something went wrong talking to the AI."}"
        }

        val card = LogCardParser.parse(replyContent)
        chatMessageDao.insert(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = replyContent,
                chatDate = date,
                cardStatus = if (card != null) CardStatus.PENDING else null
            )
        )
    }

    /** Mirrors HomeViewModel's own target calculation exactly, so the number the chat
     * reasons about is the same one shown on the Diary tab, not a separate guess. */
    private suspend fun computeTargets(): NutritionTargets? {
        val sex = securePrefs.sex
        val latestWeightKg = weightRepository.getLatest()?.weightKg
        val base = if (securePrefs.hasProfileBasics() && sex != null && latestWeightKg != null) {
            NutritionCalculator.calculate(
                age = securePrefs.age,
                heightCm = securePrefs.heightCm,
                weightKg = latestWeightKg,
                sex = sex,
                activityLevel = securePrefs.activityLevel,
                goal = securePrefs.nutritionGoal
            )
        } else if (securePrefs.dailyCalorieGoal > 0) {
            NutritionCalculator.fromCalorieGoalOnly(securePrefs.dailyCalorieGoal)
        } else {
            return null
        }
        val withGoal = NutritionCalculator.withManualGoal(base, securePrefs.dailyCalorieGoal)
        return if (securePrefs.useCustomMacros) {
            NutritionCalculator.applyCustomMacros(withGoal, securePrefs.customProteinG, securePrefs.customCarbsG, securePrefs.customFatG)
        } else {
            withGoal
        }
    }

    private fun buildLogSummary(entries: List<LogEntry>, targets: NutritionTargets?): String {
        val totalCalories = entries.sumOf { it.calories }
        val totalProtein = entries.sumOf { it.proteinG }.roundToInt()
        val totalCarbs = entries.sumOf { it.carbsG }.roundToInt()
        val totalFat = entries.sumOf { it.fatG }.roundToInt()
        val eaten = if (entries.isEmpty()) {
            "Nothing logged yet today."
        } else {
            val byMeal = entries.groupBy { it.mealType }.entries.joinToString(" | ") { (meal, items) ->
                "$meal: " + items.joinToString(", ") { "${it.foodName} (${it.calories} kcal)" }
            }
            "$totalCalories kcal so far today (${totalProtein}g protein, ${totalCarbs}g carbs, ${totalFat}g fat). $byMeal"
        }
        // Precomputed here rather than left for the model to subtract itself, arithmetic
        // it's asked to do in its head is exactly the kind of thing worth just doing in
        // code and handing over as a fact instead.
        val goalLine = if (targets != null && targets.calorieGoal > 0) {
            val remainingCal = (targets.calorieGoal - totalCalories).coerceAtLeast(0)
            val remainingProtein = (targets.proteinG - totalProtein).coerceAtLeast(0)
            val remainingCarbs = (targets.carbsG - totalCarbs).coerceAtLeast(0)
            val remainingFat = (targets.fatG - totalFat).coerceAtLeast(0)
            " Daily goal: ${targets.calorieGoal} kcal (${targets.proteinG}g protein, ${targets.carbsG}g carbs, ${targets.fatG}g fat)." +
                " Remaining today: $remainingCal kcal (${remainingProtein}g protein, ${remainingCarbs}g carbs, ${remainingFat}g fat)."
        } else {
            " No daily calorie goal set yet (Settings/You tab), so no remaining-budget math is possible, just say so if asked."
        }
        return eaten + goalLine
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
