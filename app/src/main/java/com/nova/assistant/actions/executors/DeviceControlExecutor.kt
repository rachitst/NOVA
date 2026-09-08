package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.DeviceFeature

/**
 * Interface for controlling on-device hardware features (flashlight, volume, etc.).
 */
interface DeviceControlExecutor {
    /**
     * Toggles or sets the hardware feature state.
     */
    fun setFeatureState(feature: DeviceFeature, state: Boolean): ActionResult
}
