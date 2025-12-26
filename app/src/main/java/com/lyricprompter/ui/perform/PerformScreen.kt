package com.lyricprompter.ui.perform

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricprompter.R
import com.lyricprompter.domain.model.PerformanceStatus
import com.lyricprompter.ui.theme.PerformanceAccent
import com.lyricprompter.ui.theme.PerformanceBackground
import com.lyricprompter.ui.theme.PerformanceCountIn
import com.lyricprompter.ui.theme.PerformanceListening
import com.lyricprompter.ui.theme.PerformanceReady
import com.lyricprompter.ui.theme.PerformanceText
import com.lyricprompter.ui.theme.PerformanceTypography

@Composable
fun PerformScreen(
    songId: String,
    modifier: Modifier = Modifier,
    viewModel: PerformViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle back button
    BackHandler {
        viewModel.stop()
        onBackClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PerformanceBackground)
    ) {
        when (val state = uiState) {
            is PerformUiState.Loading -> {
                LoadingContent()
            }
            is PerformUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onBackClick = onBackClick
                )
            }
            is PerformUiState.Ready -> {
                PerformContent(
                    state = state,
                    onStart = viewModel::start,
                    onStop = {
                        viewModel.stop()
                        onBackClick()
                    },
                    onRestart = viewModel::restart,
                    onBackClick = {
                        viewModel.stop()
                        onBackClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PerformanceAccent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
                style = PerformanceTypography.status,
                color = PerformanceText
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.error),
                style = PerformanceTypography.status,
                color = PerformanceAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = PerformanceTypography.status,
                color = PerformanceText
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBackClick) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun PerformContent(
    state: PerformUiState.Ready,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onBackClick: () -> Unit
) {
    val performanceState = state.state
    val song = performanceState.song

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top bar with close button and song info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = PerformanceText
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = song.title,
                    style = PerformanceTypography.status,
                    color = PerformanceText
                )
                song.displayKey?.let { key ->
                    Text(
                        text = key,
                        style = PerformanceTypography.songKey,
                        color = PerformanceAccent
                    )
                }
            }

            // Placeholder for symmetry
            Box(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = performanceState.status,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status"
            ) { status ->
                when (status) {
                    is PerformanceStatus.Ready -> {
                        ReadyContent(onStart = onStart)
                    }
                    is PerformanceStatus.CountIn -> {
                        CountInContent(
                            currentBeat = status.currentBeat,
                            totalBeats = status.totalBeats
                        )
                    }
                    is PerformanceStatus.Listening -> {
                        ListeningContent(
                            currentLineIndex = performanceState.currentLineIndex,
                            totalLines = song.lineCount,
                            currentLineText = song.lines.getOrNull(performanceState.currentLineIndex)?.text,
                            nextLineText = song.lines.getOrNull(performanceState.currentLineIndex + 1)?.text
                        )
                    }
                    is PerformanceStatus.Paused -> {
                        Text(
                            text = "Paused",
                            style = PerformanceTypography.status,
                            color = PerformanceAccent
                        )
                    }
                    is PerformanceStatus.Finished -> {
                        FinishedContent(
                            onRestart = onRestart,
                            onClose = onStop
                        )
                    }
                }
            }
        }

        // Bottom controls
        if (performanceState.status is PerformanceStatus.Listening) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PerformanceAccent
                    )
                ) {
                    Text(stringResource(R.string.perform_stop))
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.perform_ready),
            style = PerformanceTypography.status,
            color = PerformanceReady
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = PerformanceReady
            ),
            modifier = Modifier.size(120.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.perform_start),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun CountInContent(
    currentBeat: Int,
    totalBeats: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = currentBeat.toString(),
            style = PerformanceTypography.countIn,
            color = PerformanceCountIn
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.perform_count_in),
            style = PerformanceTypography.status,
            color = PerformanceText
        )
    }
}

@Composable
private fun ListeningContent(
    currentLineIndex: Int,
    totalLines: Int,
    currentLineText: String?,
    nextLineText: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status
        Text(
            text = stringResource(R.string.perform_line_of, currentLineIndex + 1, totalLines),
            style = PerformanceTypography.status,
            color = PerformanceListening
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Current line (backup display)
        currentLineText?.let { text ->
            Text(
                text = text,
                style = PerformanceTypography.currentLine,
                color = PerformanceText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Next line preview
        nextLineText?.let { text ->
            Text(
                text = text,
                style = PerformanceTypography.nextLine,
                color = PerformanceText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun FinishedContent(
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.perform_finished),
            style = PerformanceTypography.status,
            color = PerformanceAccent
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PerformanceAccent
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Again")
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PerformanceText.copy(alpha = 0.3f)
                )
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
