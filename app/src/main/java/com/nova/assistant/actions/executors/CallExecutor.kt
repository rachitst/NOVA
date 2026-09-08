package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult

/**
 * Interface for placing phone calls to named contacts or phone numbers.
 */
interface CallExecutor {
    /**
     * Executes phone call or dial action.
     * @param recipient Name of contact or raw phone number.
     */
    fun callContact(recipient: String): ActionResult
}
