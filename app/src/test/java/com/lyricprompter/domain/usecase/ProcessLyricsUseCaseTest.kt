package com.lyricprompter.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProcessLyricsUseCase] - lyrics cleaning, line splitting,
 * word extraction, prompt generation, and `//` / `//N` marker handling.
 */
class ProcessLyricsUseCaseTest {

    private lateinit var useCase: ProcessLyricsUseCase

    @Before
    fun setUp() {
        useCase = ProcessLyricsUseCase()
    }

    // --- Line splitting & cleaning ---

    @Test
    fun `process splits lyrics into one line per non-blank line`() {
        val song = useCase.process(
            rawLyrics = "Line one\nLine two\nLine three",
            title = "T",
            artist = "A"
        )

        assertEquals(3, song.lines.size)
        assertEquals("Line one", song.lines[0].text)
        assertEquals("Line three", song.lines[2].text)
    }

    @Test
    fun `process strips section markers like Verse and Chorus`() {
        val song = useCase.process(
            rawLyrics = "[Verse 1]\nReal line\n[Chorus]\nAnother line",
            title = "T",
            artist = "A"
        )

        assertEquals(2, song.lines.size)
        assertEquals("Real line", song.lines[0].text)
        assertEquals("Another line", song.lines[1].text)
    }

    @Test
    fun `process strips repeat annotations`() {
        val song = useCase.process(
            rawLyrics = "Sing this part (x2)\nThen this (repeat)",
            title = "T",
            artist = "A"
        )

        assertEquals("Sing this part", song.lines[0].text)
        assertEquals("Then this", song.lines[1].text)
    }

    @Test
    fun `process trims title and artist`() {
        val song = useCase.process("A line", "  Wonderwall  ", "  Oasis  ")
        assertEquals("Wonderwall", song.title)
        assertEquals("Oasis", song.artist)
    }

    // --- Word extraction ---

    @Test
    fun `extractWords lowercases and strips punctuation but keeps contractions`() {
        val song = useCase.process("Don't STOP, believin'!", "T", "A")
        assertEquals(listOf("don't", "stop", "believin'"), song.lines[0].words)
    }

    @Test
    fun `vocabulary contains all unique words across lines`() {
        val song = useCase.process("the real life\nthe real fantasy", "T", "A")
        assertEquals(setOf("the", "real", "life", "fantasy"), song.vocabulary)
    }

    // --- Prompt generation ---

    @Test
    fun `promptText holds the NEXT line full text by default`() {
        val song = useCase.process(
            rawLyrics = "Is this the real life\nIs this just fantasy",
            title = "T",
            artist = "A"
        )

        assertEquals("Is this just fantasy", song.lines[0].promptText)
    }

    @Test
    fun `last line has empty promptText`() {
        val song = useCase.process("First line\nLast line", "T", "A")
        assertEquals("", song.lines.last().promptText)
    }

    @Test
    fun `promptWordCount limits prompt to first N words of next line`() {
        val song = useCase.process(
            rawLyrics = "First line here\nCaught in a landslide no escape",
            title = "T",
            artist = "A",
            promptWordCount = 2
        )

        assertEquals("Caught in", song.lines[0].promptText)
    }

    // --- Marker parsing: //  and  //N ---

    @Test
    fun `no markers means every line prompts (backward compatible)`() {
        val song = useCase.process("Line one\nLine two", "T", "A")
        assertTrue(song.lines.all { it.hasPromptMarker })
        assertNull(song.lines[0].cooldownBeats)
    }

    @Test
    fun `bare slash marker flags the line and uses default cooldown`() {
        val song = useCase.process(
            rawLyrics = "Prompt me //\nDo not prompt me",
            title = "T",
            artist = "A"
        )

        assertTrue(song.lines[0].hasPromptMarker)
        assertFalse(song.lines[1].hasPromptMarker)
        assertNull(song.lines[0].cooldownBeats) // bare // -> default
    }

    @Test
    fun `slash-N marker captures the beat count`() {
        val song = useCase.process(
            rawLyrics = "Instrumental break //8\nNext",
            title = "T",
            artist = "A"
        )

        assertEquals(8, song.lines[0].cooldownBeats)
    }

    @Test
    fun `slash-N beats are clamped to 1 through 16`() {
        val song = useCase.process("Way too long //99\nNext", "T", "A")
        assertEquals(16, song.lines[0].cooldownBeats)
    }

    @Test
    fun `marker is stripped from words and prompt text but kept in display text`() {
        val song = useCase.process(
            rawLyrics = "Is this the real life //4\nIs this just fantasy",
            title = "T",
            artist = "A"
        )

        // Display keeps the marker (visible in editor)...
        assertEquals("Is this the real life //4", song.lines[0].text)
        // ...but words and the spoken prompt do not contain it.
        assertEquals(listOf("is", "this", "the", "real", "life"), song.lines[0].words)
        assertEquals("Is this just fantasy", song.lines[0].promptText)
        assertFalse(song.vocabulary.contains("4"))
    }

    // --- Regression: marker must not leak into prompt when re-deriving word count ---

    @Test
    fun `updatePromptWordCount does not leak slash-N marker into prompt`() {
        val song = useCase.process(
            rawLyrics = "Is this the real life //2\nIs this just fantasy //3",
            title = "T",
            artist = "A"
        )

        val updated = useCase.updatePromptWordCount(song, promptWordCount = 0)

        // The prompt for line 0 is line 1's text - must be clean, no "//3".
        assertEquals("Is this just fantasy", updated.lines[0].promptText)
        assertFalse(updated.lines[0].promptText.contains("/"))
    }

    @Test
    fun `updatePromptWordCount respects word count limit`() {
        val song = useCase.process(
            rawLyrics = "First line\nCaught in a landslide",
            title = "T",
            artist = "A"
        )

        val updated = useCase.updatePromptWordCount(song, promptWordCount = 2)
        assertEquals("Caught in", updated.lines[0].promptText)
    }

    // --- Reprocessing ---

    @Test
    fun `reprocess keeps id and metadata but replaces lines`() {
        val original = useCase.process("Old line", "My Song", "My Artist")
        val reprocessed = useCase.reprocess(original, "New line one\nNew line two")

        assertEquals(original.id, reprocessed.id)
        assertEquals("My Song", reprocessed.title)
        assertEquals(2, reprocessed.lines.size)
        assertEquals("New line one", reprocessed.lines[0].text)
    }
}
