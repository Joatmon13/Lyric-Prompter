package com.lyricprompter.tracking

import javax.inject.Inject

/**
 * Fuzzy matching algorithm for comparing recognized words against lyric lines.
 * Uses Longest Common Subsequence (LCS) to handle missed or misrecognized words.
 */
class FuzzyMatcher @Inject constructor() {

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

        val lcsLength = longestCommonSubsequenceLength(recognizedWords, lineWords)
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
     * Calculate the length of the Longest Common Subsequence between two word lists.
     * Uses dynamic programming for O(m*n) time complexity.
     */
    private fun longestCommonSubsequenceLength(
        recognized: List<String>,
        line: List<String>
    ): Int {
        val m = recognized.size
        val n = line.size

        // DP table: dp[i][j] = LCS length for recognized[0..i-1] and line[0..j-1]
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (recognized[i - 1] == line[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
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
