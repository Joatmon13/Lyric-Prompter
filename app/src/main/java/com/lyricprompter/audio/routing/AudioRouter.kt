package com.lyricprompter.audio.routing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio output routing, particularly for Bluetooth earpiece.
 */
@Singleton
class AudioRouter @Inject constructor(
    private val context: Context
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
}

/**
 * Audio output destination.
 */
enum class AudioOutput {
    BLUETOOTH,
    SPEAKER,
    AUTO
}
