package com.lyricprompter.ui.add

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.BuildConfig
import com.lyricprompter.data.remote.bpm.BpmLookupService
import com.lyricprompter.data.remote.bpm.BpmResult
import com.lyricprompter.data.remote.lyrics.LyricsSearchResult
import com.lyricprompter.data.remote.lyrics.LyricsSearchService
import com.lyricprompter.data.repository.SongRepository
import com.lyricprompter.domain.usecase.ProcessLyricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddSongViewModel @Inject constructor(
    private val lyricsSearchService: LyricsSearchService,
    private val bpmLookupService: BpmLookupService,
    private val processLyricsUseCase: ProcessLyricsUseCase,
    private val songRepository: SongRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AddSongViewModel"
    }

    private val _uiState = MutableStateFlow<AddSongUiState>(AddSongUiState.Idle)
    val uiState: StateFlow<AddSongUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = AddSongUiState.Searching

            lyricsSearchService.search(query)
                .onSuccess { results ->
                    _uiState.value = if (results.isEmpty()) {
                        AddSongUiState.NoResults
                    } else {
                        AddSongUiState.Results(results)
                    }
                }
                .onFailure { error ->
                    _uiState.value = AddSongUiState.Error(error.message ?: "Search failed")
                }
        }
    }

    fun selectResult(result: LyricsSearchResult) {
        viewModelScope.launch {
            _uiState.value = AddSongUiState.Importing

            lyricsSearchService.fetchLyrics(result)
                .onSuccess { lyrics ->
                    // Create song from lyrics
                    var song = processLyricsUseCase.process(
                        rawLyrics = lyrics,
                        title = result.title,
                        artist = result.artist
                    )

                    // Try to look up BPM (non-blocking, best effort)
                    song = lookupAndApplyBpm(song)

                    // Save to repository
                    songRepository.saveSong(song)

                    _uiState.value = AddSongUiState.Success(song.id)
                }
                .onFailure { error ->
                    _uiState.value = AddSongUiState.Error(error.message ?: "Failed to import lyrics")
                }
        }
    }

    /**
     * Look up BPM for the song and apply it if found.
     * This is best-effort - if lookup fails, return original song.
     */
    private suspend fun lookupAndApplyBpm(song: com.lyricprompter.domain.model.Song): com.lyricprompter.domain.model.Song {
        val apiKey = BuildConfig.GETSONGBPM_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "No GetSongBPM API key configured, skipping BPM lookup")
            return song
        }

        return when (val result = bpmLookupService.lookupBpm(song.title, song.artist, apiKey)) {
            is BpmResult.Success -> {
                Log.i(TAG, "Found BPM for '${song.title}': ${result.bpm}, time sig: ${result.timeSignature}, key: ${result.key}")
                val timeSig = result.timeSignature ?: song.timeSignature
                // Calculate optimal count-in bars for the BPM
                val optimalBars = com.lyricprompter.domain.model.Song.calculateOptimalCountInBars(result.bpm, timeSig)
                Log.d(TAG, "Set count-in bars to $optimalBars for ${result.bpm} BPM")
                song.copy(
                    bpm = result.bpm,
                    originalKey = result.key ?: song.originalKey,
                    timeSignature = timeSig,
                    countInBars = optimalBars
                )
            }
            is BpmResult.NotFound -> {
                Log.d(TAG, "No BPM found for '${song.title}'")
                song
            }
            is BpmResult.NoApiKey -> {
                Log.d(TAG, "No API key for BPM lookup")
                song
            }
            is BpmResult.Error -> {
                Log.w(TAG, "BPM lookup failed: ${result.message}")
                song
            }
        }
    }

    fun reset() {
        _uiState.value = AddSongUiState.Idle
        _searchQuery.value = ""
    }
}

sealed interface AddSongUiState {
    data object Idle : AddSongUiState
    data object Searching : AddSongUiState
    data class Results(val results: List<LyricsSearchResult>) : AddSongUiState
    data object NoResults : AddSongUiState
    data object Importing : AddSongUiState
    data class Success(val songId: String) : AddSongUiState
    data class Error(val message: String) : AddSongUiState
}
