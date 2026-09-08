package com.nova.assistant.actions.executors

import com.nova.assistant.actions.model.ActionResult

/**
 * Interface for setting system alarms and countdown timers.
 */
interface ClockExecutor {
    /**
     * Sets a system alarm.
     */
    fun setAlarm(hour: Int, minute: Int, label: String?, rawTimeText: String): ActionResult

    /**
     * Sets a countdown timer.
     */
    fun setTimer(durationSeconds: Int, label: String?): ActionResult
}
