package com.lyricprompter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lyricprompter.BuildConfig
import com.lyricprompter.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    // Settings state - would normally be stored in DataStore
    var defaultTriggerPercent by remember { mutableFloatStateOf(70f) }
    var defaultPromptWords by remember { mutableFloatStateOf(4f) }
    var defaultCountInEnabled by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var ttsSpeed by remember { mutableFloatStateOf(1.0f) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Default Settings Section
            SettingsSection(title = stringResource(R.string.settings_defaults)) {
                SliderSetting(
                    label = stringResource(R.string.settings_default_trigger),
                    value = defaultTriggerPercent,
                    onValueChange = { defaultTriggerPercent = it },
                    valueRange = 40f..90f,
                    steps = 9,
                    valueDisplay = "${defaultTriggerPercent.toInt()}%"
                )

                SliderSetting(
                    label = stringResource(R.string.settings_default_prompt_words),
                    value = defaultPromptWords,
                    onValueChange = { defaultPromptWords = it },
                    valueRange = 2f..6f,
                    steps = 3
                )

                SwitchSetting(
                    label = stringResource(R.string.settings_default_count_in),
                    checked = defaultCountInEnabled,
                    onCheckedChange = { defaultCountInEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Section
            SettingsSection(title = stringResource(R.string.settings_audio)) {
                SliderSetting(
                    label = stringResource(R.string.settings_tts_speed),
                    value = ttsSpeed,
                    onValueChange = { ttsSpeed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    valueDisplay = String.format("%.1fx", ttsSpeed)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Section
            SettingsSection(title = stringResource(R.string.settings_display)) {
                SwitchSetting(
                    label = stringResource(R.string.settings_keep_screen_on),
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
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
            Text(text = label, modifier = Modifier.weight(1f))
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

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
