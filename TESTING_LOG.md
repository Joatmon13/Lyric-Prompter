# Lyric Prompter Testing Log

This document tracks all changes made during testing sessions to avoid reiterating the same changes and ensure we only make one change at a time.

---

## Session: January 1, 2026

### Issue 1: Bluetooth TTS Audio Routing
**Problem:** TTS was silent over Bluetooth A2DP
**Solution:** Fixed audio routing to properly route TTS through Bluetooth
**Status:** RESOLVED

---

### Issue 2: UI Line Sync
**Problem:** UI line display jumped out of sync with prompts
**Solution:** Fixed tracking/visual sync between position tracker and UI state
**Status:** RESOLVED

---

### Issue 3: Prompt Marker Support
**Problem:** Needed way to control which lines trigger TTS prompts
**Solution:** Added `//` marker support in lyrics - only lines ending with `//` trigger prompts
**Files Modified:** `PositionTracker.kt`, lyrics processing
**Status:** RESOLVED

---

### Issue 4: Prompts Firing Too Early (While Still Singing)
**Problem:** Prompts were firing at the START of a line when any word matched, interrupting the performer
**Root Cause:** The "any word match" logic triggered immediately upon first word recognition

**Iteration 4a: Add Cooldown Timer**
- Added BPM-based cooldown calculation
- Initial: 2 bars worth of beats as cooldown
- Files: `PromptTrigger.kt`
- Result: Cooldown too long at slow tempos (7.5 sec at 64 BPM)

**Iteration 4b: Reduce Cooldown to 1 Bar**
- Changed from 2 bars to 1 bar
- Max cooldown: 8 seconds → 5 seconds
- Result: Still firing at start of line, just with delay

**Iteration 4c: Silence Detection (Current Approach)**
- Key insight from user: "can it identify the line and then wait for any sound to stop"
- Vosk provides `partial` results while speaking, `final` results when silence detected
- Changed logic: Only trigger prompts on FINAL results (isFinal=true)
- Files Modified:
  - `PromptTrigger.kt`: Added `isFinal` parameter to `shouldPrompt()`
  - `PositionTracker.kt`: Pass `isFinal` flag through
  - `PerformViewModel.kt`: Pass `!isPartial` as `isFinal` to tracker
- Result: Line 4 blocked by cooldown (3592ms < 3750ms)

**Iteration 4d: Reduce Cooldown (Silence Detection Handles Timing)**
- With silence detection, cooldown is just a debounce for brief pauses
- Changes:
  - `BARS_PER_LINE`: 1 → 0.5f
  - `MIN_COOLDOWN_MS`: 1500ms → 500ms
  - `MAX_COOLDOWN_MS`: 5000ms → 2000ms
  - `DEFAULT_COOLDOWN_MS`: 3000ms → 1000ms
- At 64 BPM: cooldown now ~1875ms (was 3750ms)
- Result: Works well in spoken testing
- Status: **RESOLVED**

**Iteration 4e: Re-enable triggerPercent Threshold**
- Problem: During singing test, prompts fired on 12-20% matches (racing ahead)
- Root cause: triggerPercent was being IGNORED - only checking matchScore > 0
- Fix: Re-enabled triggerPercent check from song settings
- Now requires BOTH: silence detected AND matchScore >= triggerPercent
- User can adjust threshold in song settings (default 70%)
- Status: **TESTING**

**Iteration 4f: Implement //N Beat Notation**
- User identified that bars/ms settings were redundant - should auto-calculate from BPM
- Implemented `//N` notation in lyrics for per-line beat counts
- Examples: `//` (default 2 beats), `//4` (4 beats), `//8` (8 beats for instrumental breaks)
- Changes:
  - `LyricLine.kt`: Added `cooldownBeats: Int?` field
  - `ProcessLyricsUseCase.kt`: Added regex to parse `//N` markers, extract beats 1-16
  - `PromptTrigger.kt`: Refactored to use per-line cooldowns with `setCooldownForLine(beats)`
  - `PositionTracker.kt`: Calls `configureSong(bpm)` and `setCooldownForLine()` for each line
  - `SettingsScreen.kt`: Simplified to single "Default Cooldown" slider (1-8 beats)
  - Removed redundant min/max cooldown ms sliders
- Cooldown formula: `cooldown_ms = beats × (60,000 / BPM)`
- Status: **IMPLEMENTED** - Ready for testing

---

### Issue 5: Count-In Improvement
**Problem:** Count-in just said bar numbers, not individual beats
**Solution:**
- Speak "one, two, three, four" for each beat
- Show visual countdown of bars remaining (3 → 2 → 1)
- Added `barsRemaining` field to `PerformanceStatus.CountIn`
- Added `countDown` text style (96sp) to `Type.kt`
**Files Modified:**
- `CountInPlayer.kt`
- `PerformViewModel.kt`
- `PerformScreen.kt`
- `PerformanceState.kt`
- `Type.kt`
**Status:** RESOLVED

---

## Session: June 21-22, 2026

### Issue 6: Vosk Library Outdated
**Problem:** Project pinned `vosk-android` 0.3.47; latest is 0.3.75 (Dec 2025).
**Investigation:** Also evaluated whether a better *model* exists for singing. Findings:
- Bundled model `vosk-model-small-en-us-0.15` (40MB) is still the current recommended small en-us model — not outdated.
- No Vosk model is trained for singing; the speech-vs-singing gap is inherent.
- App relies on dynamic per-song grammar (`Recognizer(model, rate, grammar)` + `setGrammar()` in `VoskEngine.kt`), which only works with dynamic-graph models: the small model or `vosk-model-en-us-0.22-lgraph` (128MB, ~20% lower WER). The large static models (0.22 1.8GB, gigaspeech 2.3GB) are NOT compatible with `setGrammar()`.
**Solution:** Bumped `vosk` to 0.3.75 in `gradle/libs.versions.toml`.
**Files Modified:** `gradle/libs.versions.toml`
**Status:** RESOLVED (committed to main, d1ef60d)

---

### Issue 7: Tracker Stuck on Line, Never Advances When Singing
**Problem:** During a sung test (42-line song, 64 BPM), `[SESSION_END] linesPrompted=0` — the tracker never advanced past line 0.
**Diagnosis (from logcat):**
- Line 0 ("They sat together in the park", 6 words) capped at `score=33%` — Vosk only caught "sat" + "together"; weak/unstressed words ("they", "in", "the") were swallowed when sung.
- Meanwhile Vosk clearly heard distinctive sustained words from *later* lines ("neon sky", "emptiness", "remember", "spark").
- Root cause: `onWordsRecognized()` matched ONLY against `lastPromptedLine + 1` (single line). The `SEARCH_WINDOW_AFTER = 2` constant was defined but never referenced — look-ahead was scaffolded but never wired up.

**Fix: Look-Ahead Matching**
- Score the next expected line PLUS a small window (`SEARCH_WINDOW_AFTER = 2`) of following lines; advance to the best match.
- Biased toward staying sequential: a later line must beat the expected line's score by `SKIP_MARGIN = 0.20f` before we skip to it. Window capped so we don't jump to a repeated chorus.
- New log fields: `[MATCH_RESULT] ... skipped=N` and `[CATCH_UP] skipping lines X..Y | matched=Z`.
- Files Modified: `PositionTracker.kt` (added `SKIP_MARGIN`; replaced single-line match in `onWordsRecognized()` with windowed best-of-search)
- Recommended companion tuning: lower per-song `triggerPercent` to ~30-35% for sung material (empirically capped at 33%).
- Status: **IMPLEMENTED** — built + installed to tablet (Samsung SM-T590), pending re-test.

**Related observation (not yet actioned):** `reset()` comment claims "Vosk can't update grammar during recognition," but `VoskEngine.updateGrammar()`/`setGrammar()` work, and `PositionTracker`'s `updateFocusedGrammar()`/`onGrammarUpdateNeeded` scaffolding is entirely unused. Re-focusing grammar to the current window could improve recognition accuracy — larger change, deferred.

---

## Current Configuration (as of June 22, 2026)

### Prompt Triggering Logic
```
1. Accumulate recognized words in buffer (max 20)
2. Match against next expected line + window of next 2 lines (look-ahead mode);
   pick best-scoring line, biased sequential via SKIP_MARGIN (0.20)
3. On PARTIAL Vosk result: just accumulate, don't trigger
4. On FINAL Vosk result (silence detected):
   - Check matchScore >= triggerPercent (from song settings)
   - Check cooldown has elapsed
   - If both: TRIGGER PROMPT
5. After trigger: set per-line cooldown based on //N notation
```

### Cooldown Configuration (Beat-Based)
| Parameter | Value | Notes |
|-----------|-------|-------|
| DEFAULT_COOLDOWN_BEATS | 2 | Default beats when //N not specified |
| DEFAULT_COOLDOWN_MS | 1000ms | Fallback when BPM unknown |

### Per-Line Beat Notation
| Notation | Beats | Use Case |
|----------|-------|----------|
| `//` | 2 (default) | Normal line endings |
| `//1` | 1 | Quick transitions |
| `//4` | 4 | Longer pauses |
| `//8` | 8 | Instrumental breaks |
| `//16` | 16 | Long solos |

### Cooldown Examples (beats × 60/BPM × 1000)
| BPM | 2 beats | 4 beats | 8 beats |
|-----|---------|---------|---------|
| 60  | 2000ms  | 4000ms  | 8000ms  |
| 90  | 1333ms  | 2667ms  | 5333ms  |
| 120 | 1000ms  | 2000ms  | 4000ms  |
| 150 | 800ms   | 1600ms  | 3200ms  |

---

## Pending Tasks

- [x] Add cooldown/timing settings to app settings with per-song override (done via //N notation)
- [ ] Connect settings UI to persistence (currently uses hardcoded defaults)
- [ ] Add timing/pause between lines for instrumental breaks
- [ ] Separate song storage from app (retain on uninstall option)
- [ ] Add song backup/export feature

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `PromptTrigger.kt` | Cooldown logic, trigger decision |
| `PositionTracker.kt` | Word matching, sequential line tracking |
| `FuzzyMatcher.kt` | LCS-based word matching algorithm |
| `PerformViewModel.kt` | Session management, Vosk callbacks |
| `VoskEngine.kt` | Speech recognition, partial/final results |
| `CountInPlayer.kt` | Beat counting, intro speech |
| `PromptSpeaker.kt` | TTS output |

---

## Testing Notes

### How to Read Logs
```bash
adb logcat -d | grep -E "LP\." | tail -100
```

### Key Log Tags
- `[WAITING_FOR_SILENCE]` - Matched words but still speaking
- `[SILENCE_DETECTED]` - Pause detected, evaluating trigger
- `[COOLDOWN]` - In cooldown period, prompt blocked
- `[TRIGGER_PROMPT]` - Prompt fired
- `[VOSK_PARTIAL]` - Still speaking
- `[VOSK_FINAL]` - Silence detected by Vosk
- `[MATCH_RESULT] ... skipped=N` - Best-match line chosen (N = lines skipped ahead)
- `[CATCH_UP] skipping lines X..Y | matched=Z` - Look-ahead jumped past missed line(s)

### Test Song
Currently using a song at 64 BPM for testing (slow tempo stress test)
