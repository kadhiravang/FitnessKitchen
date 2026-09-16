package com.kadhiravan.foodtracker.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class NutritionFacts(
    val description: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double
)

/**
 * Looks up real, published nutrition data from USDA FoodData Central — the grounding
 * source the chat model reaches for (via function calling — see [GoogleApiClient]) on
 * foods it doesn't already know from the user's own catalog. Free API key, 1,000
 * requests/hour once signed up (fdc.nal.usda.gov/api-key-signup).
 *
 * Values from FDC's `foodNutrients` array are always normalized per 100g regardless of
 * a food's actual serving size — that's FDC's own convention, not something computed
 * here — so callers scale by however many grams the user actually ate.
 */
class UsdaNutritionClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(query: String, apiKey: String): NutritionFacts? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) return@withContext null

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search" +
            "?api_key=$apiKey&query=$encodedQuery&pageSize=3"
        val request = Request.Builder().url(url).get().build()

        val bodyText = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        runCatching {
            val root = Json.parseToJsonElement(bodyText).jsonObject
            val candidates = root["foods"]?.jsonArray.orEmpty()

            // FDC's search is keyword-based, not semantic — "chicken 65" has matched a
            // sunflower-oil product because both mention "65", which would silently hand
            // back oil's calorie density for a fried-chicken dish. Requiring the query and
            // the matched description to share an actual (non-numeric) word is a cheap but
            // effective guard against that class of nonsense match; if the top hit fails
            // it, the next candidates are tried before giving up.
            val queryWords = significantWords(query)
            val relevantFood = candidates.firstOrNull { candidate ->
                val description = candidate.jsonObject["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                queryWords.isNotEmpty() && significantWords(description).any { it in queryWords }
            }?.jsonObject ?: return@runCatching null

            val nutrients = relevantFood["foodNutrients"]?.jsonArray ?: return@runCatching null

            fun valueFor(nutrientId: Int): Double? = nutrients
                .firstOrNull { it.jsonObject["nutrientId"]?.jsonPrimitive?.intOrNull == nutrientId }
                ?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull

            // 1008 = Energy (kcal), 1003 = Protein, 1004 = Total lipid (fat),
            // 1005 = Carbohydrate, by difference — FDC's standard nutrient IDs.
            val calories = valueFor(1008) ?: return@runCatching null
            NutritionFacts(
                description = relevantFood["description"]?.jsonPrimitive?.contentOrNull ?: query,
                caloriesPer100g = calories.roundToInt(),
                proteinPer100g = valueFor(1003) ?: 0.0,
                carbsPer100g = valueFor(1005) ?: 0.0,
                fatPer100g = valueFor(1004) ?: 0.0
            )
        }.getOrNull()
    }

    private fun significantWords(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filterTo(mutableSetOf()) { it.length > 2 && it.any(Char::isLetter) && it !in STOPWORDS }

    private companion object {
        val STOPWORDS = setOf("the", "and", "for", "with", "from", "your")
    }
}
