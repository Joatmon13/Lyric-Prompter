package com.lyricprompter.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricprompter.BuildConfig
import com.lyricprompter.R
import com.lyricprompter.audio.routing.AudioRouter
import com.lyricprompter.diagnostics.DiagnosticLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    audioRouter: AudioRouter,
    diagnosticLogger: DiagnosticLogger,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onSessionLogsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Persisted settings (DataStore). Sliders keep local state during a drag and
    // persist on release; re-seeded via remember(key) when the stored value changes.
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var triggerSlider by remember(settings.defaultTriggerPercent) {
        mutableFloatStateOf(settings.defaultTriggerPercent.toFloat())
    }
    var promptWordsSlider by remember(settings.defaultPromptWords) {
        mutableFloatStateOf(settings.defaultPromptWords.toFloat())
    }
    var cooldownSlider by remember(settings.defaultCooldownBeats) {
        mutableFloatStateOf(settings.defaultCooldownBeats.toFloat())
    }
    var ttsSlider by remember(settings.ttsSpeed) {
        mutableFloatStateOf(settings.ttsSpeed)
    }

    // Phone mic setting - stored via AudioRouter (already persisted in prefs)
    var usePhoneMic by remember { mutableStateOf(audioRouter.usePhoneMic) }

    // Check DND permission - refresh when returning to screen
    var isDndGranted by remember { mutableStateOf(checkDndPermission(context)) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDndGranted = checkDndPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    value = triggerSlider,
                    onValueChange = { triggerSlider = it },
                    onValueChangeFinished = { viewModel.setDefaultTriggerPercent(triggerSlider.toInt()) },
                    valueRange = 40f..90f,
                    steps = 9,
                    valueDisplay = "${triggerSlider.toInt()}%"
                )

                SliderSetting(
                    label = stringResource(R.string.settings_default_prompt_words),
                    value = promptWordsSlider,
                    onValueChange = { promptWordsSlider = it },
                    onValueChangeFinished = { viewModel.setDefaultPromptWords(promptWordsSlider.toInt()) },
                    valueRange = 0f..6f,
                    steps = 5,
                    valueDisplay = if (promptWordsSlider.toInt() == 0) {
                        stringResource(R.string.settings_prompt_words_full_line)
                    } else {
                        "${promptWordsSlider.toInt()} words"
                    }
                )

                SwitchSetting(
                    label = stringResource(R.string.settings_default_count_in),
                    checked = settings.defaultCountInEnabled,
                    onCheckedChange = { viewModel.setDefaultCountInEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timing Section
            SettingsSection(title = stringResource(R.string.settings_timing)) {
                SliderSettingWithDescription(
                    label = stringResource(R.string.settings_default_cooldown_beats),
                    description = stringResource(R.string.settings_default_cooldown_beats_description),
                    value = cooldownSlider,
                    onValueChange = { cooldownSlider = it },
                    onValueChangeFinished = { viewModel.setDefaultCooldownBeats(cooldownSlider.toInt()) },
                    valueRange = 1f..8f,
                    steps = 6,
                    valueDisplay = "${cooldownSlider.toInt()} beats"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Section
            SettingsSection(title = stringResource(R.string.settings_audio)) {
                SwitchSettingWithDescription(
                    label = stringResource(R.string.settings_use_phone_mic),
                    description = stringResource(R.string.settings_use_phone_mic_description),
                    checked = usePhoneMic,
                    onCheckedChange = {
                        usePhoneMic = it
                        audioRouter.usePhoneMic = it
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SliderSetting(
                    label = stringResource(R.string.settings_tts_speed),
                    value = ttsSlider,
                    onValueChange = { ttsSlider = it },
                    onValueChangeFinished = { viewModel.setTtsSpeed(ttsSlider) },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    valueDisplay = String.format("%.1fx", ttsSlider)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Section
            SettingsSection(title = stringResource(R.string.settings_display)) {
                SwitchSetting(
                    label = stringResource(R.string.settings_keep_screen_on),
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permissions Section
            SettingsSection(title = stringResource(R.string.settings_permissions)) {
                PermissionSetting(
                    label = stringResource(R.string.settings_dnd_access),
                    description = stringResource(R.string.settings_dnd_description),
                    isGranted = isDndGranted,
                    grantedText = stringResource(R.string.settings_dnd_granted),
                    notGrantedText = stringResource(R.string.settings_dnd_not_granted),
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Section
            SettingsSection(title = "Developer Tools") {
                // Session logging toggle
                var sessionLoggingEnabled by remember { mutableStateOf(diagnosticLogger.isEnabled) }
                SwitchSettingWithDescription(
                    label = "Enable Session Logging",
                    description = "Capture detailed logs during performance (may affect latency)",
                    checked = sessionLoggingEnabled,
                    onCheckedChange = {
                        sessionLoggingEnabled = it
                        diagnosticLogger.isEnabled = it
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSessionLogsClick)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Session Logs")
                        Text(
                            text = "View diagnostic logs from performance sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    valueDisplay: String = value.toInt().toString(),
    onValueChangeFinished: (() -> Unit)? = null
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
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun SliderSettingWithDescription(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueDisplay: String = value.toInt().toString(),
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
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

@Composable
private fun SwitchSettingWithDescription(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionSetting(
    label: String,
    description: String,
    isGranted: Boolean,
    grantedText: String,
    notGrantedText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isGranted) grantedText else notGrantedText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        Icon(
            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

private fun checkDndPermission(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.isNotificationPolicyAccessGranted
}
