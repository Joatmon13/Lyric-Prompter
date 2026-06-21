package com.lyricprompter.validation

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyricprompter.audio.vosk.VoskEngine
import com.lyricprompter.audio.vosk.VoskState
import com.lyricprompter.domain.model.Song
import com.lyricprompter.domain.usecase.ProcessLyricsUseCase
import com.lyricprompter.tracking.FuzzyMatcher
import com.lyricprompter.tracking.PositionTracker
import com.lyricprompter.tracking.PromptEvent
import com.lyricprompter.tracking.PromptTrigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Recording-playback harness for validating the core bet: can Vosk + the
 * tracking pipeline actually follow *sung* lyrics?
 *
 * This is a developer tool, not a pass/fail assertion. It replays a recording
 * of you singing through the EXACT live recognition + tracking path and logs a
 * report (raw transcript, which lines got prompted, and when).
 *
 * To use:
 *   1. Record yourself singing a song (phone voice recorder is fine).
 *   2. Convert to 16 kHz mono 16-bit WAV, e.g.:
 *        ffmpeg -i take.m4a -ac 1 -ar 16000 -sample_fmt s16 recording.wav
 *   3. Drop it at:  app/src/androidTest/assets/validation/recording.wav
 *      and the lyrics at: app/src/androidTest/assets/validation/lyrics.txt
 *   4. Run on a device/emulator:
 *        ./gradlew :app:connectedDebugAndroidTest --tests "*RecognitionValidationTest"
 *   5. Read the report:  adb logcat -s LP.Validate
 *
 * If the assets are absent the test is skipped (assumeTrue), so it never breaks CI.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionValidationTest {

    private companion object {
        const val TAG = "LP.Validate"
        const val WAV_ASSET = "validation/recording.wav"
        const val LYRICS_ASSET = "validation/lyrics.txt"
    }

    @Test
    fun replayRecordingThroughPipeline() = runBlocking {
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext

        val wavBytes = testCtx.readAssetOrNull(WAV_ASSET)
        val lyrics = testCtx.readAssetOrNull(LYRICS_ASSET)?.decodeToString()
        assumeTrue(
            "Drop $WAV_ASSET and $LYRICS_ASSET into app/src/androidTest/assets to run this harness.",
            wavBytes != null && !lyrics.isNullOrBlank()
        )

        val wav = WavData.parse(wavBytes!!)
        val song = ProcessLyricsUseCase().process(lyrics!!, "Validation Song", "")

        // Bring up the real Vosk model.
        val vosk = VoskEngine(appCtx)
        vosk.initialize()
        val ready = withTimeoutOrNull(60_000) {
            while (vosk.state.value !is VoskState.Ready) {
                check(vosk.state.value !is VoskState.Error) {
                    "Vosk failed to load: ${vosk.state.value}"
                }
                delay(100)
            }
            true
        }
        check(ready == true) { "Vosk model did not become ready within 60s" }

        // Same tracking stack as live.
        val tracker = PositionTracker(FuzzyMatcher(), PromptTrigger())
        tracker.loadSong(song)

        val transcript = StringBuilder()
        val prompts = mutableListOf<PromptLog>()

        vosk.recognizePcmStream(ByteArrayInputStream(wav.pcm), song.vocabulary) { text, isFinal, elapsedMs ->
            if (isFinal) transcript.append(text).append(' ')

            // Mirror PerformViewModel.handleRecognition word handling.
            val words = text.lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() && it != "[unk]" }
            if (words.isEmpty()) return@recognizePcmStream

            when (val event = tracker.onWordsRecognized(words, isFinal = isFinal)) {
                is PromptEvent.SpeakPrompt ->
                    prompts += PromptLog(event.lineIndex, elapsedMs, event.promptText)
                is PromptEvent.LineCompleted ->
                    prompts += PromptLog(event.lineIndex, elapsedMs, "(line completed, no prompt)")
                is PromptEvent.SongFinished ->
                    prompts += PromptLog(tracker.getState().lastPromptedLine, elapsedMs, "(song finished)")
                null -> Unit
            }
        }

        Log.i(TAG, report(song, wav, transcript.toString().trim(), prompts))
    }

    private fun report(song: Song, wav: WavData, transcript: String, prompts: List<PromptLog>): String {
        val reached = prompts.maxOfOrNull { it.lineIndex + 1 } ?: 0
        val coverage = if (song.lineCount == 0) 0 else (reached * 100) / song.lineCount
        val formatMatches = wav.sampleRate == 16000 && wav.channels == 1 && wav.bitsPerSample == 16

        return buildString {
            appendLine()
            appendLine("===== RECOGNITION VALIDATION REPORT =====")
            appendLine("audio        : ${"%.1f".format(wav.durationMs / 1000.0)}s | " +
                "${wav.sampleRate}Hz ${wav.channels}ch ${wav.bitsPerSample}bit" +
                if (formatMatches) "" else "  <-- WARNING: expected 16000Hz 1ch 16bit, results will be poor")
            appendLine("song lines   : ${song.lineCount}")
            appendLine("lines reached: $reached / ${song.lineCount}  (${coverage}% tracked)")
            appendLine("prompts fired: ${prompts.count { !it.promptText.startsWith("(") }}")
            appendLine()
            appendLine("--- What Vosk heard (final transcript) ---")
            appendLine(transcript.ifBlank { "(nothing recognized)" })
            appendLine()
            appendLine("--- Per-line timeline ---")
            if (prompts.isEmpty()) {
                appendLine("(no lines were tracked - recognition never matched a line)")
            } else {
                prompts.forEach { p ->
                    val lineText = song.lines.getOrNull(p.lineIndex)?.text?.take(40) ?: "?"
                    appendLine("  @${"%6.1f".format(p.elapsedMs / 1000.0)}s  L${p.lineIndex} \"$lineText\"  ->  ${p.promptText}")
                }
            }
            appendLine("=========================================")
        }
    }

    private data class PromptLog(val lineIndex: Int, val elapsedMs: Long, val promptText: String)

    private fun Context.readAssetOrNull(path: String): ByteArray? = try {
        assets.open(path).use { it.readBytes() }
    } catch (e: IOException) {
        null
    }
}

/**
 * Minimal WAV (PCM) reader: locates the fmt and data chunks. Supports the
 * canonical 16-bit PCM layout produced by `ffmpeg -ac 1 -ar 16000 -sample_fmt s16`.
 */
private class WavData(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val pcm: ByteArray
) {
    val durationMs: Long
        get() {
            val bytesPerSec = sampleRate * channels * (bitsPerSample / 8)
            return if (bytesPerSec == 0) 0 else pcm.size * 1000L / bytesPerSec
        }

    companion object {
        fun parse(bytes: ByteArray): WavData {
            require(bytes.size > 44) { "File too small to be a WAV" }
            require(tag(bytes, 0) == "RIFF" && tag(bytes, 8) == "WAVE") { "Not a RIFF/WAVE file" }

            var sampleRate = 0
            var channels = 0
            var bits = 0
            var pcm = ByteArray(0)

            var offset = 12
            while (offset + 8 <= bytes.size) {
                val id = tag(bytes, offset)
                val size = leInt(bytes, offset + 4)
                val body = offset + 8
                when (id) {
                    "fmt " -> {
                        channels = leShort(bytes, body + 2)
                        sampleRate = leInt(bytes, body + 4)
                        bits = leShort(bytes, body + 14)
                    }
                    "data" -> {
                        val end = minOf(body + size, bytes.size)
                        pcm = bytes.copyOfRange(body, end)
                    }
                }
                // Chunks are word-aligned (pad byte if odd size).
                offset = body + size + (size and 1)
            }
            require(pcm.isNotEmpty()) { "No data chunk found in WAV" }
            return WavData(sampleRate, channels, bits, pcm)
        }

        private fun tag(b: ByteArray, off: Int) = String(b, off, 4, Charsets.US_ASCII)
        private fun leShort(b: ByteArray, off: Int) =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
        private fun leInt(b: ByteArray, off: Int) =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }
}
