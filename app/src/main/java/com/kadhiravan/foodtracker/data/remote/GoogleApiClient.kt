package com.kadhiravan.foodtracker.data.remote

import android.util.Log
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.ChatRole
import com.kadhiravan.foodtracker.data.local.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * [LogCardParser] turns into a confirmable food card — grounded against the user's own
 * food catalog so known dishes get accurate calories instead of guesses.
 */
class GoogleApiClient : ChatApiClient {

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
        todaysLogSummary: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GoogleApiException("No Google Gemini API key set. Add one in Settings.")
        }

        val knownFoodsJson = knownFoods.joinToString(prefix = "[", postfix = "]") { food ->
            """{"name":"${food.name.escapeJson()}","servingUnit":"${food.servingUnit.escapeJson()}","caloriesPerServing":${food.caloriesPerServing},"proteinG":${food.proteinG ?: 0.0},"carbsG":${food.carbsG ?: 0.0},"fatG":${food.fatG ?: 0.0}}"""
        }

        val systemPrompt = """
            You are a friendly, concise nutrition assistant specialized in Indian and South
            Indian home cooking, chatting with the user about what they ate so you can log it.

            Rules:
            - The user may write or speak in English, Tamil (Tamil script or transliterated),
              or a mix of both — food names are often native Tamil words. Understand them
              directly and always reply in English.
            - If the user's message isn't about food, just reply naturally and briefly.
            - The user usually does NOT know exact quantities, ingredients, or calorie counts
              themselves — that's why they're asking you. NEVER leave them without a number,
              and almost never ask a clarifying question. Instead, always make your own
              reasonable best-effort estimate using typical Indian home-cooking assumptions
              (average serving size, common recipe proportions, usual oil/ghee content) and
              log it immediately — you can briefly state the assumption you made in your
              reply (e.g. "assuming a medium bowl, ~250ml") so they can correct it on the
              card afterward if it's off. Only ask a clarifying question in the rare case
              where you cannot identify the dish at all (e.g. an unfamiliar name with zero
              context) — even then, still give your best guess estimate AND the log block in
              that same reply rather than blocking on an answer.
            - Once you have an estimate (it matches a known food, or you've assumed reasonable
              defaults), reply with a short friendly line confirming what you understood, followed
              immediately by EXACTLY ONE fenced block containing ONE JSON object in this form:
              ```log
              {"meals":[{"mealType":"BREAKFAST","items":[{"name":"...","quantity":1,"unit":"piece","calories":120,"proteinG":4.5,"carbsG":18.0,"fatG":3.0,"matchedKnownFood":true}]}]}
              ```
              This must always be valid JSON — a single top-level object with one "meals"
              array. If the user describes several meals at once (e.g. logging a whole day),
              put ALL of them as separate entries inside that same "meals" array — never emit
              more than one ```log block, and never write two JSON objects back to back.
              mealType must be one of BREAKFAST, LUNCH, DINNER, SNACK (pick the most likely
              one based on context/time if not stated). "calories", "proteinG", "carbsG", and
              "fatG" are all TOTALS for the quantity/unit given, not per-unit values. If an
              item matches a known food, scale its caloriesPerServing/proteinG/carbsG/fatG by
              quantity and set matchedKnownFood true; otherwise estimate all four realistically
              from your knowledge of Indian cuisine and set matchedKnownFood false.
            - Keep every reply short — a couple of sentences at most, like a text message.
            - You're told what the user has already eaten today below. Use it for context —
              e.g. if asked "what should I eat now" or "how am I doing today", answer using
              those real numbers instead of guessing. Offer a brief suggestion when it's
              naturally relevant (they're close to/over a typical daily calorie range, a meal
              is imbalanced, etc.), but don't lecture unprompted.

            Known foods: $knownFoodsJson
            Eaten today: $todaysLogSummary
        """.trimIndent()

        val contents = buildList {
            history.forEach { msg ->
                add(GeminiContent(role = if (msg.role == ChatRole.USER) "user" else "model", parts = listOf(GeminiPart(text = msg.content))))
            }
            add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = newUserText))))
        }

        val requestBody = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val body = json.encodeToString(GeminiRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val startMs = System.currentTimeMillis()
        val responseText = executeWithRetry(request)
        Log.d(TAG, "Gemini API round trip: ${System.currentTimeMillis() - startMs}ms")

        val completion = try {
            json.decodeFromString(GeminiResponse.serializer(), responseText)
        } catch (e: Exception) {
            throw GoogleApiException("Unexpected response from Gemini API.")
        }

        val content = completion.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw GoogleApiException(
                completion.promptFeedback?.blockReason?.let { "Gemini blocked the response: $it" }
                    ?: "Gemini API returned no content."
            )

        content.trim()
    }

    /**
     * Runs the request, retrying with backoff on transient failures (network hiccups,
     * rate limiting, or an overloaded upstream — 429/503/5xx) so a momentary blip doesn't
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
                // Covers UnknownHostException too — a brief DNS hiccup (common right after
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

            Log.d(TAG, "attempt $attempt got HTTP ${response.code} after ${System.currentTimeMillis() - startMs}ms")
            val transient = response.code == 429 || response.code in 500..599
            if (transient && attempt < maxAttempts) {
                delay(backoffMs(attempt))
                continue
            }
            throw GoogleApiException("Gemini API error ${response.code}: ${bodyText.take(300)}")
        }
    }

    private fun backoffMs(attempt: Int): Long = (1000L * (1L shl (attempt - 1))).coerceAtMost(8000L)

    /**
     * Suspends until the call completes, but — unlike the blocking [Call.execute] — actually
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
        // Free-tier model. gemini-2.5-flash was retired — the API itself now points
        // callers to this replacement.
        const val MODEL = "gemini-3.6-flash"
    }
}

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
private data class GeminiSystemInstruction(val parts: List<GeminiPart>)

// The Gemini 3.x line replaced 2.5's numeric thinkingBudget with a thinkingLevel enum
// ("low"/"medium"/"high") — sending the old thinkingBudget field to a 3.x model is
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
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
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
