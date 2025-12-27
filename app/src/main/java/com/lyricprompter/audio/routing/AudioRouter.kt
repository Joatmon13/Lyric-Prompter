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

    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousDndMode: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var dndWasEnabled = false

    companion object {
        private const val TAG = "AudioRouter"
    }

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
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "Started Bluetooth SCO")
                true
            } else {
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
     */
    fun requestAudioFocus(): Boolean {
        return try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
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
     * Enter performance mode: request audio focus and optionally enable DND.
     * Note: Bluetooth SCO should be started separately after count-in completes
     * to avoid muting the metronome/count-in audio.
     */
    fun enterPerformanceMode(enableDndMode: Boolean = true): Boolean {
        val focusGranted = requestAudioFocus()

        if (enableDndMode && canModifyDnd()) {
            enableDnd()
        }

        return focusGranted
    }

    /**
     * Start Bluetooth audio routing for TTS prompts.
     * Call this after count-in completes (if any) to avoid muting metronome.
     */
    fun startBluetoothForPrompts(): Boolean {
        return if (isBluetoothConnected()) {
            startBluetoothSco()
        } else {
            Log.i(TAG, "No Bluetooth connected, prompts will play through speaker")
            true
        }
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
