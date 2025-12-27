package com.lyricprompter.ui.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.data.repository.SetlistRepository
import com.lyricprompter.data.repository.SongRepository
import com.lyricprompter.domain.model.Setlist
import com.lyricprompter.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SetlistViewModel @Inject constructor(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    val setlists: StateFlow<List<Setlist>> = setlistRepository.getAllSetlists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSongs: StateFlow<List<Song>> = songRepository.getAllSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSetlist = MutableStateFlow<Setlist?>(null)
    val currentSetlist: StateFlow<Setlist?> = _currentSetlist.asStateFlow()

    private val _setlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val setlistSongs: StateFlow<List<Song>> = _setlistSongs.asStateFlow()

    fun loadSetlist(setlistId: String) {
        viewModelScope.launch {
            val setlist = setlistRepository.getSetlistById(setlistId)
            _currentSetlist.value = setlist

            // Load songs in order
            setlist?.let { s ->
                val songs = s.songIds.mapNotNull { songId ->
                    songRepository.getSongById(songId)
                }
                _setlistSongs.value = songs
            }
        }
    }

    fun createSetlist(name: String) {
        viewModelScope.launch {
            val setlist = Setlist(
                id = UUID.randomUUID().toString(),
                name = name
            )
            setlistRepository.saveSetlist(setlist)
        }
    }

    fun deleteSetlist(setlistId: String) {
        viewModelScope.launch {
            setlistRepository.deleteSetlist(setlistId)
        }
    }

    fun addSongToSetlist(setlistId: String, songId: String) {
        viewModelScope.launch {
            setlistRepository.addSongToSetlist(setlistId, songId)
            loadSetlist(setlistId)
        }
    }

    fun removeSongFromSetlist(setlistId: String, songId: String) {
        viewModelScope.launch {
            setlistRepository.removeSongFromSetlist(setlistId, songId)
            loadSetlist(setlistId)
        }
    }

    fun reorderSongs(setlistId: String, songIds: List<String>) {
        viewModelScope.launch {
            setlistRepository.reorderSongs(setlistId, songIds)
            loadSetlist(setlistId)
        }
    }
}
