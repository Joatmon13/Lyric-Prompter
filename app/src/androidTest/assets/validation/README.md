# Recognition validation harness

Drop two files in **this folder** to validate the core "follow the singer" bet
with a real recording, end-to-end through Vosk + the tracking pipeline:

- `recording.wav` — you singing a song. **Must be 16 kHz mono 16-bit PCM WAV.**
  Convert any recording with:
  ```
  ffmpeg -i take.m4a -ac 1 -ar 16000 -sample_fmt s16 recording.wav
  ```
- `lyrics.txt` — the plain lyrics of that song (same text you'd paste into the app).

Then run on a connected device/emulator:

```
./gradlew :app:connectedDebugAndroidTest --tests "*RecognitionValidationTest"
adb logcat -s LP.Validate
```

The report shows: how many of N lines the tracker actually reached (the headline
number), the raw transcript Vosk produced from your singing, and a timeline of
when each line was prompted. If `lines reached` is low or the transcript is
garbled, recognition — not the matcher — is the thing to fix.

If these files are absent the test self-skips, so it never breaks CI.
This folder is git-ignored except for this README (recordings shouldn't be committed).
