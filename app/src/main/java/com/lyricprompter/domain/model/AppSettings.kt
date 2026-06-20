package com.lyricprompter.domain.model

/**
 * App-wide settings persisted via DataStore.
 *
 * Defaults here intentionally mirror the [Song] model defaults so that an
 * untouched install behaves exactly as before persistence existed.
 *
 * - [defaultTriggerPercent], [defaultPromptWords], [defaultCountInEnabled] are
 *   applied to newly created songs (spec F6 "Applied to new songs").
 * - [defaultCooldownBeats], [ttsSpeed], [keepScreenOn] are app-wide preferences.
 */
data class AppSettings(
    val defaultTriggerPercent: Int = 70,
    val defaultPromptWords: Int = 0, // 0 = full line (matches Song default)
    val defaultCountInEnabled: Boolean = true,
    val defaultCooldownBeats: Int = 2,
    val ttsSpeed: Float = 1.0f,
    val keepScreenOn: Boolean = true
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
