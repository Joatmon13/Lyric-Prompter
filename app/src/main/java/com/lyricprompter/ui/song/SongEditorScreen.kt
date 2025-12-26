package com.lyricprompter.ui.song

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricprompter.R
import com.lyricprompter.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditorScreen(
    songId: String?,
    modifier: Modifier = Modifier,
    viewModel: SongViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onSaved: () -> Unit
) {
    val song by viewModel.song.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    // Local state for editing
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var bpm by remember { mutableStateOf("") }
    var originalKey by remember { mutableStateOf<String?>(null) }
    var performKey by remember { mutableStateOf<String?>(null) }
    var timeSignature by remember { mutableStateOf("4/4") }
    var countInEnabled by remember { mutableStateOf(true) }
    var countInBeats by remember { mutableFloatStateOf(4f) }
    var triggerPercent by remember { mutableFloatStateOf(70f) }
    var promptWordCount by remember { mutableFloatStateOf(4f) }

    // Load song if editing existing
    LaunchedEffect(songId) {
        if (songId != null) {
            viewModel.loadSong(songId)
        }
    }

    // Populate fields when song loads
    LaunchedEffect(song) {
        song?.let { s ->
            title = s.title
            artist = s.artist
            lyrics = s.lyricsText
            bpm = s.bpm?.toString() ?: ""
            originalKey = s.originalKey
            performKey = s.performKey
            timeSignature = s.timeSignature ?: "4/4"
            countInEnabled = s.countInEnabled
            countInBeats = s.countInBeats.toFloat()
            triggerPercent = s.triggerPercent.toFloat()
            promptWordCount = s.promptWordCount.toFloat()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (songId == null) "New Song" else stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (songId == null) {
                                // Create new song
                                val newSong = viewModel.createNewSong(title, artist, lyrics)
                                val songWithSettings = newSong.copy(
                                    bpm = bpm.toIntOrNull(),
                                    originalKey = originalKey,
                                    performKey = performKey,
                                    timeSignature = timeSignature,
                                    countInEnabled = countInEnabled,
                                    countInBeats = countInBeats.toInt(),
                                    triggerPercent = triggerPercent.toInt(),
                                    promptWordCount = promptWordCount.toInt()
                                )
                                viewModel.saveSong(songWithSettings)
                            } else {
                                // Update existing song
                                viewModel.updateSong(
                                    title = title,
                                    artist = artist,
                                    lyrics = lyrics,
                                    bpm = bpm.toIntOrNull(),
                                    originalKey = originalKey,
                                    performKey = performKey,
                                    timeSignature = timeSignature,
                                    countInEnabled = countInEnabled,
                                    countInBeats = countInBeats.toInt(),
                                    triggerPercent = triggerPercent.toInt(),
                                    promptWordCount = promptWordCount.toInt()
                                )
                                viewModel.song.value?.let { viewModel.saveSong(it) }
                            }
                            onSaved()
                        },
                        enabled = title.isNotBlank() && artist.isNotBlank() && lyrics.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Basic info
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.editor_song_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text(stringResource(R.string.editor_artist)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Musical settings
            Text(
                text = "Musical Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = bpm,
                    onValueChange = { bpm = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.editor_bpm)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                KeyDropdown(
                    label = stringResource(R.string.editor_perform_key),
                    selectedKey = performKey,
                    onKeySelected = { performKey = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Performance settings
            Text(
                text = "Performance Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Count-in toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_count_in),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = countInEnabled,
                    onCheckedChange = { countInEnabled = it }
                )
            }

            if (countInEnabled) {
                SliderSetting(
                    label = stringResource(R.string.editor_count_in_beats),
                    value = countInBeats,
                    onValueChange = { countInBeats = it },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }

            SliderSetting(
                label = stringResource(R.string.editor_trigger_percent),
                value = triggerPercent,
                onValueChange = { triggerPercent = it },
                valueRange = 40f..90f,
                steps = 9,
                valueDisplay = "${triggerPercent.toInt()}%"
            )

            SliderSetting(
                label = stringResource(R.string.editor_prompt_words),
                value = promptWordCount,
                onValueChange = { promptWordCount = it },
                valueRange = 2f..6f,
                steps = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics
            Text(
                text = stringResource(R.string.editor_lyrics),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                placeholder = { Text("Paste or type lyrics here...") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyDropdown(
    label: String,
    selectedKey: String?,
    onKeySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedKey ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onKeySelected(null)
                    expanded = false
                }
            )
            Song.KEYS.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key) },
                    onClick = {
                        onKeySelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: String = value.toInt().toString()
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
