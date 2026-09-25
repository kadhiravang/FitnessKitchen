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

class NvidiaApiException(message: String, val httpCode: Int? = null) : IOException(message)

/**
 * Talks to NVIDIA's OpenAI-compatible chat completions endpoint to hold a running
 * conversation about what the user ate. The assistant either asks a clarifying
 * question (plain text) or, once confident, replies with a short line plus a
 * fenced ```log block that [LogCardParser] turns into a confirmable food card , 
 * grounded against the user's own food catalog so known dishes get accurate
 * calories instead of guesses.
 */
class NvidiaApiClient : ChatApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Generous, a growing chat history means a bigger prompt each turn, and this
        // model can take a while to respond under load. 45s was too tight in practice.
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
        openaiModel: String,
        ollamaCloudModel: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw NvidiaApiException("No NVIDIA API key set. Add one in Settings.")
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

        val apiMessages = buildList {
            add(ApiChatMessage(role = "system", content = systemPrompt))
            history.forEach { msg ->
                add(ApiChatMessage(role = if (msg.role == ChatRole.USER) "user" else "assistant", content = msg.content))
            }
            add(ApiChatMessage(role = "user", content = newUserText))
        }

        fun buildRequest(model: String): Request {
            val body = json.encodeToString(
                ChatCompletionRequest.serializer(),
                ChatCompletionRequest(model = model, messages = apiMessages)
            ).toRequestBody("application/json".toMediaType())
            return Request.Builder()
                .url("https://integrate.api.nvidia.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        }

        val startMs = System.currentTimeMillis()
        val responseText = try {
            executeWithRetry(buildRequest(activeModel))
        } catch (e: NvidiaApiException) {
            // 404/410 on chat completions means NVIDIA retired this model id (it has happened
            // three times now), so look up whatever DeepSeek flash model is live and retry once
            // instead of leaving the user stuck until the app is updated.
            if (e.httpCode != 404 && e.httpCode != 410) throw e
            val replacement = findLiveModel()?.takeIf { it != activeModel } ?: throw e
            Log.d(TAG, "model $activeModel retired, switching to $replacement")
            val text = executeWithRetry(buildRequest(replacement))
            activeModel = replacement
            text
        }
        Log.d(TAG, "NVIDIA API round trip: ${System.currentTimeMillis() - startMs}ms")

        val completion = try {
            json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
        } catch (e: Exception) {
            throw NvidiaApiException("Unexpected response from NVIDIA API: ${responseText.take(500)}")
        }

        val content = completion.choices.firstOrNull()?.message?.content
            ?: throw NvidiaApiException("NVIDIA API returned no content.")

        content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }

    /**
     * Runs the request, retrying with backoff on transient failures (network hiccups,
     * rate limiting, or an overloaded upstream, 429/5xx/529) so a momentary blip doesn't
     * force the user to manually resend. Non-transient failures (bad key, bad request)
     * fail immediately.
     */
    private suspend fun executeWithRetry(request: Request): String {
        // Each failed attempt can already cost several seconds fighting a flaky DNS
        // resolver (see ResilientDns) before backoff even starts, so a low attempt count
        // used to burn the whole retry budget on DNS alone, leaving none to ride out a
        // separately transient NVIDIA-side overload (429/529/5xx). More attempts with a
        // capped backoff gives both problems room to resolve within one exchange.
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
                    throw NvidiaApiException("Network error talking to NVIDIA API: ${e.message}")
                }
                delay(backoffMs(attempt))
                continue
            }
            val bodyText = response.use { it.body?.string().orEmpty() }
            if (response.isSuccessful) return bodyText

            Log.d(TAG, "attempt $attempt got HTTP ${response.code} after ${System.currentTimeMillis() - startMs}ms")
            val transient = response.code == 429 || response.code == 529 || response.code in 500..599
            if (transient && attempt < maxAttempts) {
                delay(backoffMs(attempt))
                continue
            }
            throw NvidiaApiException("NVIDIA API error ${response.code}: ${bodyText.take(300)}", response.code)
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

    /** The catalog listing needs no auth. Newest DeepSeek "flash" id wins (descending sort puts
     * "v4.1-flash" above "v4-flash-0731"). */
    private suspend fun findLiveModel(): String? = try {
        val request = Request.Builder().url("https://integrate.api.nvidia.com/v1/models").get().build()
        val text = executeWithRetry(request)
        json.decodeFromString(ModelListResponse.serializer(), text).data
            .map { it.id }
            .filter { it.startsWith("deepseek-ai/deepseek-v") && it.contains("flash") }
            .maxOrNull()
    } catch (e: Exception) {
        null
    }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    // Resolved lazily and remembered for the process lifetime once a retired id is replaced.
    @Volatile
    private var activeModel = DEFAULT_MODEL

    private companion object {
        const val TAG = "NvidiaApiClient"
        const val DEFAULT_MODEL = "deepseek-ai/deepseek-v4.1-flash"
    }
}

@Serializable
private data class ApiChatMessage(val role: String, val content: String)

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 1024,
    // This model defaults to an extended "thinking" reasoning pass unless told otherwise,
    // which was both slow and, combined with a smaller max_tokens, could burn the whole
    // token budget on reasoning before ever emitting the actual reply/log block, leaving the
    // user with no calorie estimate at all. Disabled for a fast, direct answer every time.
    val chat_template_kwargs: ChatTemplateKwargs = ChatTemplateKwargs()
)

@Serializable
private data class ChatTemplateKwargs(val thinking: Boolean = false)

@Serializable
private data class ModelListResponse(val data: List<ModelListEntry> = emptyList())

@Serializable
private data class ModelListEntry(val id: String)

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatCompletionChoice>)

@Serializable
private data class ChatCompletionChoice(val message: ChatMessageContent)

@Serializable
private data class ChatMessageContent(val content: String)
