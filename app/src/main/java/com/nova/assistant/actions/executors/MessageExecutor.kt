package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.MessagePlatform

/**
 * Interface for composing and sending messages via SMS or WhatsApp.
 */
interface MessageExecutor {
    /**
     * Executes message sending/composition.
     * @param recipient Contact name or phone number.
     * @param messageText Content of the message.
     * @param platform Target platform (SMS, WhatsApp, or DEFAULT).
     */
    fun sendMessage(
        recipient: String,
        messageText: String?,
        platform: MessagePlatform
    ): ActionResult
}
