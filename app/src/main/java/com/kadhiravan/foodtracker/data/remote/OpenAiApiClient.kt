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

class OpenAiApiException(message: String) : IOException(message)

/**
 * Talks to OpenAI's chat completions endpoint, same fenced ```log block contract as
 * [NvidiaApiClient]/[OllamaApiClient]/[ClaudeApiClient] (no per-turn USDA tool-calling
 * here, that's [GoogleApiClient]-only for now). Model id is user-configurable rather than
 * hardcoded, OpenAI's catalog churns often enough (see project history with NVIDIA's own
 * catalog) that baking one in risks silently pointing at a retired model later.
 */
class OpenAiApiClient : ChatApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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
        openaiModel: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw OpenAiApiException("No OpenAI API key set. Add one in Settings.")
        }
        if (openaiModel.isBlank()) {
            throw OpenAiApiException("No OpenAI model set. Add one in Settings, e.g. \"gpt-4.1\".")
        }

        val knownFoodsJson = knownFoods.joinToString(prefix = "[", postfix = "]") { food ->
            """{"name":"${food.name.escapeJson()}","servingUnit":"${food.servingUnit.escapeJson()}","caloriesPerServing":${food.caloriesPerServing},"proteinG":${food.proteinG ?: 0.0},"carbsG":${food.carbsG ?: 0.0},"fatG":${food.fatG ?: 0.0}}"""
        }

        val systemPrompt = """
            You are a friendly, concise nutrition assistant specialized in Indian and South
            Indian home cooking, chatting with the user about what they ate so you can log it.

            Rules:
            - The user may write or speak in English, Tamil (Tamil script or transliterated),
              or a mix of both, food names are often native Tamil words. Understand them
              directly and always reply in English.
            - If the user's message isn't about food, just reply naturally and briefly.
            - The user usually does NOT know exact quantities, ingredients, or calorie counts
              themselves, that's why they're asking you. NEVER leave them without a number,
              and almost never ask a clarifying question. Instead, always make your own
              reasonable best-effort estimate using typical Indian home-cooking assumptions
              (average serving size, common recipe proportions, usual oil/ghee content) and
              log it immediately, you can briefly state the assumption you made in your
              reply (e.g. "assuming a medium bowl, ~250ml") so they can correct it on the
              card afterward if it's off. Only ask a clarifying question in the rare case
              where you cannot identify the dish at all (e.g. an unfamiliar name with zero
              context), even then, still give your best guess estimate AND the log block in
              that same reply rather than blocking on an answer.
            - Once you have an estimate (it matches a known food, or you've assumed reasonable
              defaults), reply with a short friendly line confirming what you understood, followed
              immediately by EXACTLY ONE fenced block containing ONE JSON object in this form:
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
              quantity and set matchedKnownFood true; otherwise estimate all four realistically
              from your knowledge of Indian cuisine and set matchedKnownFood false.
            - Keep every reply short, a couple of sentences at most, like a text message.
            - You're told what the user has already eaten today below. Use it for context,
              e.g. if asked "what should I eat now" or "how am I doing today", answer using
              those real numbers instead of guessing. Offer a brief suggestion when it's
              naturally relevant (they're close to/over a typical daily calorie range, a meal
              is imbalanced, etc.), but don't lecture unprompted.

            Known foods: $knownFoodsJson
            Eaten today: $todaysLogSummary
        """.trimIndent()

        val apiMessages = buildList {
            add(OpenAiMessage(role = "system", content = systemPrompt))
            history.forEach { msg ->
                add(OpenAiMessage(role = if (msg.role == ChatRole.USER) "user" else "assistant", content = msg.content))
            }
            add(OpenAiMessage(role = "user", content = newUserText))
        }

        val requestBody = OpenAiChatRequest(model = openaiModel, messages = apiMessages)
        val body = json.encodeToString(OpenAiChatRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val startMs = System.currentTimeMillis()
        val responseText = executeWithRetry(request)
        Log.d(TAG, "OpenAI API round trip: ${System.currentTimeMillis() - startMs}ms")

        val completion = try {
            json.decodeFromString(OpenAiChatResponse.serializer(), responseText)
        } catch (e: Exception) {
            throw OpenAiApiException("Unexpected response from OpenAI API.")
        }

        completion.choices.firstOrNull()?.message?.content?.trim()
            ?: throw OpenAiApiException("OpenAI API returned no content.")
    }

    private suspend fun executeWithRetry(request: Request): String {
        val maxAttempts = 6
        var attempt = 0
        val startMs = System.currentTimeMillis()
        while (true) {
            attempt++
            val response = try {
                executeCancellable(request)
            } catch (e: IOException) {
                Log.d(TAG, "attempt $attempt failed after ${System.currentTimeMillis() - startMs}ms: ${e.javaClass.simpleName}: ${e.message}")
                if (attempt >= maxAttempts) {
                    throw OpenAiApiException("Network error talking to OpenAI API: ${e.message}")
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
            throw OpenAiApiException("OpenAI API error ${response.code}: ${bodyText.take(500)}")
        }
    }

    private fun backoffMs(attempt: Int): Long = (1000L * (1L shl (attempt - 1))).coerceAtMost(8000L)

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
        const val TAG = "OpenAiApiClient"
    }
}

@Serializable
private data class OpenAiMessage(val role: String, val content: String)

@Serializable
private data class OpenAiChatRequest(val model: String, val messages: List<OpenAiMessage>)

@Serializable
private data class OpenAiChatResponse(val choices: List<OpenAiChoice> = emptyList())

@Serializable
private data class OpenAiChoice(val message: OpenAiMessage)
