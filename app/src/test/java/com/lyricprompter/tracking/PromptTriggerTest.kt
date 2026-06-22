package com.lyricprompter.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PromptTrigger] - the decision of WHEN to fire a prompt.
 *
 * Encodes the behaviour converged on in TESTING_LOG.md Issues 4c-4f and 9:
 * fire as soon as the match meets the song's triggerPercent (partial OR final,
 * no longer waiting for silence — that added end-of-line lag), gated only by
 * the per-line BPM-derived cooldown.
 */
class PromptTriggerTest {

    private lateinit var trigger: PromptTrigger

    @Before
    fun setUp() {
        trigger = PromptTrigger()
    }

    // --- calculateCooldownMs (pure beat -> ms math) ---

    @Test
    fun `cooldown is beats times 60 over bpm in millis`() {
        assertEquals(1000L, PromptTrigger.calculateCooldownMs(beats = 2, bpm = 120))
        assertEquals(2000L, PromptTrigger.calculateCooldownMs(beats = 4, bpm = 120))
        assertEquals(8000L, PromptTrigger.calculateCooldownMs(beats = 8, bpm = 60))
    }

    @Test
    fun `cooldown falls back to default when bpm is missing or invalid`() {
        assertEquals(1000L, PromptTrigger.calculateCooldownMs(beats = 4, bpm = null))
        assertEquals(1000L, PromptTrigger.calculateCooldownMs(beats = 4, bpm = 0))
        assertEquals(1000L, PromptTrigger.calculateCooldownMs(beats = 4, bpm = -10))
    }

    // --- shouldPrompt gates ---

    @Test
    fun `fires when silence and threshold met and no prior prompt`() {
        val fired = trigger.shouldPrompt(
            lineIndex = 0,
            matchScore = 0.8f,
            triggerPercent = 70,
            lastPromptedLine = -1,
            isFinal = true
        )
        assertTrue(fired)
    }

    @Test
    fun `does not fire below the trigger threshold`() {
        val fired = trigger.shouldPrompt(
            lineIndex = 0,
            matchScore = 0.5f,
            triggerPercent = 70,
            lastPromptedLine = -1,
            isFinal = true
        )
        assertFalse(fired)
    }

    @Test
    fun `fires on a partial result once the threshold is met`() {
        // Issue 9: no longer wait for silence - fire while finishing the line
        // so the prompt is not late. A partial that meets threshold triggers.
        val fired = trigger.shouldPrompt(
            lineIndex = 0,
            matchScore = 1.0f,
            triggerPercent = 70,
            lastPromptedLine = -1,
            isFinal = false
        )
        assertTrue(fired)
    }

    @Test
    fun `does not fire on a partial below the threshold`() {
        val fired = trigger.shouldPrompt(
            lineIndex = 0,
            matchScore = 0.5f,
            triggerPercent = 70,
            lastPromptedLine = -1,
            isFinal = false
        )
        assertFalse(fired)
    }

    @Test
    fun `does not re-prompt the same or an earlier line`() {
        assertFalse(
            trigger.shouldPrompt(
                lineIndex = 2, matchScore = 1.0f, triggerPercent = 70,
                lastPromptedLine = 2, isFinal = true
            )
        )
        assertFalse(
            trigger.shouldPrompt(
                lineIndex = 1, matchScore = 1.0f, triggerPercent = 70,
                lastPromptedLine = 2, isFinal = true
            )
        )
    }

    // --- cooldown debounce ---

    @Test
    fun `blocks a second prompt while still inside the cooldown window`() {
        trigger.configureSong(bpm = 120) // ~1000ms default cooldown
        trigger.markPromptTriggered()

        val fired = trigger.shouldPrompt(
            lineIndex = 1, matchScore = 0.9f, triggerPercent = 70,
            lastPromptedLine = 0, isFinal = true
        )
        assertFalse(fired)
    }

    @Test
    fun `allows a prompt once the cooldown has elapsed`() {
        // Very high bpm -> ~20ms cooldown, so a short sleep clears it deterministically.
        trigger.configureSong(bpm = 6000)
        trigger.markPromptTriggered()

        Thread.sleep(80)

        val fired = trigger.shouldPrompt(
            lineIndex = 1, matchScore = 0.9f, triggerPercent = 70,
            lastPromptedLine = 0, isFinal = true
        )
        assertTrue(fired)
    }

    @Test
    fun `resetCooldown clears the block`() {
        trigger.configureSong(bpm = 120)
        trigger.markPromptTriggered()
        trigger.resetCooldown()

        val fired = trigger.shouldPrompt(
            lineIndex = 1, matchScore = 0.9f, triggerPercent = 70,
            lastPromptedLine = 0, isFinal = true
        )
        assertTrue(fired)
    }

    // --- linesSkipped ---

    @Test
    fun `linesSkipped counts the gap past the expected next line`() {
        assertEquals(2, trigger.linesSkipped(currentLineIndex = 5, lastPromptedLine = 2))
        assertEquals(0, trigger.linesSkipped(currentLineIndex = 3, lastPromptedLine = 2))
        assertEquals(0, trigger.linesSkipped(currentLineIndex = 2, lastPromptedLine = 2))
    }
}
