package com.kadhiravan.foodtracker.data.remote

import com.kadhiravan.foodtracker.data.local.MealType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class MealLog(
    val mealType: MealType,
    val items: List<ParsedFoodItem>
)

data class LogCard(
    val meals: List<MealLog>
)

/**
 * Extracts and parses the assistant's fenced ```log block (if any) from a chat
 * message's raw content. Used both right after a reply arrives and when
 * re-rendering historical messages loaded from Room.
 *
 * The fence always wraps a single JSON object holding a "meals" array (even when
 * logging just one meal), this lets one message log an entire day (breakfast,
 * lunch, dinner...) as a single card instead of the model emitting multiple
 * concatenated JSON objects in one fence, which isn't valid JSON and used to
 * silently fail to parse.
 */
object LogCardParser {

    private val fenceRegex = Regex("(?s)```log\\s*(\\{.*\\})\\s*```")
    private val json = Json { ignoreUnknownKeys = true }

    /** The message text with the ```log fence stripped out, for display as a chat bubble. */
    fun textWithoutCard(content: String): String =
        fenceRegex.replace(content, "").trim()

    fun parse(content: String): LogCard? {
        val match = fenceRegex.find(content) ?: return null
        return try {
            json.decodeFromString(LogCardPayload.serializer(), match.groupValues[1])
                .let { payload ->
                    LogCard(
                        meals = payload.meals.map { meal ->
                            MealLog(
                                mealType = runCatching { MealType.valueOf(meal.mealType.uppercase()) }
                                    .getOrDefault(MealType.SNACK),
                                items = meal.items
                            )
                        }
                    )
                }
        } catch (e: Exception) {
            null
        }
    }

    /** Rebuilds a message's ```log fence from what was actually confirmed (after any edits
     * made in the card UI) and splices it back into the stored content, so a re-render of
     * the CONFIRMED chip reflects the edited totals instead of the AI's original estimate , 
     * see ChatRepository.confirmCard. */
    fun withUpdatedFence(content: String, meals: List<Pair<MealType, List<ParsedFoodItem>>>): String {
        val payload = LogCardPayload(meals.map { (mealType, items) -> MealGroupPayload(mealType.name, items) })
        val fence = "```log\n${json.encodeToString(LogCardPayload.serializer(), payload)}\n```"
        return if (fenceRegex.containsMatchIn(content)) fenceRegex.replace(content, fence) else "$content\n\n$fence"
    }
}

@Serializable
private data class LogCardPayload(
    val meals: List<MealGroupPayload>
)

@Serializable
private data class MealGroupPayload(
    val mealType: String,
    val items: List<ParsedFoodItem>
)
