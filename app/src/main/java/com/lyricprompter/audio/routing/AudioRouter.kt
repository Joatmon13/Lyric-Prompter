package com.lyricprompter.audio.routing

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio output routing, particularly for Bluetooth earpiece.
 * Also handles audio focus and Do Not Disturb mode for uninterrupted performance.
 */
@Singleton
class AudioRouter @Inject constructor(
    private val context: Context
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = context.getSharedPreferences("lyricprompter_prefs", Context.MODE_PRIVATE)

    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousDndMode: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var dndWasEnabled = false
    private var savedBluetoothVolume: Int = -1
    private var savedMusicVolume: Int = -1

    companion object {
        private const val TAG = "LP.Audio"
        private const val PREF_USE_PHONE_MIC = "use_phone_mic"
    }

    /**
     * Whether to use phone mic for recognition (better quality) while routing
     * TTS output to Bluetooth via A2DP only (no SCO).
     */
    var usePhoneMic: Boolean
        get() = prefs.getBoolean(PREF_USE_PHONE_MIC, false)
        set(value) = prefs.edit().putBoolean(PREF_USE_PHONE_MIC, value).apply()

    /**
     * Check if a Bluetooth audio device is connected.
     */
    fun isBluetoothConnected(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - check audio devices
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                }
            } else {
                // Older Android - use BluetoothAdapter
                @Suppress("DEPRECATION")
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                bluetoothAdapter?.isEnabled == true &&
                    (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                        bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission not granted", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth connection", e)
            false
        }
    }

    /**
     * Get the current audio output type.
     */
    fun getCurrentOutput(): AudioOutput {
        return when {
            isBluetoothConnected() -> AudioOutput.BLUETOOTH
            audioManager.isSpeakerphoneOn -> AudioOutput.SPEAKER
            else -> AudioOutput.SPEAKER
        }
    }

    /**
     * Start Bluetooth SCO for voice communication (low latency).
     * This is used for TTS prompts to ensure they go to the earpiece.
     */
    @Suppress("DEPRECATION")
    fun startBluetoothSco(): Boolean {
        return try {
            if (isBluetoothConnected()) {
                Log.i(TAG, "Bluetooth connected, starting SCO...")
                Log.d(TAG, "Current audio mode: ${audioManager.mode}")
                Log.d(TAG, "SCO audio state: ${audioManager.isBluetoothScoOn}")

                // Save current Bluetooth volume before mode change (MODE_IN_COMMUNICATION can reset it)
                savedBluetoothVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                val btMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                Log.d(TAG, "Saving volumes - VOICE_CALL: $savedBluetoothVolume, MUSIC: $btMusicVolume")

                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true

                // Restore volume if it was reset by mode change
                ensureVolumeNotMuted()

                Log.i(TAG, "Started Bluetooth SCO - mode: ${audioManager.mode}, sco on: ${audioManager.isBluetoothScoOn}")
                true
            } else {
                Log.w(TAG, "No Bluetooth audio device connected")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth SCO", e)
            false
        }
    }

    /**
     * Stop Bluetooth SCO.
     */
    @Suppress("DEPRECATION")
    fun stopBluetoothSco() {
        try {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "Stopped Bluetooth SCO")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Bluetooth SCO", e)
        }
    }

    /**
     * Set audio to route through speaker.
     */
    @Suppress("DEPRECATION")
    fun setSpeakerOutput() {
        try {
            stopBluetoothSco()
            audioManager.isSpeakerphoneOn = true
            Log.i(TAG, "Set speaker output")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speaker output", e)
        }
    }

    /**
     * Reset audio routing to default.
     */
    @Suppress("DEPRECATION")
    fun resetRouting() {
        try {
            stopBluetoothSco()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "Reset audio routing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset audio routing", e)
        }
    }

    /**
     * Request exclusive audio focus to prevent other apps from interrupting.
     * This will duck or pause other audio sources.
     *
     * NOTE: We use USAGE_MEDIA to match TTS output, ensuring consistent audio routing.
     */
    fun requestAudioFocus(): Boolean {
        return try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()

            audioFocusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            val success = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.i(TAG, "Audio focus request: ${if (success) "granted" else "denied"}")

            // Ensure volume wasn't muted by audio focus request
            if (success) {
                ensureVolumeNotMuted()
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
            false
        }
    }

    /**
     * Abandon audio focus when performance is done.
     */
    fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                Log.i(TAG, "Audio focus abandoned")
            }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
    }

    /**
     * Check if we have permission to modify Do Not Disturb settings.
     */
    fun canModifyDnd(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    /**
     * Get an intent to request DND permission from the user.
     */
    fun getDndPermissionIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    /**
     * Enable Do Not Disturb mode for performance.
     * Only works if permission was granted.
     */
    fun enableDnd(): Boolean {
        if (!canModifyDnd()) {
            Log.w(TAG, "No permission to modify DND")
            return false
        }

        return try {
            previousDndMode = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            dndWasEnabled = true
            Log.i(TAG, "DND enabled (was: $previousDndMode)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable DND", e)
            false
        }
    }

    /**
     * Restore previous Do Not Disturb setting.
     */
    fun restoreDnd() {
        if (!dndWasEnabled || !canModifyDnd()) return

        try {
            notificationManager.setInterruptionFilter(previousDndMode)
            Log.i(TAG, "DND restored to: $previousDndMode")
            dndWasEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore DND", e)
        }
    }

    /**
     * Enter performance mode.
     *
     * NOTE: We DO NOT request audio focus because it suspends Bluetooth A2DP
     * on some devices (including Pixel 9), causing TTS to have no audio output.
     * The side effect is that other apps can interrupt our performance, but
     * at least we can hear the prompts.
     *
     * NOTE: We also skip DND for now as it may block TTS output.
     *
     * TODO: Investigate alternative audio focus strategies that don't suspend A2DP.
     */
    fun enterPerformanceMode(enableDndMode: Boolean = true): Boolean {
        // Save current volumes before any audio mode changes
        savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        savedBluetoothVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        Log.d(TAG, "Entering performance mode - saved volumes: MUSIC=$savedMusicVolume, VOICE_CALL=$savedBluetoothVolume")

        // Skip audio focus - it suspends Bluetooth A2DP and causes silence
        // Skip DND - it may block TTS output
        Log.i(TAG, "Performance mode: skipping audio focus and DND to preserve A2DP/TTS")

        return true
    }

    /**
     * Ensure Bluetooth/media volume wasn't muted by audio mode changes.
     * Made public so it can be called after audio routing stabilizes.
     */
    fun ensureVolumeNotMuted() {
        val currentMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val maxMusicVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        Log.d(TAG, "[VOLUME_CHECK] " +
            "MUSIC=$currentMusicVolume/$maxMusicVolume | " +
            "VOICE_CALL=$currentCallVolume | " +
            "savedMusic=$savedMusicVolume | " +
            "savedBT=$savedBluetoothVolume")

        // Restore MUSIC volume if it was reset
        if (currentMusicVolume == 0 && savedMusicVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            Log.i(TAG, "Restored MUSIC volume from 0 to $savedMusicVolume")
        } else if (currentMusicVolume == 0 && savedMusicVolume <= 0) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val defaultVolume = (maxVolume * 0.7).toInt()  // 70% default
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, 0)
            Log.i(TAG, "Set MUSIC volume to default $defaultVolume")
        }

        // Restore VOICE_CALL volume if it was reset
        if (currentCallVolume == 0 && savedBluetoothVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedBluetoothVolume, 0)
            Log.i(TAG, "Restored VOICE_CALL volume from 0 to $savedBluetoothVolume")
        } else if (currentCallVolume == 0 && savedBluetoothVolume <= 0) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val defaultVolume = maxVolume / 2
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, defaultVolume, 0)
            Log.i(TAG, "Set VOICE_CALL volume to default $defaultVolume")
        }
    }

    /**
     * Start Bluetooth audio routing for TTS prompts.
     * Call this after count-in completes (if any) to avoid muting metronome.
     *
     * NOTE: We now ALWAYS skip SCO mode because:
     * 1. SCO causes volume to reset to zero on many devices
     * 2. TTS works fine over A2DP (normal media routing)
     * 3. SCO is really designed for phone calls, not TTS
     *
     * The usePhoneMic setting now only affects which mic is used for recognition,
     * not the audio output routing.
     *
     * TODO: Investigate if we can use SCO for Bluetooth earpiece mic without
     * causing volume reset. This would allow using the earpiece mic for voice
     * recognition when the phone is in a pocket. Current workaround requires
     * phone mic to be near the performer. See volume reset issue when calling
     * audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
     */
    fun startBluetoothForPrompts(): Boolean {
        val btConnected = isBluetoothConnected()

        // Always ensure volume is not muted before starting audio
        ensureVolumeNotMuted()

        // Log the audio configuration
        val audioSource = if (usePhoneMic) "PHONE_MIC" else "BT_MIC"
        Log.i(TAG, "[AUDIO_CONFIG] " +
            "mode=A2DP_ONLY | " +
            "micSource=$audioSource | " +
            "scoEnabled=false | " +
            "btConnected=$btConnected")

        // TTS will route through STREAM_MUSIC which goes to A2DP automatically
        // No need for SCO mode which causes volume issues
        val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "A2DP mode - MUSIC volume: $musicVol/$musicMax")

        return true
    }

    /**
     * Exit performance mode: restore audio and DND settings.
     */
    fun exitPerformanceMode() {
        restoreDnd()
        abandonAudioFocus()
        stopBluetoothSco()
    }
}

/**
 * Audio output destination.
 */
enum class AudioOutput {
    BLUETOOTH,
    SPEAKER,
    AUTO
}
