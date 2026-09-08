package com.nova.assistant.actions.executors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.MessagePlatform
import com.nova.assistant.core.logging.NovaLogger
import java.net.URLEncoder

/**
 * Android implementation of MessageExecutor.
 * Supports SMS composition/dispatch and WhatsApp messaging.
 */
class AndroidMessageExecutor(
    private val context: Context
) : MessageExecutor {

    override fun sendMessage(
        recipient: String,
        messageText: String?,
        platform: MessagePlatform
    ): ActionResult {
        val trimmedRecipient = recipient.trim()
        val text = messageText?.trim() ?: ""

        if (trimmedRecipient.isBlank()) {
            return ActionResult.Failure(
                spokenResponse = "Who would you like to message?",
                displayFeedback = "Recipient was not specified.",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }

        NovaLogger.i("MessageExecutor", "Processing message to '$trimmedRecipient' via $platform with text: '$text'")

        return when (platform) {
            MessagePlatform.WHATSAPP -> sendWhatsAppMessage(trimmedRecipient, text)
            MessagePlatform.SMS, MessagePlatform.DEFAULT -> sendSmsMessage(trimmedRecipient, text)
        }
    }

    private fun sendWhatsAppMessage(recipient: String, text: String): ActionResult {
        val phoneNumber = resolvePhoneNumber(recipient)
        val cleanedNumber = phoneNumber?.replace(Regex("[^0-9]"), "")

        return try {
            val intent = if (!cleanedNumber.isNullOrBlank()) {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanedNumber&text=$encodedText")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            // Check if WhatsApp is installed
            val packageManager = context.packageManager
            val resolvedActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

            if (resolvedActivities.isEmpty()) {
                // Try opening without package restriction or prompt install
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(text, "UTF-8")}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } else {
                context.startActivity(intent)
            }

            val spokenResponse = if (text.isNotBlank()) {
                "Opening WhatsApp to message $recipient."
            } else {
                "Opening WhatsApp chat with $recipient."
            }

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = "WhatsApp: $recipient: \"$text\""
            )
        } catch (e: Exception) {
            NovaLogger.e("MessageExecutor", "Failed to launch WhatsApp", e)
            ActionResult.Failure(
                spokenResponse = "Could not open WhatsApp.",
                displayFeedback = "Failed to launch WhatsApp: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun sendSmsMessage(recipient: String, text: String): ActionResult {
        val phoneNumber = resolvePhoneNumber(recipient) ?: recipient

        return try {
            val smsUri = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", text)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val spokenResponse = if (text.isNotBlank()) {
                "Sending message to $recipient."
            } else {
                "Opening messages for $recipient."
            }

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = "SMS to $recipient: \"$text\""
            )
        } catch (e: Exception) {
            NovaLogger.e("MessageExecutor", "Failed to prepare SMS", e)
            ActionResult.Failure(
                spokenResponse = "Could not open messaging app.",
                displayFeedback = "Failed to launch SMS: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun resolvePhoneNumber(contactName: String): String? {
        val cleaned = contactName.replace(Regex("[\\s\\-().]"), "")
        if (cleaned.matches(Regex("^\\+?[0-9]{3,15}$"))) {
            return cleaned
        }

        val hasReadContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContactsPermission) {
            return null
        }

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            NovaLogger.e("MessageExecutor", "Error resolving phone number for '$contactName'", e)
            null
        }
    }
}
