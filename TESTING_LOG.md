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

**Next Consideration: Variable Timing Per Line**
- Some lines may need shorter/longer gaps than others
- Potential enhancement: `//1`, `//2`, `//3` markers for beat count
- To be evaluated after more singing tests

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

## Current Configuration (as of Jan 1, 2026 16:30)

### Prompt Triggering Logic
```
1. Accumulate recognized words in buffer (max 20)
2. Match ONLY against next expected line (sequential mode)
3. On PARTIAL Vosk result: just accumulate, don't trigger
4. On FINAL Vosk result (silence detected):
   - If matchScore > 0 (any word matched): check cooldown
   - If past cooldown: TRIGGER PROMPT
5. After trigger: start cooldown timer
```

### Cooldown Configuration
| Parameter | Value | Notes |
|-----------|-------|-------|
| BARS_PER_LINE | 0.5f | Half a bar debounce |
| MIN_COOLDOWN_MS | 500ms | Minimum cooldown |
| MAX_COOLDOWN_MS | 2000ms | Maximum cooldown |
| DEFAULT_COOLDOWN_MS | 1000ms | When BPM unknown |

### Cooldown Examples (4/4 time, 0.5 bars)
| BPM | Calculated | Actual (clamped) |
|-----|------------|------------------|
| 60  | 2000ms     | 2000ms (max)     |
| 90  | 1333ms     | 1333ms           |
| 120 | 1000ms     | 1000ms           |
| 150 | 800ms      | 800ms            |
| 180 | 667ms      | 667ms            |

---

## Pending Tasks

- [ ] Add cooldown/timing settings to app settings with per-song override
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

### Test Song
Currently using a song at 64 BPM for testing (slow tempo stress test)
