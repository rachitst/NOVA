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
import com.nova.assistant.core.logging.NovaLogger

/**
 * Android implementation of CallExecutor.
 * Resolves contact numbers via ContactsContract and places calls via ACTION_CALL or ACTION_DIAL.
 */
class AndroidCallExecutor(
    private val context: Context
) : CallExecutor {

    override fun callContact(recipient: String): ActionResult {
        val trimmedRecipient = recipient.trim()
        if (trimmedRecipient.isBlank()) {
            NovaLogger.w("CallExecutor", "Call rejected: empty recipient")
            return ActionResult.Failure(
                spokenResponse = "Who would you like to call?",
                displayFeedback = "Recipient name or number was empty.",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }

        NovaLogger.i("CallExecutor", "Attempting to call recipient: '$trimmedRecipient'")

        // 1. Check if recipient is directly a phone number
        val phoneNumber = if (isDirectPhoneNumber(trimmedRecipient)) {
            trimmedRecipient
        } else {
            resolvePhoneNumber(trimmedRecipient)
        }

        if (phoneNumber == null) {
            NovaLogger.w("CallExecutor", "Could not find phone number for '$trimmedRecipient'")
            return ActionResult.Failure(
                spokenResponse = "I couldn't find a phone number for $trimmedRecipient in your contacts.",
                displayFeedback = "Contact '$trimmedRecipient' not found in contacts.",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }

        // 2. Determine whether to make direct call or open dialer
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intentAction = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(intentAction, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)

            val actionDescription = if (hasCallPermission) "Calling" else "Opening dialer for"
            val spokenText = "$actionDescription $trimmedRecipient."

            NovaLogger.i("CallExecutor", "Call initiated: $spokenText (number=$phoneNumber, directCall=$hasCallPermission)")
            ActionResult.Success(
                spokenResponse = spokenText,
                displayFeedback = "$actionDescription $trimmedRecipient ($phoneNumber)"
            )
        } catch (e: Exception) {
            NovaLogger.e("CallExecutor", "Failed to initiate call to $trimmedRecipient", e)
            ActionResult.Failure(
                spokenResponse = "Unable to place call to $trimmedRecipient.",
                displayFeedback = "Call initiation failed: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun isDirectPhoneNumber(text: String): Boolean {
        val cleaned = text.replace(Regex("[\\s\\-().]"), "")
        return cleaned.matches(Regex("^\\+?[0-9]{3,15}$"))
    }

    private fun resolvePhoneNumber(contactName: String): String? {
        val hasReadContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContactsPermission) {
            NovaLogger.w("CallExecutor", "READ_CONTACTS permission not granted. Cannot look up contact by name.")
            return null
        }

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex >= 0) {
                        cursor.getString(numberIndex)
                    } else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            NovaLogger.e("CallExecutor", "Error querying ContactsContract for '$contactName'", e)
            null
        }
    }
}
