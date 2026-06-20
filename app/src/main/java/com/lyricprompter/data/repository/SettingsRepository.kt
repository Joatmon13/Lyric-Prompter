package com.lyricprompter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lyricprompter.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_settings")

/**
 * Persists [AppSettings] using Jetpack DataStore (Preferences).
 *
 * Exposes a reactive [settings] flow for the UI and a one-shot [current] for
 * call sites that just need the latest snapshot (e.g. applying defaults when
 * creating a song).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TRIGGER_PERCENT = intPreferencesKey("default_trigger_percent")
        val PROMPT_WORDS = intPreferencesKey("default_prompt_words")
        val COUNT_IN_ENABLED = booleanPreferencesKey("default_count_in_enabled")
        val COOLDOWN_BEATS = intPreferencesKey("default_cooldown_beats")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val d = AppSettings.DEFAULT
        AppSettings(
            defaultTriggerPercent = prefs[Keys.TRIGGER_PERCENT] ?: d.defaultTriggerPercent,
            defaultPromptWords = prefs[Keys.PROMPT_WORDS] ?: d.defaultPromptWords,
            defaultCountInEnabled = prefs[Keys.COUNT_IN_ENABLED] ?: d.defaultCountInEnabled,
            defaultCooldownBeats = prefs[Keys.COOLDOWN_BEATS] ?: d.defaultCooldownBeats,
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: d.ttsSpeed,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn
        )
    }

    /** Latest persisted snapshot. */
    suspend fun current(): AppSettings = settings.first()

    suspend fun setDefaultTriggerPercent(value: Int) =
        edit { it[Keys.TRIGGER_PERCENT] = value }

    suspend fun setDefaultPromptWords(value: Int) =
        edit { it[Keys.PROMPT_WORDS] = value }

    suspend fun setDefaultCountInEnabled(value: Boolean) =
        edit { it[Keys.COUNT_IN_ENABLED] = value }

    suspend fun setDefaultCooldownBeats(value: Int) =
        edit { it[Keys.COOLDOWN_BEATS] = value }

    suspend fun setTtsSpeed(value: Float) =
        edit { it[Keys.TTS_SPEED] = value }

    suspend fun setKeepScreenOn(value: Boolean) =
        edit { it[Keys.KEEP_SCREEN_ON] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
