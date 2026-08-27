package com.kadhiravan.foodtracker.data.remote

import com.kadhiravan.foodtracker.data.local.FoodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class NvidiaApiException(message: String) : IOException(message)

/**
 * Talks to NVIDIA's OpenAI-compatible chat completions endpoint to turn a spoken
 * transcript into a structured list of foods + calories, grounded against the
 * user's own food catalog so known dishes get accurate calories instead of guesses.
 */
class NvidiaApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun parseTranscript(
        transcript: String,
        apiKey: String,
        knownFoods: List<FoodItem>
    ): List<ParsedFoodItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw NvidiaApiException("No NVIDIA API key set. Add one in Settings.")
        }

        val knownFoodsJson = knownFoods.joinToString(prefix = "[", postfix = "]") { food ->
            """{"name":"${food.name.escapeJson()}","servingUnit":"${food.servingUnit.escapeJson()}","caloriesPerServing":${food.caloriesPerServing}}"""
        }

        val systemPrompt = """
            You are a nutrition parser specialized in Indian and South Indian home cooking.
            You will be given a spoken transcript of foods someone just ate, and a JSON list
            of foods the user already tracks with known calories per serving.

            Rules:
            - Split the transcript into individual food items.
            - If an item matches (or closely matches) a known food, use its caloriesPerServing
              to compute the total calories for the quantity mentioned, and set matchedKnownFood true.
            - If an item does not match any known food, estimate realistic calories for a typical
              home-cooked serving using your knowledge of Indian cuisine, and set matchedKnownFood false.
            - "calories" must be the TOTAL calories for the quantity/unit given, not a per-unit value.
            - Infer sensible quantities/units even if the speaker didn't state them explicitly (e.g. "a dosa" -> quantity 1, unit "piece").
            - Respond with STRICT JSON ONLY: a JSON array of objects with exactly these keys:
              name (string), quantity (number), unit (string), calories (integer), matchedKnownFood (boolean).
            - No markdown, no code fences, no explanation, no extra text outside the JSON array.

            Known foods: $knownFoodsJson
        """.trimIndent()

        val requestBody = ChatCompletionRequest(
            model = "deepseek-ai/deepseek-v4-flash-0731",
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = transcript)
            )
        )

        val body = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw NvidiaApiException("Network error talking to NVIDIA API: ${e.message}")
        }

        val responseText = response.use { r ->
            val bodyText = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw NvidiaApiException("NVIDIA API error ${r.code}: ${bodyText.take(300)}")
            }
            bodyText
        }

        val completion = try {
            json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
        } catch (e: Exception) {
            throw NvidiaApiException("Unexpected response from NVIDIA API.")
        }

        val content = completion.choices.firstOrNull()?.message?.content
            ?: throw NvidiaApiException("NVIDIA API returned no content.")

        val cleaned = content
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        try {
            json.decodeFromString<List<ParsedFoodItem>>(cleaned)
        } catch (e: Exception) {
            throw NvidiaApiException("Couldn't understand the food list the AI returned. Try rephrasing.")
        }
    }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val max_tokens: Int = 1024
)

@Serializable
private data class ChatCompletionResponse(val choices: List<ChatCompletionChoice>)

@Serializable
private data class ChatCompletionChoice(val message: ChatMessageContent)

@Serializable
private data class ChatMessageContent(val content: String)
