package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult

/**
 * Interface for reading pending notifications.
 */
interface NotificationExecutor {
    /**
     * Reads pending notifications or guides the user to enable notification access.
     */
    fun readNotifications(appFilter: String?): ActionResult
}
