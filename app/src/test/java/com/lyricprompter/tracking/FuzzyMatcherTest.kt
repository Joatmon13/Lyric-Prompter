package com.lyricprompter.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FuzzyMatcher] - the LCS-based matching used to decide how well
 * recognized speech lines up with an expected lyric line.
 */
class FuzzyMatcherTest {

    private lateinit var matcher: FuzzyMatcher

    @Before
    fun setUp() {
        matcher = FuzzyMatcher()
    }

    private fun words(s: String) = s.split(" ")

    // --- matchScore basics ---

    @Test
    fun `matchScore returns 1 when all line words are present in order`() {
        val score = matcher.matchScore(
            words("is this the real life"),
            words("is this the real life")
        )
        assertEquals(1.0f, score, 0.0001f)
    }

    @Test
    fun `matchScore is fraction of line words matched`() {
        // 4 of 5 line words appear -> 0.8
        val score = matcher.matchScore(
            words("is this the real"),
            words("is this the real life")
        )
        assertEquals(0.8f, score, 0.0001f)
    }

    @Test
    fun `matchScore returns 0 for empty inputs`() {
        assertEquals(0f, matcher.matchScore(emptyList(), words("a b c")), 0f)
        assertEquals(0f, matcher.matchScore(words("a b c"), emptyList()), 0f)
    }

    @Test
    fun `matchScore tolerates an extra recognized word in the middle`() {
        // "the real life" expected; recognized has a spurious "uh" in the middle.
        val score = matcher.matchScore(
            words("the uh real life"),
            words("the real life")
        )
        assertEquals(1.0f, score, 0.0001f)
    }

    // --- fuzzy word matching (stem / phonetic / edit distance) ---

    @Test
    fun `plural and verb forms match the same stem`() {
        // "skies" should match "sky", "walking" should match "walk"
        assertTrue(matcher.matchScore(words("skies"), words("sky")) >= 1.0f)
        assertTrue(matcher.matchScore(words("walking"), words("walk")) >= 1.0f)
    }

    @Test
    fun `minor misrecognition within edit distance still matches`() {
        // single-character slip on a longer word
        assertTrue(matcher.matchScore(words("fantacy"), words("fantasy")) >= 1.0f)
    }

    @Test
    fun `very short words (under 3 chars) require exact match`() {
        // MIN_FUZZY_WORD_LENGTH = 3, so 2-letter words must match exactly.
        // "of" must not fuzzy-match "to".
        val score = matcher.matchScore(words("of"), words("to"))
        assertEquals(0f, score, 0.0001f)
    }

    @Test
    fun `completely different words do not match`() {
        val score = matcher.matchScore(words("banana"), words("elephant"))
        assertEquals(0f, score, 0.0001f)
    }

    // --- findBestMatch over a window ---

    @Test
    fun `findBestMatch picks the highest scoring line in the window`() {
        val lines = listOf(
            words("hello darkness my old friend"),
            words("is this the real life"),
            words("here comes the sun")
        )

        val result = matcher.findBestMatch(words("is this the real life"), lines, 0..2)
        assertEquals(1, result?.first)
        assertEquals(1.0f, result?.second ?: 0f, 0.0001f)
    }

    @Test
    fun `findBestMatch clamps an out-of-bounds window`() {
        val lines = listOf(words("only line here"))
        val result = matcher.findBestMatch(words("only line here"), lines, -5..99)
        assertEquals(0, result?.first)
    }

    @Test
    fun `findBestMatch returns null when nothing matches`() {
        val lines = listOf(words("alpha beta"), words("gamma delta"))
        assertNull(matcher.findBestMatch(words("zzz qqq"), lines, 0..1))
    }

    @Test
    fun `findBestMatch returns null for empty recognized words`() {
        val lines = listOf(words("alpha beta"))
        assertNull(matcher.findBestMatch(emptyList(), lines, 0..0))
    }

    // --- matchesFromStart ---

    @Test
    fun `matchesFromStart detects the opening of a new line`() {
        assertTrue(
            matcher.matchesFromStart(
                recognizedWords = words("blah blah is this"),
                lineWords = words("is this the real life")
            )
        )
    }

    @Test
    fun `matchesFromStart is false when the opening differs`() {
        assertFalse(
            matcher.matchesFromStart(
                recognizedWords = words("here comes the"),
                lineWords = words("is this the real life")
            )
        )
    }
}
