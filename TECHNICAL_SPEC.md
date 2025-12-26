# Lyric Prompter - Technical Specification

## Technology Stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Platform | Native Android (Kotlin) | Low latency audio, direct Vosk integration, Paul's device is Pixel 9 |
| Min SDK | API 26 (Android 8.0) | Vosk compatibility, modern audio APIs |
| Target SDK | API 34 (Android 14) | Current best practices |
| UI Framework | Jetpack Compose | Modern, declarative UI |
| Architecture | MVVM + Clean Architecture | Testable, maintainable |
| DI | Hilt | Standard Android DI |
| Speech Recognition | Vosk | Offline, streaming, custom vocabulary support |
| Text-to-Speech | Android TTS | Built-in, low latency |
| Local Storage | Room + JSON files | Structured data + lyrics/vocabulary |
| Async | Kotlin Coroutines + Flow | Standard async patterns |
| Lyrics API | LRCLIB + Genius | Free, comprehensive coverage |
| HTTP Client | Ktor or Retrofit | API calls for lyrics search |

---

## Project Structure

```
app/
├── src/main/
│   ├── java/com/boynetech/lyricprompter/
│   │   ├── LyricPrompterApp.kt              # Application class
│   │   ├── MainActivity.kt                   # Single activity
│   │   │
│   │   ├── di/                               # Dependency injection
│   │   │   ├── AppModule.kt
│   │   │   ├── AudioModule.kt
│   │   │   └── DatabaseModule.kt
│   │   │
│   │   ├── data/                             # Data layer
│   │   │   ├── local/
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── SongDao.kt
│   │   │   │   │   ├── SetlistDao.kt
│   │   │   │   │   └── entities/
│   │   │   │   │       ├── SongEntity.kt
│   │   │   │   │       └── SetlistEntity.kt
│   │   │   │   └── preferences/
│   │   │   │       └── AppPreferences.kt
│   │   │   ├── remote/
│   │   │   │   ├── lyrics/
│   │   │   │   │   ├── LyricsSearchService.kt
│   │   │   │   │   ├── LrcLibApi.kt
│   │   │   │   │   └── GeniusApi.kt
│   │   │   └── repository/
│   │   │       ├── SongRepository.kt
│   │   │       └── SetlistRepository.kt
│   │   │
│   │   ├── domain/                           # Domain layer
│   │   │   ├── model/
│   │   │   │   ├── Song.kt
│   │   │   │   ├── LyricLine.kt
│   │   │   │   ├── Setlist.kt
│   │   │   │   └── PerformanceState.kt
│   │   │   └── usecase/
│   │   │       ├── ImportLyricsUseCase.kt
│   │   │       ├── ProcessLyricsUseCase.kt
│   │   │       └── SearchLyricsUseCase.kt
│   │   │
│   │   ├── audio/                            # Audio engine
│   │   │   ├── vosk/
│   │   │   │   ├── VoskEngine.kt             # Vosk initialisation & streaming
│   │   │   │   ├── VoskVocabulary.kt         # Grammar/vocabulary builder
│   │   │   │   └── RecognitionResult.kt
│   │   │   ├── tts/
│   │   │   │   ├── PromptSpeaker.kt          # TTS wrapper
│   │   │   │   └── CountInPlayer.kt          # Metronome clicks
│   │   │   └── routing/
│   │   │       └── AudioRouter.kt            # Bluetooth/speaker routing
│   │   │
│   │   ├── tracking/                         # Position tracking
│   │   │   ├── PositionTracker.kt            # Core tracking logic
│   │   │   ├── FuzzyMatcher.kt               # Word sequence matching
│   │   │   └── PromptTrigger.kt              # Decides when to fire prompts
│   │   │
│   │   ├── ui/                               # Presentation layer
│   │   │   ├── navigation/
│   │   │   │   └── NavGraph.kt
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt
│   │   │   │   ├── Color.kt
│   │   │   │   └── Type.kt
│   │   │   ├── library/
│   │   │   │   ├── LibraryScreen.kt
│   │   │   │   └── LibraryViewModel.kt
│   │   │   ├── song/
│   │   │   │   ├── SongDetailScreen.kt
│   │   │   │   ├── SongEditorScreen.kt
│   │   │   │   └── SongViewModel.kt
│   │   │   ├── add/
│   │   │   │   ├── AddSongScreen.kt
│   │   │   │   ├── SearchResultsScreen.kt
│   │   │   │   └── AddSongViewModel.kt
│   │   │   ├── perform/
│   │   │   │   ├── PerformScreen.kt
│   │   │   │   └── PerformViewModel.kt
│   │   │   ├── setlist/
│   │   │   │   ├── SetlistScreen.kt
│   │   │   │   └── SetlistViewModel.kt
│   │   │   └── settings/
│   │   │       ├── SettingsScreen.kt
│   │   │       └── SettingsViewModel.kt
│   │   │
│   │   └── util/
│   │       ├── TextProcessor.kt              # Lyrics cleaning/splitting
│   │       └── Extensions.kt
│   │
│   ├── assets/
│   │   └── vosk-model-small-en-us/          # ~50MB English model
│   │
│   └── res/
│       └── ...
│
├── build.gradle.kts
└── proguard-rules.pro
```

---

## Data Models

### Song (Domain Model)

```kotlin
data class Song(
    val id: String,                          // UUID
    val title: String,
    val artist: String,
    
    // Musical metadata
    val bpm: Int?,
    val originalKey: String?,
    val performKey: String?,
    val timeSignature: String?,
    
    // Count-in settings
    val countInEnabled: Boolean = true,
    val countInBeats: Int = 4,
    
    // Prompt settings
    val triggerPercent: Int = 70,            // 40-90
    val promptWordCount: Int = 4,            // 2-6
    
    // Lyrics
    val lines: List<LyricLine>,
    val vocabulary: Set<String>,
    
    // Organisation
    val setlistIds: List<String>,
    val tags: List<String>,
    
    // Timestamps
    val createdAt: Long,
    val updatedAt: Long
)

data class LyricLine(
    val index: Int,
    val text: String,                        // Original text: "Is this the real life"
    val words: List<String>,                 // Normalised: ["is", "this", "the", "real", "life"]
    val promptText: String                   // For TTS: "Is this the real"
)
```

### SongEntity (Room Database)

```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val bpm: Int?,
    val originalKey: String?,
    val performKey: String?,
    val timeSignature: String?,
    val countInEnabled: Boolean,
    val countInBeats: Int,
    val triggerPercent: Int,
    val promptWordCount: Int,
    val lyricsJson: String,                  // JSON array of LyricLine
    val vocabularyJson: String,              // JSON array of words
    val setlistIdsJson: String,              // JSON array of setlist IDs
    val tagsJson: String,                    // JSON array of tags
    val createdAt: Long,
    val updatedAt: Long
)
```

### Setlist

```kotlin
data class Setlist(
    val id: String,
    val name: String,
    val songIds: List<String>,               // Ordered list
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val songIdsJson: String,                 // JSON array preserving order
    val createdAt: Long,
    val updatedAt: Long
)
```

### PerformanceState

```kotlin
data class PerformanceState(
    val status: PerformanceStatus,
    val currentLineIndex: Int,
    val recognisedWords: List<String>,       // Rolling buffer of recent words
    val lineConfidence: Float,               // 0.0 - 1.0 for current line
    val lastPromptedLine: Int,               // Prevent double-prompting
    val startTime: Long?,
    val song: Song
)

enum class PerformanceStatus {
    READY,
    COUNT_IN,
    LISTENING,
    PAUSED,
    FINISHED
}
```

---

## Core Components

### 1. VoskEngine

Handles speech recognition setup and streaming.

```kotlin
class VoskEngine @Inject constructor(
    private val context: Context
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    
    // Load model from assets (do once at app start)
    suspend fun initialise(): Result<Unit>
    
    // Configure recognizer with song-specific vocabulary
    fun loadVocabulary(vocabulary: Set<String>): Result<Unit>
    
    // Start streaming recognition
    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (Exception) -> Unit
    )
    
    // Stop recognition
    fun stopListening()
    
    // Cleanup
    fun release()
}
```

**Vocabulary Loading:**

Vosk supports grammar-based recognition. We build a JSON grammar from the song's vocabulary:

```kotlin
fun buildGrammar(words: Set<String>): String {
    // Vosk grammar format
    val wordList = words.joinToString("\", \"") { it.lowercase() }
    return "[\"$wordList\", \"[unk]\"]"
}
```

The `[unk]` token handles words not in vocabulary (coughs, mumbles, etc.)

### 2. PositionTracker

Matches recognised words to lyrics position and triggers prompts.

```kotlin
class PositionTracker @Inject constructor(
    private val fuzzyMatcher: FuzzyMatcher,
    private val promptTrigger: PromptTrigger
) {
    private var song: Song? = null
    private var currentLineIndex = 0
    private var recognisedBuffer = mutableListOf<String>()
    private var lastPromptedLine = -1
    
    // Initialise with song
    fun loadSong(song: Song)
    
    // Process newly recognised words
    fun onWordsRecognised(words: List<String>): PromptEvent?
    
    // Reset to beginning
    fun reset()
}

sealed class PromptEvent {
    data class SpeakPrompt(val lineIndex: Int, val promptText: String) : PromptEvent()
    data class LineCompleted(val lineIndex: Int) : PromptEvent()
    object SongFinished : PromptEvent()
}
```

**Matching Algorithm:**

```kotlin
class FuzzyMatcher {
    
    // Score how well recent words match a line
    fun matchScore(
        recognisedWords: List<String>,
        lineWords: List<String>
    ): Float {
        // Use Longest Common Subsequence approach
        // Returns 0.0 - 1.0 representing % of line words matched
        
        val lcs = longestCommonSubsequence(recognisedWords, lineWords)
        return lcs.size.toFloat() / lineWords.size
    }
    
    // Find best matching line given recent words
    fun findBestLine(
        recognisedWords: List<String>,
        lines: List<LyricLine>,
        searchWindow: IntRange      // Only search nearby lines
    ): Pair<Int, Float>? {
        return lines
            .slice(searchWindow)
            .mapIndexed { i, line -> 
                (searchWindow.first + i) to matchScore(recognisedWords, line.words)
            }
            .maxByOrNull { it.second }
    }
}
```

**Trigger Logic:**

```kotlin
class PromptTrigger {
    
    fun shouldPrompt(
        lineIndex: Int,
        matchScore: Float,
        triggerPercent: Int,
        lastPromptedLine: Int
    ): Boolean {
        // Don't re-prompt same line
        if (lineIndex <= lastPromptedLine) return false
        
        // Check if we've hit the trigger threshold
        return matchScore >= (triggerPercent / 100f)
    }
}
```

### 3. PromptSpeaker

Handles TTS output to earpiece.

```kotlin
class PromptSpeaker @Inject constructor(
    private val context: Context,
    private val audioRouter: AudioRouter
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    
    // Initialise TTS engine
    suspend fun initialise(): Result<Unit>
    
    // Speak prompt text immediately
    fun speak(text: String, onComplete: () -> Unit = {})
    
    // Stop any current speech
    fun stop()
    
    // Configure voice/speed from settings
    fun configure(speed: Float, pitch: Float)
    
    // Cleanup
    fun release()
}
```

### 4. CountInPlayer

Plays metronome clicks for count-in.

```kotlin
class CountInPlayer @Inject constructor(
    private val context: Context,
    private val audioRouter: AudioRouter
) {
    // Play count-in clicks
    suspend fun playCountIn(
        bpm: Int,
        beats: Int,
        onBeat: (Int) -> Unit,       // Callback per beat for UI
        onComplete: () -> Unit
    )
    
    // Stop early if needed
    fun stop()
}
```

Uses `SoundPool` or `AudioTrack` for low-latency click playback.

### 5. AudioRouter

Manages audio output routing to Bluetooth.

```kotlin
class AudioRouter @Inject constructor(
    private val context: Context
) {
    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Check if Bluetooth audio is connected
    fun isBluetoothConnected(): Boolean
    
    // Get current output device
    fun getCurrentOutput(): AudioOutput
    
    // Force output to specific device (if possible)
    fun setPreferredOutput(output: AudioOutput)
}

enum class AudioOutput {
    BLUETOOTH,
    SPEAKER,
    AUTO
}
```

---

## Lyrics Import Pipeline

### LyricsSearchService

Orchestrates search across multiple sources.

```kotlin
class LyricsSearchService @Inject constructor(
    private val lrcLibApi: LrcLibApi,
    private val geniusApi: GeniusApi
) {
    // Search with fallback
    suspend fun search(query: String): Result<List<LyricsSearchResult>>
    
    // Fetch full lyrics for a result
    suspend fun fetchLyrics(result: LyricsSearchResult): Result<String>
}

data class LyricsSearchResult(
    val source: LyricsSource,
    val title: String,
    val artist: String,
    val id: String,                          // Source-specific ID
    val previewText: String?                 // First few lines
)

enum class LyricsSource {
    LRCLIB,
    GENIUS,
    MANUAL
}
```

### LrcLibApi

```kotlin
interface LrcLibApi {
    // GET https://lrclib.net/api/search?q={query}
    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<LrcLibSearchResult>
    
    // GET https://lrclib.net/api/get?artist_name={artist}&track_name={track}
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String
    ): LrcLibLyrics
}

data class LrcLibSearchResult(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Int?
)

data class LrcLibLyrics(
    val plainLyrics: String?,               // Unsynced lyrics
    val syncedLyrics: String?               // LRC format (timestamped)
)
```

### GeniusApi

```kotlin
interface GeniusApi {
    // GET https://api.genius.com/search?q={query}
    @GET("search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): GeniusSearchResponse
}

// Note: Genius API doesn't return lyrics directly
// Need to scrape from the song URL returned in search results
// Or use a library like genius-lyrics-api pattern
```

### ProcessLyricsUseCase

Transforms raw lyrics text into structured Song data.

```kotlin
class ProcessLyricsUseCase @Inject constructor() {
    
    fun process(
        rawLyrics: String,
        title: String,
        artist: String
    ): Song {
        // 1. Clean up text
        val cleaned = cleanLyrics(rawLyrics)
        
        // 2. Split into lines
        val lines = splitIntoLines(cleaned)
        
        // 3. Build LyricLine objects
        val lyricLines = lines.mapIndexed { index, text ->
            LyricLine(
                index = index,
                text = text,
                words = extractWords(text),
                promptText = generatePrompt(lines.getOrNull(index + 1), 4)
            )
        }
        
        // 4. Extract vocabulary
        val vocabulary = lyricLines
            .flatMap { it.words }
            .toSet()
        
        // 5. Build Song with defaults
        return Song(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            bpm = null,
            originalKey = null,
            performKey = null,
            timeSignature = "4/4",
            countInEnabled = true,
            countInBeats = 4,
            triggerPercent = 70,
            promptWordCount = 4,
            lines = lyricLines,
            vocabulary = vocabulary,
            setlistIds = emptyList(),
            tags = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    private fun cleanLyrics(raw: String): String {
        return raw
            // Remove [Verse 1], [Chorus], etc.
            .replace(Regex("\\[.*?\\]"), "")
            // Remove multiple blank lines
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
    
    private fun splitIntoLines(text: String): List<String> {
        return text
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    private fun extractWords(line: String): List<String> {
        return line
            .lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), "")  // Keep apostrophes for contractions
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
    }
    
    private fun generatePrompt(nextLine: String?, wordCount: Int): String {
        if (nextLine == null) return ""
        val words = nextLine.split(Regex("\\s+"))
        return words.take(wordCount).joinToString(" ")
    }
}
```

---

## Performance Mode Flow

### PerformViewModel

```kotlin
@HiltViewModel
class PerformViewModel @Inject constructor(
    private val voskEngine: VoskEngine,
    private val positionTracker: PositionTracker,
    private val promptSpeaker: PromptSpeaker,
    private val countInPlayer: CountInPlayer,
    private val songRepository: SongRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(PerformanceState.initial())
    val state: StateFlow<PerformanceState> = _state.asStateFlow()
    
    fun loadSong(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSong(songId)
            voskEngine.loadVocabulary(song.vocabulary)
            positionTracker.loadSong(song)
            _state.value = PerformanceState(
                status = PerformanceStatus.READY,
                currentLineIndex = 0,
                recognisedWords = emptyList(),
                lineConfidence = 0f,
                lastPromptedLine = -1,
                startTime = null,
                song = song
            )
        }
    }
    
    fun start() {
        viewModelScope.launch {
            val song = _state.value.song
            
            // Count-in if enabled
            if (song.countInEnabled && song.bpm != null) {
                _state.value = _state.value.copy(status = PerformanceStatus.COUNT_IN)
                countInPlayer.playCountIn(
                    bpm = song.bpm,
                    beats = song.countInBeats,
                    onBeat = { /* update UI */ },
                    onComplete = { startListening() }
                )
            } else {
                startListening()
            }
        }
    }
    
    private fun startListening() {
        _state.value = _state.value.copy(
            status = PerformanceStatus.LISTENING,
            startTime = System.currentTimeMillis()
        )
        
        voskEngine.startListening(
            onPartialResult = { partial ->
                handleRecognition(partial, isFinal = false)
            },
            onFinalResult = { final ->
                handleRecognition(final, isFinal = true)
            },
            onError = { error ->
                // Handle error - maybe show message but keep going
            }
        )
    }
    
    private fun handleRecognition(text: String, isFinal: Boolean) {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        // Update recognised buffer
        val updated = _state.value.recognisedWords + words
        val buffer = updated.takeLast(20)  // Rolling buffer of last 20 words
        
        // Check for prompt trigger
        val event = positionTracker.onWordsRecognised(buffer)
        
        when (event) {
            is PromptEvent.SpeakPrompt -> {
                promptSpeaker.speak(event.promptText)
                _state.value = _state.value.copy(
                    currentLineIndex = event.lineIndex,
                    lastPromptedLine = event.lineIndex,
                    recognisedWords = buffer
                )
            }
            is PromptEvent.LineCompleted -> {
                _state.value = _state.value.copy(
                    currentLineIndex = event.lineIndex + 1,
                    recognisedWords = buffer
                )
            }
            is PromptEvent.SongFinished -> {
                stop()
            }
            null -> {
                _state.value = _state.value.copy(recognisedWords = buffer)
            }
        }
    }
    
    fun stop() {
        voskEngine.stopListening()
        promptSpeaker.stop()
        _state.value = _state.value.copy(status = PerformanceStatus.FINISHED)
    }
}
```

---

## Build Configuration

### build.gradle.kts (app)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.boynetech.lyricprompter"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.boynetech.lyricprompter"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Vosk
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
```

### AndroidManifest.xml Permissions

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Microphone for voice recognition -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- Internet for lyrics search -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- Bluetooth for earpiece -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    
    <!-- Keep screen on during performance -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <application
        android:name=".LyricPrompterApp"
        ...>
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:keepScreenOn="true">
            ...
        </activity>
        
    </application>
</manifest>
```

---

## Vosk Model Setup

1. Download English model from https://alphacephei.com/vosk/models
   - Recommended: `vosk-model-small-en-us-0.15` (~40MB)
   - Or: `vosk-model-en-us-0.22` (~1GB) for better accuracy

2. Extract to `app/src/main/assets/vosk-model-small-en-us/`

3. Model loads at app startup:

```kotlin
class LyricPrompterApp : Application() {
    
    @Inject
    lateinit var voskEngine: VoskEngine
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialise Vosk in background
        CoroutineScope(Dispatchers.IO).launch {
            voskEngine.initialise()
        }
    }
}
```

---

## Testing Strategy

### Unit Tests
- `FuzzyMatcher` - various word sequence combinations
- `ProcessLyricsUseCase` - lyrics cleaning, splitting, vocabulary extraction
- `PromptTrigger` - threshold logic, no double-prompting

### Integration Tests
- `VoskEngine` - vocabulary loading, recognition streaming
- `PositionTracker` - full recognition → prompt flow
- `LyricsSearchService` - API responses, fallback logic

### UI Tests
- Navigation flows
- Performance mode start/stop
- Song editing saves correctly

### Manual Testing
- Real singing with earpiece
- Various ambient noise levels
- Different song tempos
- Bluetooth connectivity

---

## Phase 1 Implementation Order

1. **Project Setup**
   - Create Android project with dependencies
   - Set up Hilt modules
   - Add Vosk model to assets

2. **Data Layer**
   - Room database and entities
   - SongRepository
   - Basic CRUD operations

3. **Lyrics Processing**
   - ProcessLyricsUseCase
   - Text cleaning and splitting
   - Manual lyrics entry UI

4. **Vosk Integration**
   - VoskEngine initialisation
   - Vocabulary loading
   - Basic recognition test

5. **Position Tracking**
   - FuzzyMatcher
   - PositionTracker
   - PromptTrigger

6. **Audio Output**
   - PromptSpeaker (TTS)
   - CountInPlayer
   - Basic audio routing

7. **Performance Mode UI**
   - PerformScreen
   - PerformViewModel
   - Full flow integration

8. **Library UI**
   - LibraryScreen
   - AddSongScreen (manual entry)
   - SongEditorScreen

9. **Lyrics Search**
   - LrcLibApi integration
   - GeniusApi integration
   - Search UI

10. **Polish**
    - Settings screen
    - Setlist management
    - Error handling
    - Edge cases

---

## Known Challenges & Mitigations

| Challenge | Mitigation |
|-----------|------------|
| Recognition accuracy in noisy pub | Use constrained vocabulary, tune trigger %, allow manual position override |
| Bluetooth audio latency | Test with target earpiece, consider wired backup option |
| Vosk model size (~50MB) | Bundle in APK, load asynchronously at startup |
| Battery drain from constant listening | Optimise Vosk usage, pause when not performing |
| Lost position mid-song | Add "reset to line X" gesture, or tap current line to resync |
| Lyrics copyright | Only fetch on user request, don't cache from APIs, manual entry fallback |
