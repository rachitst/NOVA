package com.nova.assistant.actions.executors

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.DeviceFeature
import com.nova.assistant.core.logging.NovaLogger

/**
 * Android implementation of DeviceControlExecutor.
 * Controls Flashlight via CameraManager, Volume via AudioManager, and Settings panels.
 */
class AndroidDeviceControlExecutor(
    private val context: Context
) : DeviceControlExecutor {

    override fun setFeatureState(feature: DeviceFeature, state: Boolean): ActionResult {
        NovaLogger.i("DeviceControl", "Executing device control: feature=$feature, state=$state")

        return when (feature) {
            DeviceFeature.FLASHLIGHT -> setTorchMode(state)
            DeviceFeature.VOLUME_MUTE -> setMuteState(state)
            DeviceFeature.WIFI -> openWifiSettings(state)
            DeviceFeature.BLUETOOTH -> openBluetoothSettings(state)
        }
    }

    private fun setTorchMode(enable: Boolean): ActionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                return ActionResult.Failure(
                    spokenResponse = "Camera service is not available on this device.",
                    displayFeedback = "CameraManager was null.",
                    errorType = ActionErrorType.EXECUTION_FAILED
                )
            }

            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                return ActionResult.Failure(
                    spokenResponse = "No flashlight found on this device.",
                    displayFeedback = "No camera with flash capability found.",
                    errorType = ActionErrorType.UNSUPPORTED_ACTION
                )
            }

            cameraManager.setTorchMode(cameraId, enable)
            val stateText = if (enable) "on" else "off"
            val spokenResponse = "Flashlight turned $stateText."
            NovaLogger.i("DeviceControl", spokenResponse)

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = "Flashlight: ${stateText.uppercase()}"
            )
        } catch (e: Exception) {
            NovaLogger.e("DeviceControl", "Error toggling flashlight", e)
            ActionResult.Failure(
                spokenResponse = "Unable to toggle flashlight.",
                displayFeedback = "Flashlight error: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun setMuteState(mute: Boolean): ActionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                return ActionResult.Failure(
                    spokenResponse = "Audio service not available.",
                    displayFeedback = "AudioManager was null.",
                    errorType = ActionErrorType.EXECUTION_FAILED
                )
            }

            val targetVolume = if (mute) 0 else (audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) / 2)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVolume, AudioManager.FLAG_SHOW_UI)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVolume, 0)

            val spokenResponse = if (mute) "Volume muted." else "Volume unmuted."
            NovaLogger.i("DeviceControl", spokenResponse)

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = spokenResponse
            )
        } catch (e: Exception) {
            NovaLogger.e("DeviceControl", "Error setting volume mute", e)
            ActionResult.Failure(
                spokenResponse = "Could not adjust volume.",
                displayFeedback = "Audio error: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun openWifiSettings(state: Boolean): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success(
                spokenResponse = "Opening Wi-Fi settings.",
                displayFeedback = "Wi-Fi Settings"
            )
        } catch (e: Exception) {
            NovaLogger.e("DeviceControl", "Failed to open Wi-Fi settings", e)
            ActionResult.Failure(
                spokenResponse = "Could not open Wi-Fi settings.",
                displayFeedback = "Settings launch failed: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun openBluetoothSettings(state: Boolean): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success(
                spokenResponse = "Opening Bluetooth settings.",
                displayFeedback = "Bluetooth Settings"
            )
        } catch (e: Exception) {
            NovaLogger.e("DeviceControl", "Failed to open Bluetooth settings", e)
            ActionResult.Failure(
                spokenResponse = "Could not open Bluetooth settings.",
                displayFeedback = "Settings launch failed: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }
}
