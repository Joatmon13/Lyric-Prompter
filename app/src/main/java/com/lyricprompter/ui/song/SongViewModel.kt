package com.lyricprompter.ui.song

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.BuildConfig
import com.lyricprompter.data.remote.bpm.BpmLookupService
import com.lyricprompter.data.remote.bpm.BpmResult
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
    private val processLyricsUseCase: ProcessLyricsUseCase,
    private val bpmLookupService: BpmLookupService
) : ViewModel() {

    companion object {
        private const val TAG = "SongViewModel"
    }

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _bpmRefreshState = MutableStateFlow<BpmRefreshState>(BpmRefreshState.Idle)
    val bpmRefreshState: StateFlow<BpmRefreshState> = _bpmRefreshState.asStateFlow()

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

    fun refreshBpm() {
        val currentSong = _song.value ?: return
        val apiKey = BuildConfig.GETSONGBPM_API_KEY

        if (apiKey.isBlank()) {
            Log.w(TAG, "No GetSongBPM API key configured")
            _bpmRefreshState.value = BpmRefreshState.Error("No API key configured")
            return
        }

        viewModelScope.launch {
            _bpmRefreshState.value = BpmRefreshState.Loading

            when (val result = bpmLookupService.lookupBpm(currentSong.title, currentSong.artist, apiKey)) {
                is BpmResult.Success -> {
                    Log.i(TAG, "Refreshed BPM for '${currentSong.title}': ${result.bpm}, time sig: ${result.timeSignature}, key: ${result.key}")
                    val timeSig = result.timeSignature ?: currentSong.timeSignature
                    val countInBeats = parseCountInBeatsFromTimeSignature(timeSig)
                    val updatedSong = currentSong.copy(
                        bpm = result.bpm,
                        originalKey = result.key ?: currentSong.originalKey,
                        timeSignature = timeSig,
                        countInBeats = countInBeats,
                        updatedAt = System.currentTimeMillis()
                    )
                    _song.value = updatedSong
                    songRepository.saveSong(updatedSong)
                    _bpmRefreshState.value = BpmRefreshState.Success
                }
                is BpmResult.NotFound -> {
                    Log.d(TAG, "No BPM found for '${currentSong.title}'")
                    _bpmRefreshState.value = BpmRefreshState.NotFound
                }
                is BpmResult.NoApiKey -> {
                    Log.d(TAG, "No API key for BPM lookup")
                    _bpmRefreshState.value = BpmRefreshState.Error("No API key configured")
                }
                is BpmResult.Error -> {
                    Log.w(TAG, "BPM lookup failed: ${result.message}")
                    _bpmRefreshState.value = BpmRefreshState.Error(result.message)
                }
            }
        }
    }

    fun clearBpmRefreshState() {
        _bpmRefreshState.value = BpmRefreshState.Idle
    }

    private fun parseCountInBeatsFromTimeSignature(timeSignature: String?): Int {
        if (timeSignature.isNullOrBlank()) return 4
        return try {
            val parts = timeSignature.split("/")
            val numerator = parts.getOrNull(0)?.toIntOrNull() ?: 4
            numerator.coerceIn(1, 8)
        } catch (e: Exception) {
            4
        }
    }
}

sealed interface BpmRefreshState {
    data object Idle : BpmRefreshState
    data object Loading : BpmRefreshState
    data object Success : BpmRefreshState
    data object NotFound : BpmRefreshState
    data class Error(val message: String) : BpmRefreshState
}
