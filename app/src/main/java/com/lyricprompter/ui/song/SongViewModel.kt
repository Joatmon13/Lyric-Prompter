package com.lyricprompter.ui.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.data.repository.SongRepository
import com.lyricprompter.domain.model.Song
import com.lyricprompter.domain.usecase.ProcessLyricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val processLyricsUseCase: ProcessLyricsUseCase
) : ViewModel() {

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun loadSong(songId: String) {
        viewModelScope.launch {
            _song.value = songRepository.getSongById(songId)
        }
    }

    fun createNewSong(title: String, artist: String, lyrics: String): Song {
        return processLyricsUseCase.process(
            rawLyrics = lyrics,
            title = title,
            artist = artist
        )
    }

    fun saveSong(song: Song) {
        viewModelScope.launch {
            _isSaving.value = true
            songRepository.saveSong(song)
            _isSaving.value = false
        }
    }

    fun updateSong(
        title: String? = null,
        artist: String? = null,
        lyrics: String? = null,
        bpm: Int? = null,
        originalKey: String? = null,
        performKey: String? = null,
        timeSignature: String? = null,
        countInEnabled: Boolean? = null,
        countInBeats: Int? = null,
        triggerPercent: Int? = null,
        promptWordCount: Int? = null
    ) {
        val currentSong = _song.value ?: return

        var updatedSong = currentSong.copy(
            title = title ?: currentSong.title,
            artist = artist ?: currentSong.artist,
            bpm = bpm ?: currentSong.bpm,
            originalKey = originalKey ?: currentSong.originalKey,
            performKey = performKey ?: currentSong.performKey,
            timeSignature = timeSignature ?: currentSong.timeSignature,
            countInEnabled = countInEnabled ?: currentSong.countInEnabled,
            countInBeats = countInBeats ?: currentSong.countInBeats,
            triggerPercent = triggerPercent ?: currentSong.triggerPercent,
            promptWordCount = promptWordCount ?: currentSong.promptWordCount,
            updatedAt = System.currentTimeMillis()
        )

        // Reprocess lyrics if changed
        if (lyrics != null && lyrics != currentSong.lyricsText) {
            updatedSong = processLyricsUseCase.reprocess(updatedSong, lyrics)
        }

        // Update prompt word count if changed
        if (promptWordCount != null && promptWordCount != currentSong.promptWordCount) {
            updatedSong = processLyricsUseCase.updatePromptWordCount(updatedSong, promptWordCount)
        }

        _song.value = updatedSong
    }

    fun deleteSong(songId: String) {
        viewModelScope.launch {
            songRepository.deleteSong(songId)
        }
    }
}
