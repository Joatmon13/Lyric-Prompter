package com.lyricprompter.tracking

import javax.inject.Inject
import kotlin.math.min

/**
 * Fuzzy matching algorithm for comparing recognized words against lyric lines.
 * Uses Longest Common Subsequence (LCS) with approximate word matching to handle
 * missed, misrecognized, or phonetically similar words.
 */
class FuzzyMatcher @Inject constructor() {

    companion object {
        // Levenshtein distance threshold as a ratio of word length
        // E.g., for a 5-letter word, allow up to 2 edits (5 * 0.4 = 2)
        private const val EDIT_DISTANCE_RATIO = 0.4f

        // Minimum word length for fuzzy matching (short words must be exact)
        private const val MIN_FUZZY_WORD_LENGTH = 3
    }

    /**
     * Calculate match score between recognized words and a lyric line's words.
     *
     * @param recognizedWords Words recognized from speech (rolling buffer)
     * @param lineWords Words from a lyric line (normalized)
     * @return Match score from 0.0 to 1.0 (percentage of line words matched)
     */
    fun matchScore(recognizedWords: List<String>, lineWords: List<String>): Float {
        if (lineWords.isEmpty()) return 0f
        if (recognizedWords.isEmpty()) return 0f

        val lcsLength = longestCommonSubsequenceLengthFuzzy(recognizedWords, lineWords)
        return lcsLength.toFloat() / lineWords.size
    }

    /**
     * Find the best matching line within a search window.
     *
     * @param recognizedWords Words recognized from speech
     * @param lineWordsList List of word lists for each lyric line
     * @param searchWindow Range of line indices to search
     * @return Pair of (line index, match score) or null if no match found
     */
    fun findBestMatch(
        recognizedWords: List<String>,
        lineWordsList: List<List<String>>,
        searchWindow: IntRange
    ): Pair<Int, Float>? {
        if (recognizedWords.isEmpty()) return null

        val validRange = searchWindow.first.coerceAtLeast(0)..
            searchWindow.last.coerceAtMost(lineWordsList.lastIndex)

        if (validRange.isEmpty()) return null

        var bestIndex = -1
        var bestScore = 0f

        for (index in validRange) {
            val score = matchScore(recognizedWords, lineWordsList[index])
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        return if (bestIndex >= 0 && bestScore > 0f) {
            bestIndex to bestScore
        } else {
            null
        }
    }

    /**
     * Calculate the length of the Longest Common Subsequence between two word lists,
     * using fuzzy word matching instead of exact equality.
     * Uses dynamic programming for O(m*n) time complexity.
     */
    private fun longestCommonSubsequenceLengthFuzzy(
        recognized: List<String>,
        line: List<String>
    ): Int {
        val m = recognized.size
        val n = line.size

        // DP table: dp[i][j] = LCS length for recognized[0..i-1] and line[0..j-1]
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (wordsMatch(recognized[i - 1], line[j - 1])) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        return dp[m][n]
    }

    /**
     * Check if two words match, using fuzzy matching for longer words.
     * Short words (< 3 chars) require exact match to avoid false positives.
     */
    private fun wordsMatch(recognized: String, expected: String): Boolean {
        // Exact match is always good
        if (recognized == expected) return true

        // Short words must be exact (too many false positives otherwise)
        if (expected.length < MIN_FUZZY_WORD_LENGTH) return false

        // Check if words share the same root (handles plurals, verb forms)
        if (sharesStem(recognized, expected)) return true

        // Calculate allowed edit distance based on word length
        val maxDistance = (expected.length * EDIT_DISTANCE_RATIO).toInt().coerceAtLeast(1)
        val distance = levenshteinDistance(recognized, expected)

        return distance <= maxDistance
    }

    /**
     * Check if two words share a common stem (handles plurals, verb conjugations).
     * E.g., "sky" matches "skies", "walk" matches "walking"
     */
    private fun sharesStem(word1: String, word2: String): Boolean {
        val shorter = if (word1.length <= word2.length) word1 else word2
        val longer = if (word1.length <= word2.length) word2 else word1

        // If shorter word is a prefix of longer (minus common suffixes)
        if (shorter.length >= 3 && longer.startsWith(shorter)) {
            val suffix = longer.substring(shorter.length)
            // Common English suffixes
            if (suffix in listOf("s", "es", "ed", "ing", "er", "est", "ly", "'s")) {
                return true
            }
        }

        // Handle vowel changes in plurals (sky -> skies, baby -> babies)
        if (shorter.length >= 3 && longer.length >= 3) {
            val shorterBase = shorter.dropLast(1)
            val longerBase = longer.dropLast(3)
            if (shorterBase.length >= 2 && longerBase == shorterBase) {
                // sky -> ski (from skies), baby -> bab (from babies)
                return true
            }
        }

        return false
    }

    /**
     * Calculate Levenshtein (edit) distance between two strings.
     * Returns the minimum number of single-character edits needed.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Optimization: if length difference is too big, skip calculation
        if (kotlin.math.abs(m - n) > (n * EDIT_DISTANCE_RATIO).toInt() + 1) {
            return maxOf(m, n) // Return max to indicate no match
        }

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Check if a word sequence starts matching from the beginning of a line.
     * Useful for detecting when a performer starts a new line.
     */
    fun matchesFromStart(
        recognizedWords: List<String>,
        lineWords: List<String>,
        minMatchWords: Int = 2
    ): Boolean {
        if (lineWords.size < minMatchWords) return false
        if (recognizedWords.size < minMatchWords) return false

        // Check if the last N recognized words match the first N line words
        val recentWords = recognizedWords.takeLast(minMatchWords)
        val startWords = lineWords.take(minMatchWords)

        return recentWords == startWords
    }
}
