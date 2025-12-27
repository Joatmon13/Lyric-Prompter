# LyricPrompter Recognition Tuning Analysis

## Recognition & Matching Settings

| Setting | Current Value | Range | Impact |
|---------|---------------|-------|--------|
| Trigger Percent | 25% (user set) | 20-90% | Lower = prompts earlier with fewer matched words. Too low risks false triggers, too high misses lines |
| Edit Distance Ratio | 0.5 | 0-1.0 | Higher = more lenient fuzzy matching. At 0.5, a 6-letter word allows 3 character edits |
| Min Fuzzy Word Length | 3 chars | - | Words < 3 chars must match exactly (prevents "a" matching "the") |
| Word Buffer Size | 20 words | - | Larger = more context for matching, but older words may not be relevant |
| Keep After Prompt | 5 words | - | Words retained after triggering. Too few = lose context, too many = stale matches |
| Search Window Before | 0 lines | - | Never look backwards (prevents re-prompting) |
| Search Window After | 2 lines | - | Look ahead 2 lines to catch up if one missed |

---

## Audio Input Settings

| Setting | Current Value | Impact |
|---------|---------------|--------|
| Audio Source | Bluetooth SCO mic | 8kHz mono (phone quality) - degrades Vosk accuracy |
| Alternative | Phone mic | 16kHz - better quality but no privacy |
| Spatial Audio | OFF (recommended) | Prevents processing interference |

---

## Vosk Configuration

| Setting | Current Value | Impact |
|---------|---------------|--------|
| Vocabulary | Full song (~50-200 words) | Constrains recognition to lyric words only |
| Model | vosk-model-small-en-us | Trained on speech, not singing |
| Sample Rate | 16kHz expected | Bluetooth provides 8kHz = quality loss |

---

## Observed Results

**Song:** Lyin' Eyes @ 25% trigger threshold

| Sung Lyrics | Vosk Heard | Match Score | Outcome |
|-------------|------------|-------------|---------|
| "Late at night a big old house gets lonely" | "late", "big", "they gone" | 33% | Prompted |
| "I guess every form of refuge..." | (skipped) | - | Auto-prompted via skip detection |
| "And it breaks her heart..." | "guess", "lonely" | 18% | Stuck |

---

## Key Bottlenecks

| Issue | Cause | Potential Fix |
|-------|-------|---------------|
| Low match scores (11-33%) | Vosk mishears sung words | More aggressive fuzzy/phonetic matching |
| Fragments only | Singing != speech patterns | Lower threshold OR time-based fallback |
| Buffer clears too fast | 5 words kept after prompt | Increase to 8-10 words |
| Stuck on lines | Can't reach threshold | Auto-advance after N seconds? |

---

## Possible Improvements to Test

| Change | Risk | Benefit |
|--------|------|---------|
| Lower trigger to 20% | False triggers on wrong lines | More lines prompted |
| Increase buffer retention to 10 words | Stale matches | Better context accumulation |
| Time-based fallback (5s no prompt -> advance) | Skips during pauses | Never gets stuck |
| Even more lenient phonetic matching | Wrong word matches | Catches more singing variations |

---

## Test Session Log

| Date | Song | Trigger % | Lines Prompted | Lines Skipped | Notes |
|------|------|-----------|----------------|---------------|-------|
| 2024-12-27 | Lyin' Eyes | 35% | 2 | many | Got stuck after line 1 |
| 2024-12-27 | Lyin' Eyes | 25% | 5 | some | Better, skip detection helped |

---

## Next Steps

1. Test with 20% trigger threshold
2. Consider increasing buffer retention from 5 to 10 words
3. Evaluate time-based fallback mechanism
4. Explore alternative speech recognition models trained on singing
