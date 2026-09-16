package com.kadhiravan.foodtracker.util

import com.kadhiravan.foodtracker.data.local.FoodItem

/**
 * Deterministic fuzzy matching of a chat-parsed food name against the user's own food
 * catalog — used so a dish logged before gets its exact stored calories/macros reused
 * instead of trusting the model to both recognize the match *and* re-estimate numbers for
 * it consistently every time. The model is still what identifies the food/quantity from
 * free text; this only replaces its guess at the nutrition numbers once app code confirms
 * it's (almost certainly) the same dish as something already in the catalog.
 */
object FoodMatcher {

    /** Below this combined similarity, two names are treated as different dishes rather
     * than risk silently substituting the wrong food's numbers. */
    private const val MIN_SCORE = 0.72

    fun findBestMatch(name: String, knownFoods: List<FoodItem>): FoodItem? {
        val query = normalize(name)
        if (query.isBlank()) return null
        var best: FoodItem? = null
        var bestScore = 0.0
        for (food in knownFoods) {
            val score = similarity(query, normalize(food.name))
            if (score > bestScore) {
                bestScore = score
                best = food
            }
        }
        return best.takeIf { bestScore >= MIN_SCORE }
    }

    /** True when two unit strings almost certainly mean the same thing (both "serving",
     * both "g", "piece" vs "pieces", etc.) — scaling a matched food's per-serving numbers
     * by raw quantity only makes sense when the units actually line up. */
    fun unitsCompatible(a: String, b: String): Boolean {
        val na = normalizeUnit(a)
        val nb = normalizeUnit(b)
        return na.isNotBlank() && na == nb
    }

    private fun normalizeUnit(unit: String): String =
        unit.lowercase().trim().removeSuffix("s")

    private fun normalize(s: String): String =
        s.lowercase().trim().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ")

    /** Blends word-overlap (robust to extra/missing descriptor words) with character-level
     * edit distance (robust to minor spelling/transliteration differences) — food names are
     * short enough that either signal alone gives too many false positives or negatives. */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val tokensA = a.split(" ").toSet()
        val tokensB = b.split(" ").toSet()
        val jaccard = tokensA.intersect(tokensB).size.toDouble() / tokensA.union(tokensB).size.toDouble()

        val maxLen = maxOf(a.length, b.length)
        val charSim = 1.0 - levenshtein(a, b).toDouble() / maxLen

        return 0.6 * jaccard + 0.4 * charSim
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
