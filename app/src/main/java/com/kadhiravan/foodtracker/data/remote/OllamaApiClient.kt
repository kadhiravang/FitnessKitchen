package com.kadhiravan.foodtracker.data.remote

import android.util.Log
import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.ChatRole
import com.kadhiravan.foodtracker.data.local.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaApiException(message: String) : IOException(message)

/**
 * Talks to a locally-running Ollama server (see ollama.com), the fast alternative when a
 * hosted provider's free tier is slow or rate-limited, everything runs on the user's own
 * machine/network instead of a shared public API. No API key, no per-turn tool-calling
 * grounding (like [NvidiaApiClient], not [GoogleApiClient]), just a direct chat completion
 * asked to reply with the same fenced ```log block [LogCardParser] expects.
 */
class OllamaApiClient : ChatApiClient {

    // encodeDefaults matters here: without it, "stream = false" (equal to its Kotlin
    // default) gets silently omitted from the encoded request body, and Ollama then
    // falls back to its own default of streaming, returning newline-delimited partial
    // chunks instead of the single JSON object this client expects.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val httpClient = OkHttpClient.Builder()
        // A local/LAN server is either up or it isn't, no point retrying a flaky public
        // DNS/network path the way NvidiaApiClient does for a hosted API.
        .connectTimeout(5, TimeUnit.SECONDS)
        // Generous: a large local model on modest hardware can genuinely take a while to
        // generate, especially cold (not yet loaded into memory) on the first call.
        .readTimeout(120, TimeUnit.SECONDS)
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
        val serverUrl = apiKey
        if (serverUrl.isBlank()) {
            throw OllamaApiException("No Ollama server URL set. Add one in Settings.")
        }
        if (ollamaModel.isBlank()) {
            throw OllamaApiException("No Ollama model set. Add one in Settings, e.g. \"llama3.1\".")
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
            add(OllamaMessage(role = "system", content = systemPrompt))
            history.forEach { msg ->
                add(OllamaMessage(role = if (msg.role == ChatRole.USER) "user" else "assistant", content = msg.content))
            }
            add(OllamaMessage(role = "user", content = newUserText))
        }

        val requestBody = OllamaChatRequest(model = ollamaModel, messages = apiMessages)
        val body = json.encodeToString(OllamaChatRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/chat")
            .post(body)
            .build()

        val startMs = System.currentTimeMillis()
        val bodyText = try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OllamaApiException("Ollama server error ${response.code}: ${text.take(500)}")
                }
                text
            }
        } catch (e: IOException) {
            throw OllamaApiException("Couldn't reach Ollama at $serverUrl, is it running? (${e.message})")
        }
        Log.d(TAG, "Ollama round trip: ${System.currentTimeMillis() - startMs}ms")

        val completion = try {
            json.decodeFromString(OllamaChatResponse.serializer(), bodyText)
        } catch (e: Exception) {
            throw OllamaApiException("Unexpected response from Ollama: ${bodyText.take(500)}")
        }

        completion.message?.content?.trim()
            ?: throw OllamaApiException("Ollama returned no content.")
    }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val TAG = "OllamaApiClient"
    }
}

@Serializable
private data class OllamaMessage(val role: String, val content: String)

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
private data class OllamaChatResponse(val message: OllamaMessage? = null)
