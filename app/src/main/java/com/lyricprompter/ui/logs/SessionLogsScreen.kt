package com.lyricprompter.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLogsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionLogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Logs?") },
            text = { Text("This will permanently delete all session logs. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllSessions()
                    showDeleteAllDialog = false
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectedSession != null) "Session Details"
                        else "Session Logs"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedSession != null) {
                            viewModel.clearSelectedSession()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedSession == null && uiState.sessions.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete All")
                        }
                    }
                    if (uiState.selectedSession != null) {
                        IconButton(onClick = {
                            uiState.selectedSession?.let { viewModel.deleteSession(it.id) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Session")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.selectedSession != null -> {
                    SessionDetailContent(
                        session = uiState.selectedSession!!,
                        events = uiState.selectedSessionEvents
                    )
                }
                uiState.sessions.isEmpty() -> {
                    Text(
                        text = "No session logs yet.\nPerform a song to start logging.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    SessionListContent(
                        sessions = uiState.sessions,
                        onSessionClick = { viewModel.selectSession(it.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionListContent(
    sessions: List<SessionDisplayItem>,
    onSessionClick: (SessionDisplayItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(sessions) { session ->
            SessionCard(
                session = session,
                onClick = { onSessionClick(session) }
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionDisplayItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Song title and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.songTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session.songArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = session.dateTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = session.duration,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Settings row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio mode indicator
                Text(
                    text = if (session.usePhoneMic) "Phone" else if (session.bluetoothConnected) "BT" else "Spkr",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.bluetoothConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Trigger: ${session.triggerPercent}%",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = if (session.useFullLine) "Full Line" else "${session.promptWordCount} words",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Prompted: ${session.linesPrompted}/${session.totalLines}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.linesPrompted > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Avg Score: ${session.averageMatchScore}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SessionDetailContent(
    session: SessionDisplayItem,
    events: List<EventDisplayItem>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        // Session summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = session.songTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = session.songArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Settings grid
                    Text(
                        text = "Session Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingRow("Date/Time", session.dateTime)
                    SettingRow("Duration", session.duration)
                    SettingRow("Audio Mode", session.audioMode)
                    SettingRow("Use Phone Mic", if (session.usePhoneMic) "Yes" else "No")
                    SettingRow("Bluetooth", if (session.bluetoothConnected) "Connected" else "Not Connected")
                    SettingRow("Trigger %", "${session.triggerPercent}%")
                    SettingRow("Prompt Words", if (session.useFullLine) "Full Line" else "${session.promptWordCount}")
                    SettingRow("Count-In", if (session.countInEnabled) "Yes" else "No")
                    session.bpm?.let { SettingRow("BPM", it.toString()) }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Results
                    Text(
                        text = "Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingRow("Lines Prompted", "${session.linesPrompted} / ${session.totalLines}")
                    SettingRow("Average Match Score", session.averageMatchScore)
                    SettingRow("Total Recognitions", session.totalRecognitions.toString())
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Events header
        item {
            Text(
                text = "Event Log (${events.size} events)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Event list
        items(events) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EventRow(event: EventDisplayItem) {
    val bgColor = when (event.eventType) {
        "PROMPT_FIRED" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        "THRESHOLD_MET" -> Color(0xFF2196F3).copy(alpha = 0.1f)
        "THRESHOLD_NOT_MET" -> Color(0xFFFF9800).copy(alpha = 0.05f)
        "SESSION_START", "SESSION_END" -> Color(0xFF9C27B0).copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = event.timestamp,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.eventType,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            event.recognizedText?.let { text ->
                Text(
                    text = "\"$text\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            event.promptText?.let { text ->
                Text(
                    text = "Prompt: \"$text\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = Color(0xFF4CAF50),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        event.matchScore?.let { score ->
            Text(
                text = score,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (event.thresholdMet == true) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.width(40.dp)
            )
        }

        event.lineIndex?.let { line ->
            Text(
                text = "L$line",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.width(30.dp)
            )
        }
    }
}
