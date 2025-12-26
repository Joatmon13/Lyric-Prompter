# Lyric Prompter - Functional Specification

## Overview

Lyric Prompter is a native Android app that helps guitarists and singers remember lyrics during performance. The app listens to the user singing, tracks their position in the song, and speaks the first few words of the next line through an earpiece - before they need it.

**Target User:** Paul - a guitarist who plays well but struggles to recall lyrics. Wants a hands-free solution using just phone and Bluetooth earpiece (no foot pedals or extra gear).

**Core Value Proposition:** The app follows YOU, not the other way around. Unlike karaoke or teleprompter apps that force you to match a fixed tempo, Lyric Prompter uses voice recognition to track where you are and prompts at the right moment regardless of your playing speed.

---

## User Journeys

### Journey 1: Adding a Song (Planned Setlist)

**Scenario:** Paul is preparing for a gig next week and wants to add "Wonderwall" to his library.

1. Opens app → Song Library screen
2. Taps "Add Song" button
3. Chooses "Search Online"
4. Types "Wonderwall Oasis"
5. App searches LRCLIB, then Genius if needed
6. Results appear → selects correct match
7. Lyrics are imported and displayed for review
8. Paul edits if needed (fix line breaks, remove [Verse 1] markers, etc.)
9. Sets metadata:
   - BPM: 87
   - Key I sing it in: G
   - Trigger %: 70 (default)
   - Count-in: enabled, 4 beats
10. Saves song → returns to library

**Alternative:** If online search fails, Paul can paste lyrics manually from clipboard or type them in.

### Journey 2: Quick Add (Spontaneous Request)

**Scenario:** Someone at the pub asks "Do you know Hallelujah?"

1. Paul opens app (already running in background)
2. Taps "Quick Add" on main screen
3. Types "Hallelujah Leonard Cohen"
4. Lyrics found → auto-imported with default settings
5. Taps "Perform Now"
6. App is ready in under 30 seconds

### Journey 3: Performing a Song

**Scenario:** Paul is about to play "Bohemian Rhapsody" at an open mic.

1. Opens app → Song Library
2. Selects "Bohemian Rhapsody"
3. Taps "Perform"
4. Screen shows minimal UI:
   - Song title and key (G) at top
   - Large "Ready" indicator
   - Current line display (backup if earpiece fails)
5. Puts in Bluetooth earpiece
6. Taps "Start"
7. **Count-in:** Hears 4 clicks at 72 BPM in earpiece
8. Starts playing guitar intro
9. Begins singing "Is this the real life..."
10. **App recognises ~70% of line 1**
11. **Earpiece speaks:** "Is this just fantasy"
12. Paul continues seamlessly into line 2
13. Pattern continues throughout song
14. Song ends → taps "Stop" or app auto-detects extended silence
15. Returns to library or selects next song

### Journey 4: Playing a Setlist

**Scenario:** Paul has a 45-minute pub gig with 12 songs.

1. Opens app → Setlists
2. Selects "Pub Gig - December"
3. Sees ordered list of songs with keys displayed
4. Taps "Start Setlist"
5. First song loads in performance mode
6. After finishing song, swipes right or taps "Next"
7. Next song loads immediately (vocabulary pre-cached)
8. Continues through setlist
9. Can skip songs, go back, or end early

---

## Features

### F1: Song Library

**Description:** Browse, search, and manage saved songs.

**User Actions:**
- View all songs (list or grid)
- Search by title or artist
- Filter by setlist or tag
- Sort by title, artist, date added, or last performed
- Select song → view details, edit, or perform
- Delete songs (with confirmation)

**Display per Song:**
- Title
- Artist
- Key (perform key, not original)
- BPM (if set)
- Setlist badges

### F2: Add/Import Songs

**Description:** Multiple ways to get lyrics into the app.

**Methods:**
1. **Online Search**
   - Search by "title artist" 
   - Sources: LRCLIB (primary), Genius (fallback)
   - Preview lyrics before importing
   - Auto-extract artist/title metadata

2. **Paste Lyrics**
   - Paste from clipboard
   - Manual entry of title/artist
   - App auto-splits into lines

3. **Import from File**
   - Plain text (.txt)
   - PDF extraction (nice-to-have for Phase 2)

**Auto-Processing on Import:**
- Split text into lines (by line breaks)
- Extract unique words → build vocabulary
- Generate prompt text for each line (first N words of next line)
- Strip common markers like [Verse], [Chorus], [Bridge]

### F3: Song Editor

**Description:** Edit lyrics and configure per-song settings.

**Lyrics Editing:**
- Edit full lyrics text
- Re-split into lines after editing
- Preview how lines will be prompted

**Settings:**
| Setting | Type | Default | Range/Options |
|---------|------|---------|---------------|
| BPM | Number | Empty | 40-220 |
| Original Key | Dropdown | Empty | C, C#, D... B, plus minor keys |
| Perform Key | Dropdown | Empty | Same as above |
| Time Signature | Dropdown | 4/4 | 4/4, 3/4, 6/8, etc. |
| Count-in Enabled | Toggle | On | On/Off |
| Count-in Beats | Number | 4 | 1-8 |
| Trigger % | Slider | 70 | 40-90 |
| Prompt Words | Number | 4 | 2-6 |

**Actions:**
- Save changes
- Test prompts (simulate prompting without singing)
- Delete song

### F4: Performance Mode

**Description:** Minimal UI optimised for playing live.

**Screen Elements:**
- Song title and perform key (top, large)
- Current status: "Ready" / "Listening" / "Line 3 of 24"
- Current line text (large, centred) - backup display
- Next line text (smaller, below) - optional
- Stop button

**Behaviour:**
1. On "Start":
   - Play count-in clicks if enabled
   - Begin voice recognition
   - Screen dims slightly (save battery, reduce distraction)

2. During Performance:
   - Vosk streams recognised words
   - Position tracker matches words to current line
   - When trigger threshold reached → TTS speaks next line's prompt
   - Display updates to show current line

3. On "Stop" or song end:
   - Stop recognition
   - Show summary (optional): lines completed, duration
   - Option to perform again or return to library

**Audio Routing:**
- Count-in clicks → Bluetooth earpiece (or default audio)
- TTS prompts → Bluetooth earpiece (or default audio)
- Recognition → device microphone (not earpiece mic)

### F5: Setlist Management

**Description:** Organise songs into ordered setlists for gigs.

**Features:**
- Create/rename/delete setlists
- Add songs to setlist (from library picker)
- Reorder songs (drag and drop)
- Remove songs from setlist
- Duplicate setlist

**Setlist Performance:**
- Play through setlist in order
- Next/Previous song navigation
- Skip songs
- See upcoming songs
- Pre-cache next song's vocabulary for instant switching

### F6: Settings (App-Wide)

**Description:** Global defaults and preferences.

| Setting | Description | Default |
|---------|-------------|---------|
| Default Trigger % | Applied to new songs | 70% |
| Default Prompt Words | Applied to new songs | 4 |
| Default Count-in | Applied to new songs | Enabled, 4 beats |
| TTS Voice | Android TTS voice selection | System default |
| TTS Speed | Prompt speaking rate | 1.0x |
| Audio Output | Force earpiece or auto-detect | Auto |
| Theme | Light/Dark/System | System |
| Keep Screen On | During performance | On |

### F7: Quick Actions

**Description:** Fast access from home screen or library.

- **Quick Add:** Search and add song with minimal taps
- **Recent Songs:** Last 5 performed songs, one tap to perform
- **Quick Setlist:** Start default/last setlist immediately

---

## Screen Inventory

### S1: Home / Library Screen
- Song list with search/filter
- Quick Add button (prominent)
- Recent songs section
- Navigation to Setlists and Settings

### S2: Song Detail Screen
- Song metadata display
- Lyrics preview (scrollable)
- Perform button (prominent)
- Edit button
- Delete option

### S3: Song Editor Screen
- Lyrics text editor
- Metadata fields
- Performance settings (trigger %, prompts, count-in)
- Save/Cancel actions

### S4: Add Song Screen
- Search input
- Search results list
- Paste/Manual entry tab
- Import button

### S5: Performance Screen
- Minimal, distraction-free
- Large key display
- Current line backup
- Stop button only

### S6: Setlist List Screen
- All setlists
- Create new button
- Tap to view/edit

### S7: Setlist Detail Screen
- Ordered song list
- Drag to reorder
- Add/remove songs
- Start setlist button

### S8: Settings Screen
- Grouped settings
- About/version info

---

## Non-Functional Requirements

### Performance
- Vocabulary loading: < 2 seconds per song
- Recognition latency: < 500ms from speech to match
- TTS latency: < 200ms from trigger to audio start
- App launch to ready: < 3 seconds

### Reliability
- Recognition should work in moderate ambient noise (pub environment)
- Graceful handling of recognition errors (don't get stuck)
- Auto-recovery if position tracking loses confidence

### Usability
- One-handed operation where possible
- Large touch targets for gigging (may be dark, may be rushed)
- Minimal taps from app launch to performing

### Privacy
- All recognition happens on-device (Vosk)
- Lyrics stored locally
- Online search only when user initiates
- No analytics or tracking

### Platform
- Android only (Paul's device: Pixel 9)
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 34 (Android 14)

---

## Out of Scope (Phase 1)

- iOS version
- Cloud sync / backup
- Chord charts / tabs
- Multiple user profiles
- Sharing songs between devices
- PDF import
- Tempo/beat detection from audio
- Harmony parts / multiple vocal lines
- Integration with music streaming services
- Offline lyrics caching from online sources

---

## Success Criteria

1. **Core Loop Works:** Paul can add a song, perform it with voice tracking, and receive timely prompts through his earpiece.

2. **Spontaneous Requests:** From "do you know X?" to performing in under 60 seconds.

3. **No Gear:** Phone + earpiece only. No pedals, no tablet, no music stand.

4. **Follows the Player:** Prompts arrive at the right time regardless of tempo variations.

5. **Pub-Ready:** Works reliably in typical acoustic gig environment.
