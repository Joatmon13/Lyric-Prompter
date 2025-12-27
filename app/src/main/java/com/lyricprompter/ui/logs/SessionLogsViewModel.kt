package com.lyricprompter.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricprompter.data.local.db.SessionLogDao
import com.lyricprompter.data.local.db.entities.LogEventEntity
import com.lyricprompter.data.local.db.entities.SessionLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SessionLogsViewModel @Inject constructor(
    private val sessionLogDao: SessionLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionLogsUiState())
    val uiState: StateFlow<SessionLogsUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            sessionLogDao.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(
                    sessions = sessions.map { it.toDisplayItem() },
                    isLoading = false
                )
            }
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val session = sessionLogDao.getSession(sessionId)
            if (session != null) {
                _uiState.value = _uiState.value.copy(
                    selectedSession = session.toDisplayItem()
                )
                loadEventsForSession(sessionId)
            }
        }
    }

    private fun loadEventsForSession(sessionId: String) {
        viewModelScope.launch {
            sessionLogDao.getEventsForSession(sessionId).collect { events ->
                _uiState.value = _uiState.value.copy(
                    selectedSessionEvents = events.map { it.toDisplayItem() }
                )
            }
        }
    }

    fun clearSelectedSession() {
        _uiState.value = _uiState.value.copy(
            selectedSession = null,
            selectedSessionEvents = emptyList()
        )
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionLogDao.deleteSession(sessionId)
            clearSelectedSession()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            sessionLogDao.deleteAllSessions()
        }
    }

    private fun SessionLogEntity.toDisplayItem(): SessionDisplayItem {
        val durationStr = durationMs?.let { ms ->
            val seconds = ms / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
        } ?: "In progress"

        val avgScoreStr = averageMatchScore?.let { "${(it * 100).toInt()}%" } ?: "-"

        return SessionDisplayItem(
            id = id,
            songTitle = songTitle,
            songArtist = songArtist,
            dateTime = dateFormat.format(Date(startTime)),
            duration = durationStr,
            triggerPercent = triggerPercent,
            promptWordCount = promptWordCount,
            useFullLine = useFullLine,
            usePhoneMic = usePhoneMic,
            audioMode = audioMode,
            linesPrompted = linesPrompted,
            totalLines = totalLines,
            averageMatchScore = avgScoreStr,
            totalRecognitions = totalRecognitions,
            bluetoothConnected = bluetoothConnected,
            countInEnabled = countInEnabled,
            bpm = bpm
        )
    }

    private fun LogEventEntity.toDisplayItem(): EventDisplayItem {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        return EventDisplayItem(
            id = id,
            timestamp = timeStr,
            eventType = eventType,
            recognizedText = recognizedText,
            lineIndex = lineIndex,
            matchScore = matchScore?.let { "${(it * 100).toInt()}%" },
            promptText = promptText,
            thresholdMet = thresholdMet
        )
    }
}

data class SessionLogsUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionDisplayItem> = emptyList(),
    val selectedSession: SessionDisplayItem? = null,
    val selectedSessionEvents: List<EventDisplayItem> = emptyList()
)

data class SessionDisplayItem(
    val id: String,
    val songTitle: String,
    val songArtist: String,
    val dateTime: String,
    val duration: String,
    val triggerPercent: Int,
    val promptWordCount: Int,
    val useFullLine: Boolean,
    val usePhoneMic: Boolean,
    val audioMode: String,
    val linesPrompted: Int,
    val totalLines: Int,
    val averageMatchScore: String,
    val totalRecognitions: Int,
    val bluetoothConnected: Boolean,
    val countInEnabled: Boolean,
    val bpm: Int?
)

data class EventDisplayItem(
    val id: Long,
    val timestamp: String,
    val eventType: String,
    val recognizedText: String?,
    val lineIndex: Int?,
    val matchScore: String?,
    val promptText: String?,
    val thresholdMet: Boolean?
)
