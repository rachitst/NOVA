package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.AppTarget

/**
 * Interface responsible for validating and launching applications.
 */
interface AppLauncher {
    /**
     * Checks if the given app target is available on the device.
     */
    fun isAppInstalled(target: AppTarget): Boolean

    /**
     * Launches the target application.
     */
    fun launchApp(target: AppTarget): ActionResult
}
