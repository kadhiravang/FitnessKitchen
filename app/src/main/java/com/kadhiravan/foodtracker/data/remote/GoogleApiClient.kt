package com.kadhiravan.foodtracker.data.remote

import android.util.Log
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.ChatRole
import com.kadhiravan.foodtracker.data.local.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleApiException(message: String) : IOException(message)

/**
 * Talks to Google's Gemini REST API (generateContent) to hold a running conversation
 * about what the user ate. The assistant either asks a clarifying question (plain text)
 * or, once confident, replies with a short line plus a fenced ```log block that
 * [LogCardParser] turns into a confirmable food card, grounded two ways: against the
 * user's own food catalog (known dishes get accurate calories instead of guesses), and,
 * for anything not already known, via a `lookup_nutrition` function tool backed by real
 * USDA FoodData Central data (see [UsdaNutritionClient]) instead of the model's own
 * memorized guess. This is a genuine tool-call loop, the model decides when to invoke
 * the function, our code executes the real lookup, and the result is fed back for a
 * second round before the model produces its final reply, not just repeated guessing.
 */
class GoogleApiClient(private val usdaClient: UsdaNutritionClient) : ChatApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .dns(ResilientDns(bootstrapClient))
        .build()

    override suspend fun sendMessage(
        history: List<ChatMessage>,
        newUserText: String,
        apiKey: String,
        knownFoods: List<FoodItem>,
        todaysLogSummary: String,
        usdaApiKey: String,
        geminiModel: String,
        ollamaModel: String,
        openaiModel: String,
        ollamaCloudModel: String
    ): String = withContext(Dispatchers.IO) {
        runConversation(history, newUserText, apiKey, knownFoods, todaysLogSummary, usdaApiKey, geminiModel)
    }

    private suspend fun runConversation(
        history: List<ChatMessage>,
        newUserText: String,
        apiKey: String,
        knownFoods: List<FoodItem>,
        todaysLogSummary: String,
        usdaApiKey: String,
        geminiModel: String
    ): String {
        if (apiKey.isBlank()) {
            throw GoogleApiException("No Google Gemini API key set. Add one in Settings.")
        }

        val knownFoodsJson = knownFoods.joinToString(prefix = "[", postfix = "]") { food ->
            """{"name":"${food.name.escapeJson()}","servingUnit":"${food.servingUnit.escapeJson()}","caloriesPerServing":${food.caloriesPerServing},"proteinG":${food.proteinG ?: 0.0},"carbsG":${food.carbsG ?: 0.0},"fatG":${food.fatG ?: 0.0}}"""
        }

        val toolsAvailable = usdaApiKey.isNotBlank()

        val groundingRule = if (toolsAvailable) {
            """
            - You have a lookup_nutrition function tool backed by a real nutrition database.
              For any food that ISN'T already in the known-foods list below, call it with a
              simple generic search term for the dish (e.g. "chicken biryani", not the user's
              exact phrasing) BEFORE answering, it returns real calories/protein/carbs/fat per
              100g. Use that as your base, then combine it with the quantity/ingredients the
              user actually described to compute the final totals (adjusting for home-cooking
              factors like extra oil the lookup's reference item might not match exactly).
              If the user describes a custom dish by its ingredients (e.g. "200g chicken,
              2 tsp oil, 1 onion, 100g tomatoes") rather than naming a known dish, look up
              EACH significant ingredient separately instead of guessing at the whole thing
              (you can call the tool multiple times in the same turn), then sum each
              ingredient's real per-100g values scaled by its own quantity. This is usually
              far more accurate than a single whole-dish lookup for something home-made.
              Only skip the lookup for an exact known-foods match. The lookup's search is
              keyword-based, not smart, check its "description" field is genuinely the same
              food you asked about before trusting the numbers (e.g. a "chicken 65" search
              matching a cooking-oil product is a false match, not real data on fried
              chicken). Many South Indian dishes (kothu parotta, rasam, poriyal, specific
              kuzhambu varieties, etc.) simply aren't in this database at all, if the lookup
              returns found:false, or the match is clearly the wrong food, fall back to your
              own best estimate exactly as if no tool existed, rather than using a bad match.
            """.trimIndent()
        } else {
            """
            - The user usually does NOT know exact quantities, ingredients, or calorie counts
              themselves, that's why they're asking you. Always make your own reasonable
              best-effort estimate using typical Indian home-cooking assumptions (average
              serving size, common recipe proportions, usual oil/ghee content).
            """.trimIndent()
        }

        val systemPrompt = """
            You are a friendly, concise nutrition assistant specialized in Indian and South
            Indian home cooking, chatting with the user about what they ate so you can log it.

            Rules:
            - The user may write or speak in English, Tamil (Tamil script or transliterated),
              or a mix of both, food names are often native Tamil words. Understand them
              directly and always reply in English.
            - If the user's message isn't about food, just reply naturally and briefly.
            - NEVER leave the user without a number, and almost never ask a clarifying
              question, log your best-effort result immediately. You can briefly state any
              assumption you made in your reply (e.g. "assuming a medium bowl, ~250ml") so
              they can correct it on the card afterward if it's off. Only ask a clarifying
              question in the rare case where you cannot identify the dish at all, even then,
              still give your best guess estimate AND the log block in that same reply rather
              than blocking on an answer.
            $groundingRule
            - Once you have an estimate (it matches a known food, or you've used the lookup
              tool / your own best assumption), reply with a short friendly line confirming
              what you understood, followed immediately by EXACTLY ONE fenced block containing
              ONE JSON object in this form:
              ```log
              {"meals":[{"mealType":"BREAKFAST","items":[{"name":"...","quantity":1,"unit":"piece","calories":120,"proteinG":4.5,"carbsG":18.0,"fatG":3.0,"matchedKnownFood":true}]}]}
              ```
              This must always be valid JSON, a single top-level object with one "meals"
              array. If the user describes several meals at once (e.g. logging a whole day),
              put ALL of them as separate entries inside that same "meals" array, never emit
              more than one ```log block, and never write two JSON objects back to back.
              mealType must be one of BREAKFAST, LUNCH, DINNER, SNACK (pick the most likely
              one based on context/time if not stated). "calories", "proteinG", "carbsG", and
              "fatG" are all TOTALS for the quantity/unit given, not per-unit values. If an
              item matches a known food, scale its caloriesPerServing/proteinG/carbsG/fatG by
              quantity and set matchedKnownFood true; otherwise set matchedKnownFood false.
            - Keep every reply short, a couple of sentences at most, like a text message.
            - You're told what the user has already eaten today, their daily goal, and
              exactly how much is left below. When they actually ask for planning help
              (e.g. "what should I eat now", "how much rice can I have", "can I eat this
              and stay under budget", "how am I doing today"), give a genuine, specific
              answer using those exact numbers: real food/quantity suggestions and the
              actual math showing how it fits what's left of their day, the way a
              knowledgeable friend would, not a vague estimate. Don't volunteer this kind
              of breakdown unprompted on an ordinary logging message, only when they
              actually ask for guidance.

            Known foods: $knownFoodsJson
            Eaten today: $todaysLogSummary
        """.trimIndent()

        val contents = buildList {
            history.forEach { msg ->
                add(GeminiContent(role = if (msg.role == ChatRole.USER) "user" else "model", parts = listOf(GeminiPart(text = msg.content))))
            }
            add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = newUserText))))
        }.toMutableList()

        val tools = if (toolsAvailable) listOf(GeminiTool(functionDeclarations = listOf(NUTRITION_LOOKUP_DECLARATION))) else emptyList()
        val systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt)))

        var round = 0
        while (true) {
            round++

            val requestBody = GeminiRequest(contents = contents, systemInstruction = systemInstruction, tools = tools)
            val body = json.encodeToString(GeminiRequest.serializer(), requestBody)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${geminiModel.ifBlank { MODEL }}:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val startMs = System.currentTimeMillis()
            val responseText = executeWithRetry(request)
            Log.d(TAG, "Gemini API round $round trip: ${System.currentTimeMillis() - startMs}ms")

            val completion = try {
                json.decodeFromString(GeminiResponse.serializer(), responseText)
            } catch (e: Exception) {
                throw GoogleApiException("Unexpected response from Gemini API: ${responseText.take(500)}")
            }

            val parts = completion.candidates.firstOrNull()?.content?.parts
                ?: throw GoogleApiException(
                    completion.promptFeedback?.blockReason?.let { "Gemini blocked the response: $it" }
                        ?: "Gemini API returned no content."
                )

            val functionCalls = parts.mapNotNull { it.functionCall }
            if (functionCalls.isNotEmpty() && round <= MAX_TOOL_ROUNDS) {
                // A custom dish described ingredient-by-ingredient (oil, veggies, protein,
                // etc.) needs one lookup per ingredient, not one for the whole dish, Gemini
                // batches these as several functionCall parts in a single turn rather than
                // one at a time, so every part has to be answered, not just the first.
                contents.add(GeminiContent(role = "model", parts = parts))

                // Run every lookup in this round concurrently rather than one at a time,
                // a 4-ingredient custom dish used to mean 4 sequential USDA round trips
                // stacked in front of the next Gemini call; now it's bounded by the
                // slowest single lookup instead of their sum.
                val responseParts = coroutineScope {
                    functionCalls.map { call ->
                        async {
                            val foodName = (call.args["food_name"] as? JsonPrimitive)?.content.orEmpty()
                            val facts = runCatching { usdaClient.lookup(foodName, usdaApiKey) }.getOrNull()
                            val resultJson = buildJsonObject {
                                if (facts != null) {
                                    put("found", true)
                                    put("description", facts.description)
                                    put("caloriesPer100g", facts.caloriesPer100g)
                                    put("proteinPer100g", facts.proteinPer100g)
                                    put("carbsPer100g", facts.carbsPer100g)
                                    put("fatPer100g", facts.fatPer100g)
                                } else {
                                    put("found", false)
                                }
                            }
                            GeminiPart(
                                functionResponse = GeminiFunctionResponse(
                                    name = call.name,
                                    id = call.id,
                                    response = resultJson
                                )
                            )
                        }
                    }.awaitAll()
                }
                contents.add(GeminiContent(role = "user", parts = responseParts))
                continue
            }

            // Joined rather than just the first part, replies after a tool round can come
            // back split across multiple text parts instead of one.
            val text = parts.mapNotNull { it.text }.joinToString("").takeIf { it.isNotBlank() }
                ?: throw GoogleApiException("Gemini API returned no content.")
            return text.trim()
        }
    }

    /**
     * Runs the request, retrying with backoff on transient failures (network hiccups,
     * rate limiting, or an overloaded upstream, 429/503/5xx) so a momentary blip doesn't
     * force the user to manually resend. Non-transient failures (bad key, bad request)
     * fail immediately.
     */
    private suspend fun executeWithRetry(request: Request): String {
        // Each failed attempt can already cost several seconds fighting a flaky DNS
        // resolver (see ResilientDns) before backoff even starts, so a low attempt count
        // used to burn the whole retry budget on DNS alone, leaving none to ride out a
        // separately transient upstream overload. More attempts with a capped backoff
        // gives both problems room to resolve within one exchange.
        val maxAttempts = 6
        var attempt = 0
        val startMs = System.currentTimeMillis()
        while (true) {
            attempt++
            val response = try {
                executeCancellable(request)
            } catch (e: IOException) {
                // Covers UnknownHostException too, a brief DNS hiccup (common right after
                // a phone hands off between Wi-Fi and cellular) looks identical to this.
                Log.d(TAG, "attempt $attempt failed after ${System.currentTimeMillis() - startMs}ms: ${e.javaClass.simpleName}: ${e.message}")
                if (attempt >= maxAttempts) {
                    throw GoogleApiException("Network error talking to Gemini API: ${e.message}")
                }
                delay(backoffMs(attempt))
                continue
            }
            val bodyText = response.use { it.body?.string().orEmpty() }
            if (response.isSuccessful) return bodyText

            Log.d(TAG, "attempt $attempt got HTTP ${response.code} after ${System.currentTimeMillis() - startMs}ms: $bodyText")
            val transient = response.code == 429 || response.code in 500..599
            if (transient && attempt < maxAttempts) {
                delay(backoffMs(attempt))
                continue
            }
            // Truncated to 300 chars used to cut this off before the "details" array,
            // which is exactly where Google puts the quotaMetric/quotaId that says
            // WHICH limit (per-minute vs per-day, which model) was actually hit.
            throw GoogleApiException("Gemini API error ${response.code}: ${bodyText.take(2000)}")
        }
    }

    private fun backoffMs(attempt: Int): Long = (1000L * (1L shl (attempt - 1))).coerceAtMost(8000L)

    /**
     * Suspends until the call completes, but unlike the blocking [Call.execute], it actually
     * aborts the in-flight HTTP call when the coroutine is cancelled (e.g. the user tapped
     * Cancel while a reply was hanging), instead of leaving it running unattended.
     */
    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val TAG = "GoogleApiClient"
        // Fallback if Settings hasn't set a model yet (fresh install, or the caller
        // passed a blank string) — kept in sync with GeminiModel.DEFAULT. A per-model
        // Settings switch (see GeminiModel) lets the user hop to 3.7 or 3.8 if this one
        // hits its daily cap, since each model's quota is tracked separately.
        const val MODEL = "gemini-3.6-flash"
        // Caps how many tool-call round TRIPS one message can trigger, not how many
        // lookups, since Gemini batches several functionCall parts into a single turn
        // when it can (e.g. every ingredient of a custom dish at once). This just stops a
        // confused model from looping indefinitely across turns.
        const val MAX_TOOL_ROUNDS = 6

        val NUTRITION_LOOKUP_DECLARATION = GeminiFunctionDeclaration(
            name = "lookup_nutrition",
            description = "Looks up real nutrition facts (calories, protein, carbs, fat, all " +
                "per 100g) for a food from the USDA FoodData Central database. Call this for " +
                "any food that isn't already in the known-foods list, before estimating.",
            parameters = GeminiSchema(
                type = "object",
                properties = mapOf(
                    "food_name" to GeminiSchema(
                        type = "string",
                        description = "A simple, generic search term for the food or dish " +
                            "(e.g. \"chicken biryani\", \"idli\"), not the user's exact wording."
                    )
                ),
                required = listOf("food_name")
            )
        )
    }
}

@Serializable
private data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    // Gemini 3's internal reasoning-state token attached to functionCall (and sometimes
    // text) parts, must be echoed back verbatim on the part it arrived on when replaying
    // the model's own turn in a follow-up request, or the API 400s ("missing
    // thought_signature"). We never read this ourselves, only round-trip it.
    val thoughtSignature: String? = null
)

@Serializable
private data class GeminiFunctionCall(
    val name: String,
    val id: String? = null,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
private data class GeminiFunctionResponse(
    val name: String,
    val id: String? = null,
    val response: JsonObject
)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
private data class GeminiSystemInstruction(val parts: List<GeminiPart>)

// The Gemini 3.x line replaced 2.5's numeric thinkingBudget with a thinkingLevel enum
// ("low"/"medium"/"high"), sending the old thinkingBudget field to a 3.x model is
// invalid (and has been reported to trigger bogus billing errors on the free tier).
// "low" keeps replies fast and avoids burning the maxOutputTokens budget on reasoning
// before the real reply is written.
@Serializable
private data class GeminiThinkingConfig(val thinkingLevel: String = "low")

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double = 0.3,
    val maxOutputTokens: Int = 1024,
    val thinkingConfig: GeminiThinkingConfig = GeminiThinkingConfig()
)

@Serializable
private data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null
)

@Serializable
private data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiSchema
)

@Serializable
private data class GeminiTool(val functionDeclarations: List<GeminiFunctionDeclaration>? = null)

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    val tools: List<GeminiTool> = emptyList()
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
private data class GeminiCandidate(val content: GeminiResponseContent? = null)

@Serializable
private data class GeminiResponseContent(val parts: List<GeminiPart> = emptyList())

@Serializable
private data class GeminiPromptFeedback(val blockReason: String? = null)
