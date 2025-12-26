# CLAUDE.md - Lyric Prompter Project Guidelines

## Project Overview

**Lyric Prompter** is a native Android app that helps guitarists remember lyrics during performance. It uses Vosk (offline speech recognition) to track what the user is singing and speaks prompts for upcoming lines through an earpiece.

**Key Insight:** The app follows the performer's tempo, not the other way around. No beat matching, no karaoke-style timing - just voice recognition that tracks position and prompts at the right moment.

**Target User:** A guitarist who plays well but struggles with lyric recall. Uses phone + Bluetooth earpiece only (no pedals, tablets, or music stands).

## Architecture

### Pattern: MVVM + Clean Architecture

```
UI Layer (Compose)
    ↓
ViewModels (State holders)
    ↓
Use Cases (Business logic)
    ↓
Repositories (Data abstraction)
    ↓
Data Sources (Room DB, APIs, Vosk)
```

### Package Structure

```
com.lyricprompter/
├── di/                 # Hilt modules
├── data/
│   ├── local/          # Room database, DAOs, entities
│   ├── remote/         # API clients (LRCLIB, Genius)
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain models (Song, LyricLine, etc.)
│   └── usecase/        # Business logic use cases
├── audio/
│   ├── vosk/           # Speech recognition
│   ├── tts/            # Text-to-speech
│   └── routing/        # Audio output routing
├── tracking/           # Position tracking & fuzzy matching
├── ui/
│   ├── navigation/     # Nav graph
│   ├── theme/          # Colors, typography, theme
│   ├── components/     # Reusable composables
│   └── screens/        # Feature screens (library, perform, etc.)
└── util/               # Extensions, helpers
```

## Coding Conventions

### Kotlin Style

- Use `data class` for immutable data holders
- Prefer `sealed class` or `sealed interface` for state/events
- Use `Flow` for reactive streams, `StateFlow` for UI state
- Extension functions for cleaner APIs
- Avoid `!!` - use safe calls, `requireNotNull()`, or let the app crash meaningfully

```kotlin
// Good
sealed interface PerformanceStatus {
    data object Ready : PerformanceStatus
    data object CountIn : PerformanceStatus
    data class Listening(val currentLine: Int) : PerformanceStatus
    data object Finished : PerformanceStatus
}

// Bad
enum class PerformanceStatus {
    READY, COUNT_IN, LISTENING, FINISHED
}
```

### Compose Style

- One composable per file for screens
- Small reusable composables in `ui/components/`
- Use `Modifier` as first optional parameter
- Hoist state to ViewModel, keep composables stateless where possible
- Use `collectAsStateWithLifecycle()` for StateFlow

```kotlin
// Good
@Composable
fun PerformScreen(
    modifier: Modifier = Modifier,
    viewModel: PerformViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    PerformScreenContent(
        state = state,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        modifier = modifier
    )
}

@Composable
private fun PerformScreenContent(
    state: PerformanceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // UI implementation
}
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Composable screens | `XxxScreen` | `LibraryScreen`, `PerformScreen` |
| ViewModels | `XxxViewModel` | `LibraryViewModel` |
| Use cases | `XxxUseCase` | `ProcessLyricsUseCase` |
| Repositories | `XxxRepository` | `SongRepository` |
| Room entities | `XxxEntity` | `SongEntity` |
| Domain models | Plain name | `Song`, `LyricLine` |
| State classes | `XxxState` | `PerformanceState` |
| Event classes | `XxxEvent` | `PromptEvent` |

### File Naming

- Kotlin files: `PascalCase.kt` matching the main class/interface
- One public class per file (private helpers are fine)
- Test files: `XxxTest.kt` in matching test package

## Key Domain Concepts

### Song

The central data model. Contains:
- Metadata (title, artist, bpm, keys)
- Performance settings (triggerPercent, promptWordCount, countIn)
- Lyrics as `List<LyricLine>`
- Vocabulary as `Set<String>` (pre-extracted for Vosk)

### LyricLine

```kotlin
data class LyricLine(
    val index: Int,
    val text: String,           // "Is this the real life"
    val words: List<String>,    // ["is", "this", "the", "real", "life"]
    val promptText: String      // "Is this just fantasy" (from NEXT line)
)
```

**Important:** `promptText` contains the prompt for the NEXT line, not this line. When line N is recognised, we speak `lines[N].promptText` which prompts line N+1.

### Trigger Logic

The app prompts when `triggerPercent` of the current line's words have been recognised. This is configurable per song (default 70%).

```
triggerPercent = 70
Line words: ["is", "this", "the", "real", "life"]  // 5 words
Recognised: ["is", "this", "the", "real"]          // 4 words = 80%
80% >= 70% → TRIGGER PROMPT
```

### Vocabulary

Each song has a pre-built vocabulary (set of unique words). This is loaded into Vosk before performing to constrain recognition and improve accuracy/speed.

## Critical Implementation Details

### Vosk Integration

```kotlin
// Loading vocabulary - use JSON grammar format
val grammar = "[\"${words.joinToString("\", \"")}\", \"[unk]\"]"
recognizer.setGrammar(grammar)
```

- Model loads at app startup (async)
- Vocabulary loads when song is selected (~1-2 sec)
- Recognition is streaming - partial results arrive continuously
- Always include `[unk]` token for unrecognised sounds

### Position Tracking Algorithm

1. Maintain rolling buffer of last ~20 recognised words
2. Use Longest Common Subsequence (LCS) to score match against current line
3. When score >= triggerPercent, fire prompt event
4. Advance to next line
5. Never re-prompt the same line (track `lastPromptedLine`)

```kotlin
// Fuzzy matching - don't require exact sequential match
// "is this real life" should match "is this the real life" reasonably well
fun matchScore(recognised: List<String>, lineWords: List<String>): Float {
    val lcs = longestCommonSubsequence(recognised, lineWords)
    return lcs.size.toFloat() / lineWords.size
}
```

### Audio Routing

- TTS and count-in should route to Bluetooth earpiece when connected
- Use `AudioManager` to check/set output
- Test with actual Bluetooth earpiece - latency matters

### TTS Prompt Timing

The prompt should arrive while the user is finishing the current line, giving them time to hear it before they need it. The triggerPercent controls this - lower = earlier prompt.

## State Management

### ViewModel Pattern

```kotlin
@HiltViewModel
class PerformViewModel @Inject constructor(
    private val voskEngine: VoskEngine,
    private val positionTracker: PositionTracker,
    private val promptSpeaker: PromptSpeaker
) : ViewModel() {
    
    private val _state = MutableStateFlow(PerformanceState.initial())
    val state: StateFlow<PerformanceState> = _state.asStateFlow()
    
    // Public functions for UI events
    fun start() { ... }
    fun stop() { ... }
    
    // Private state updates
    private fun updateState(transform: (PerformanceState) -> PerformanceState) {
        _state.update(transform)
    }
}
```

### UI Events

Use lambdas passed to composables, not event classes:

```kotlin
// Good
PerformScreenContent(
    onStart = viewModel::start,
    onStop = viewModel::stop
)

// Avoid (over-engineering for this app)
sealed class PerformUiEvent {
    object Start : PerformUiEvent()
    object Stop : PerformUiEvent()
}
```

## Database

### Room Entities

Store complex data as JSON strings:

```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    // ... simple fields ...
    val lyricsJson: String,      // JSON array of LyricLine
    val vocabularyJson: String,  // JSON array of strings
    val setlistIdsJson: String   // JSON array of strings
)
```

### Mappers

Always map between Entity and Domain model:

```kotlin
fun SongEntity.toDomain(): Song { ... }
fun Song.toEntity(): SongEntity { ... }
```

## Error Handling

### Use Result Type

```kotlin
suspend fun searchLyrics(query: String): Result<List<LyricsSearchResult>>

// Usage
searchLyrics(query)
    .onSuccess { results -> /* update state */ }
    .onFailure { error -> /* show error */ }
```

### User-Facing Errors

- Show snackbar for recoverable errors
- Show dialog for critical errors
- Never show technical messages - always human-readable

### Recognition Errors

- Don't stop performance on recognition errors
- Log the error, continue listening
- Maybe show subtle indicator that recognition is struggling

## Testing

### Unit Tests (Required)

- `FuzzyMatcher` - core matching algorithm
- `ProcessLyricsUseCase` - lyrics processing
- `PromptTrigger` - trigger logic
- Domain model functions

### Integration Tests (Important)

- `SongRepository` - database operations
- `LyricsSearchService` - API integration

### UI Tests (Nice to Have)

- Navigation flows
- Critical user journeys

### Test Naming

```kotlin
@Test
fun `matchScore returns 1 when all words match`() { ... }

@Test
fun `matchScore handles partial matches correctly`() { ... }
```

## Dependencies

### Use These

| Dependency | Purpose |
|------------|---------|
| Jetpack Compose | UI |
| Hilt | Dependency injection |
| Room | Local database |
| Vosk Android | Speech recognition |
| Retrofit + Gson | API calls |
| Kotlin Coroutines | Async |
| DataStore | Preferences |

### Avoid

- RxJava (use Coroutines/Flow)
- Dagger without Hilt (complexity not needed)
- Third-party UI libraries unless essential
- Multiple JSON libraries (stick with Gson)

## Performance Considerations

- Load Vosk model at app startup, not on first perform
- Pre-cache next song's vocabulary when playing setlists
- Keep recognised word buffer bounded (last 20 words)
- Use `LaunchedEffect` carefully - avoid recomposition loops
- Profile battery usage during extended listening

## Security & Privacy

- All speech recognition is on-device (Vosk)
- Lyrics stored locally only
- No analytics or tracking
- Online search only when user initiates
- Don't cache lyrics from APIs long-term (copyright)

## Git Conventions

### Commit Messages

```
feat: add lyrics search with LRCLIB
fix: prevent double-prompting same line
refactor: extract FuzzyMatcher to separate class
docs: update README with setup instructions
test: add unit tests for ProcessLyricsUseCase
```

### Branch Names

```
feature/lyrics-search
bugfix/double-prompt
refactor/position-tracker
```

## Common Pitfalls

### Don't

- ❌ Use `mutableStateOf` in ViewModel (use `MutableStateFlow`)
- ❌ Call suspend functions in composables without `LaunchedEffect`
- ❌ Block main thread with Vosk operations
- ❌ Assume Bluetooth is always connected
- ❌ Forget to release Vosk/TTS resources
- ❌ Use hardcoded strings (use string resources)

### Do

- ✅ Collect flows with lifecycle awareness
- ✅ Handle configuration changes gracefully
- ✅ Test with actual earpiece hardware
- ✅ Keep performance mode UI minimal
- ✅ Log recognition results during development
- ✅ Make trigger % tunable per song

## Getting Started

1. Read `FUNCTIONAL_SPEC.md` for user journeys and features
2. Read `TECHNICAL_SPEC.md` for architecture and implementation details
3. Set up Android project with dependencies from tech spec
4. Download Vosk model to assets
5. Follow implementation order in tech spec

## Questions to Ask

When implementing a feature, consider:

1. What's the user journey? (Check functional spec)
2. Where does this fit in the architecture?
3. What state does this need? Where does it live?
4. How does this fail? How do we handle it?
5. Does this affect performance mode latency?

## Contact

Project owner: Paul (Joatmon13)
Original Flutter app: https://github.com/Joatmon13/lyric_viewer
