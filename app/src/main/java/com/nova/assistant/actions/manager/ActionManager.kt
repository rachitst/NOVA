package com.nova.assistant.actions.manager

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.NovaAction

/**
 * Security boundary and execution manager for NOVA actions.
 * All actions from the AI or parser must pass through this interface.
 */
interface ActionManager {
    /**
     * Validates and executes a [NovaAction].
     */
    suspend fun executeAction(action: NovaAction): ActionResult
}
