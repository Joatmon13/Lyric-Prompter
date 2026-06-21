package com.lyricprompter.tracking

import com.lyricprompter.domain.usecase.ProcessLyricsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for manual tap-to-advance: forcing the next line's prompt when
 * recognition has fallen behind, bypassing match/silence/cooldown/marker gates.
 */
class PositionTrackerManualAdvanceTest {

    private fun newTracker() = PositionTracker(FuzzyMatcher(), PromptTrigger())
    private val process = ProcessLyricsUseCase()

    @Test
    fun `advanceManually forces the next line's prompt without any recognition`() {
        val tracker = newTracker()
        tracker.loadSong(process.process("line one\nline two\nline three", "T", "A"))

        val event = tracker.advanceManually()

        assertTrue(event is PromptEvent.SpeakPrompt)
        event as PromptEvent.SpeakPrompt
        assertEquals(0, event.lineIndex)
        assertEquals("line two", event.promptText) // prompt = next line's text
    }

    @Test
    fun `repeated advances walk through the song then finish then stop`() {
        val tracker = newTracker()
        tracker.loadSong(process.process("line one\nline two\nline three", "T", "A"))

        assertEquals(0, (tracker.advanceManually() as PromptEvent.SpeakPrompt).lineIndex)
        assertEquals(1, (tracker.advanceManually() as PromptEvent.SpeakPrompt).lineIndex)
        // Last line has no following line -> song finished.
        assertEquals(PromptEvent.SongFinished, tracker.advanceManually())
        // Past the end -> nothing.
        assertNull(tracker.advanceManually())
    }

    @Test
    fun `advanceManually speaks even on a line that carries no marker`() {
        // Only line one has a // marker, so auto mode would not speak line two.
        val tracker = newTracker()
        tracker.loadSong(process.process("line one //\nline two\nline three", "T", "A"))

        tracker.advanceManually() // line 0 (marked)
        val event = tracker.advanceManually() // line 1 (unmarked) - must still speak

        assertTrue(event is PromptEvent.SpeakPrompt)
        assertEquals(1, (event as PromptEvent.SpeakPrompt).lineIndex)
    }
}
