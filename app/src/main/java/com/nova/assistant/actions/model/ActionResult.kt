package com.nova.assistant.actions.model

/**
 * Standard error categories for action execution failures.
 */
enum class ActionErrorType {
    APP_NOT_FOUND,
    INVALID_PARAMETER,
    PERMISSION_REQUIRED,
    UNSUPPORTED_ACTION,
    EXECUTION_FAILED
}

/**
 * Strongly typed result returned by the ActionManager after executing an action.
 */
sealed interface ActionResult {
    val spokenResponse: String
    val displayFeedback: String

    /**
     * Action executed successfully.
     */
    data class Success(
        override val spokenResponse: String,
        override val displayFeedback: String = spokenResponse
    ) : ActionResult

    /**
     * Action failed or could not be validated.
     */
    data class Failure(
        override val spokenResponse: String,
        override val displayFeedback: String = spokenResponse,
        val errorType: ActionErrorType = ActionErrorType.EXECUTION_FAILED
    ) : ActionResult
}
