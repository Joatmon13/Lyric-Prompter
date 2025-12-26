package com.lyricprompter.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val processLyricsUseCase: ProcessLyricsUseCase,
    private val songRepository: SongRepository
) : ViewModel() {

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
                    val song = processLyricsUseCase.process(
                        rawLyrics = lyrics,
                        title = result.title,
                        artist = result.artist
                    )

                    // Save to repository
                    songRepository.saveSong(song)

                    _uiState.value = AddSongUiState.Success(song.id)
                }
                .onFailure { error ->
                    _uiState.value = AddSongUiState.Error(error.message ?: "Failed to import lyrics")
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
