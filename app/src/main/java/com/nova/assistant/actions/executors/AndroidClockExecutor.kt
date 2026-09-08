package com.nova.assistant.actions.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.core.logging.NovaLogger
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Android implementation of ClockExecutor.
 * Interacts directly with the Android system Clock application to create real alarms and countdown timers.
 */
class AndroidClockExecutor(
    private val context: Context
) : ClockExecutor {

    override fun setAlarm(
        hour: Int,
        minute: Int,
        label: String?,
        rawTimeText: String
    ): ActionResult {
        if (hour !in 0..23 || minute !in 0..59) {
            NovaLogger.w("ClockExecutor", "Invalid alarm time: $hour:$minute")
            return ActionResult.Failure(
                spokenResponse = "Invalid alarm time.",
                displayFeedback = "Time out of range: $hour:$minute",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }

        return try {
            val formattedTime = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
            val messageLabel = label?.ifBlank { "NOVA Alarm" } ?: "NOVA Alarm"

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, messageLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }

            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                NovaLogger.w("ClockExecutor", "No activity found to handle AlarmClock.ACTION_SET_ALARM")
                return ActionResult.Failure(
                    spokenResponse = "No clock app found to set the alarm.",
                    displayFeedback = "No clock app found on device.",
                    errorType = ActionErrorType.APP_NOT_FOUND
                )
            }

            context.startActivity(intent)

            val spokenResponse = "Alarm set for $formattedTime."
            NovaLogger.i("ClockExecutor", "Alarm created successfully: $spokenResponse via $resolved")

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = "Alarm: $formattedTime ($messageLabel)"
            )
        } catch (e: Exception) {
            NovaLogger.e("ClockExecutor", "Failed to create alarm", e)
            ActionResult.Failure(
                spokenResponse = "Could not set alarm.",
                displayFeedback = "Failed to launch clock app: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    override fun setTimer(durationSeconds: Int, label: String?): ActionResult {
        if (durationSeconds <= 0) {
            NovaLogger.w("ClockExecutor", "Invalid timer duration: $durationSeconds")
            return ActionResult.Failure(
                spokenResponse = "Invalid timer duration.",
                displayFeedback = "Duration must be positive: ${durationSeconds}s",
                errorType = ActionErrorType.INVALID_PARAMETER
            )
        }

        return try {
            val messageLabel = label?.ifBlank { "NOVA Timer" } ?: "NOVA Timer"
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, messageLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }

            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                NovaLogger.w("ClockExecutor", "No activity found to handle AlarmClock.ACTION_SET_TIMER")
                return ActionResult.Failure(
                    spokenResponse = "No clock app found to set the timer.",
                    displayFeedback = "No clock app found on device.",
                    errorType = ActionErrorType.APP_NOT_FOUND
                )
            }

            context.startActivity(intent)

            val durationFormatted = formatDuration(durationSeconds)
            val spokenResponse = "Timer set for $durationFormatted."
            NovaLogger.i("ClockExecutor", "Timer created successfully: $spokenResponse via $resolved")

            ActionResult.Success(
                spokenResponse = spokenResponse,
                displayFeedback = "Timer: $durationFormatted"
            )
        } catch (e: Exception) {
            NovaLogger.e("ClockExecutor", "Failed to create timer", e)
            ActionResult.Failure(
                spokenResponse = "Could not set timer.",
                displayFeedback = "Failed to launch timer: ${e.localizedMessage}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        val hrs = mins / 60
        val remainingMins = mins % 60

        return when {
            hrs > 0 && remainingMins > 0 -> "$hrs hour${if (hrs > 1) "s" else ""} $remainingMins minute${if (remainingMins > 1) "s" else ""}"
            hrs > 0 -> "$hrs hour${if (hrs > 1) "s" else ""}"
            mins > 0 && secs > 0 -> "$mins minute${if (mins > 1) "s" else ""} $secs second${if (secs > 1) "s" else ""}"
            mins > 0 -> "$mins minute${if (mins > 1) "s" else ""}"
            else -> "$secs second${if (secs > 1) "s" else ""}"
        }
    }
}
