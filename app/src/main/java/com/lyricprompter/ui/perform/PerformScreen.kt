package com.lyricprompter.ui.perform

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
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
    val context = LocalContext.current

    // Permission state
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PermissionChecker.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    // Request permission on first load
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
        if (!hasAudioPermission) {
            // Show permission required message
            PermissionRequiredContent(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onBackClick = onBackClick
            )
        } else {
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
}

@Composable
private fun PermissionRequiredContent(
    onRequestPermission: () -> Unit,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Microphone Permission Required",
                style = PerformanceTypography.status,
                color = PerformanceAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The app needs microphone access to listen to your singing and provide prompts.",
                style = PerformanceTypography.status,
                color = PerformanceText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = PerformanceAccent)
            ) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PerformanceText.copy(alpha = 0.3f)
                )
            ) {
                Text("Go Back")
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
                            currentBar = status.currentBar,
                            totalBars = status.totalBars,
                            currentBeatInBar = status.currentBeatInBar,
                            beatsPerBar = status.beatsPerBar
                        )
                    }
                    is PerformanceStatus.Listening -> {
                        ListeningContent(
                            currentLineIndex = performanceState.currentLineIndex,
                            totalLines = song.lineCount,
                            currentLineText = song.lines.getOrNull(performanceState.currentLineIndex)?.text,
                            currentLineWords = song.lines.getOrNull(performanceState.currentLineIndex)?.words ?: emptyList(),
                            nextLineText = song.lines.getOrNull(performanceState.currentLineIndex + 1)?.text,
                            recognizedWords = performanceState.recognizedWords,
                            lineConfidence = performanceState.lineConfidence
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
    currentBar: Int,
    totalBars: Int,
    currentBeatInBar: Int,
    beatsPerBar: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Bar indicator dots at top (small)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            repeat(totalBars) { barIndex ->
                val isCurrentBar = barIndex + 1 == currentBar
                val isPastBar = barIndex + 1 < currentBar
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when {
                                isPastBar -> PerformanceAccent
                                isCurrentBar -> PerformanceCountIn
                                else -> PerformanceText.copy(alpha = 0.3f)
                            },
                            shape = CircleShape
                        )
                )
            }
        }

        // Beat dots - visual metronome
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            repeat(beatsPerBar) { beatIndex ->
                val beatNumber = beatIndex + 1
                val isCurrentBeat = beatNumber == currentBeatInBar
                val isPastBeat = beatNumber < currentBeatInBar
                val isDownbeat = beatNumber == 1

                // Animate current beat
                val infiniteTransition = rememberInfiniteTransition(label = "beat_$beatNumber")
                val scale by infiniteTransition.animateFloat(
                    initialValue = if (isCurrentBeat) 1f else 1f,
                    targetValue = if (isCurrentBeat) 1.3f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 150),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "beat_scale_$beatNumber"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size((if (isDownbeat) 56.dp else 44.dp) * (if (isCurrentBeat) scale else 1f))
                        .background(
                            color = when {
                                isCurrentBeat -> PerformanceCountIn
                                isPastBeat -> PerformanceAccent.copy(alpha = 0.6f)
                                else -> PerformanceText.copy(alpha = 0.2f)
                            },
                            shape = CircleShape
                        )
                ) {
                    Text(
                        text = beatNumber.toString(),
                        style = PerformanceTypography.status,
                        color = when {
                            isCurrentBeat || isPastBeat -> PerformanceBackground
                            else -> PerformanceText.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simple text indicator
        Text(
            text = if (currentBar < totalBars) "Get ready..." else "Starting!",
            style = PerformanceTypography.status,
            color = PerformanceText.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListeningContent(
    currentLineIndex: Int,
    totalLines: Int,
    currentLineText: String?,
    currentLineWords: List<String>,
    nextLineText: String?,
    recognizedWords: List<String>,
    lineConfidence: Float
) {
    // Get last few recognized words for display
    val recentWords = recognizedWords.takeLast(8)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status with microphone indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Pulsing microphone indicator
            PulsingMicIndicator()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.perform_line_of, currentLineIndex + 1, totalLines),
                style = PerformanceTypography.status,
                color = PerformanceListening
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current line with word highlighting
        if (currentLineText != null && currentLineWords.isNotEmpty()) {
            HighlightedLine(
                originalText = currentLineText,
                lineWords = currentLineWords,
                recognizedWords = recognizedWords
            )
        } else {
            currentLineText?.let { text ->
                Text(
                    text = text,
                    style = PerformanceTypography.currentLine,
                    color = PerformanceText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
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

        Spacer(modifier = Modifier.height(32.dp))

        // Recognition feedback - show recent recognized words
        if (recentWords.isNotEmpty()) {
            RecognitionFeedback(words = recentWords)
        }
    }
}

@Composable
private fun PulsingMicIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    // Pulsing circle with inner dot to indicate recording
    Box(
        modifier = Modifier
            .size((20 * scale).dp)
            .background(
                color = PerformanceListening.copy(alpha = alpha * 0.3f),
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = PerformanceListening.copy(alpha = alpha),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner recording dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = PerformanceListening.copy(alpha = alpha),
                    shape = CircleShape
                )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightedLine(
    originalText: String,
    lineWords: List<String>,
    recognizedWords: List<String>
) {
    // Split original text into words while preserving spacing/punctuation
    val displayWords = originalText.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }

    // Track which line words have been recognized
    val recognizedSet = recognizedWords.map { it.lowercase() }.toSet()

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        var lineWordIndex = 0
        displayWords.forEach { word ->
            val trimmedWord = word.trim()
            val normalizedWord = trimmedWord.lowercase().replace(Regex("[^a-z0-9']"), "")

            if (trimmedWord.isBlank()) {
                // Preserve whitespace
                Text(
                    text = word,
                    style = PerformanceTypography.currentLine,
                    color = PerformanceText
                )
            } else {
                // Check if this word has been recognized
                val isRecognized = if (lineWordIndex < lineWords.size) {
                    val lineWord = lineWords[lineWordIndex]
                    recognizedSet.contains(lineWord)
                } else {
                    false
                }

                Text(
                    text = word,
                    style = PerformanceTypography.currentLine,
                    color = if (isRecognized) PerformanceAccent else PerformanceText.copy(alpha = 0.7f),
                    modifier = if (isRecognized) {
                        Modifier
                            .background(
                                color = PerformanceAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 2.dp)
                    } else {
                        Modifier
                    }
                )

                if (normalizedWord.isNotEmpty()) {
                    lineWordIndex++
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecognitionFeedback(words: List<String>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Heard:",
            style = PerformanceTypography.status,
            color = PerformanceText.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            words.forEach { word ->
                Text(
                    text = word,
                    style = PerformanceTypography.nextLine,
                    color = PerformanceListening,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .border(
                            width = 1.dp,
                            color = PerformanceListening.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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
