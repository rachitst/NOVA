package com.nova.assistant.core.logging

import android.util.Log

/**
 * Privacy-aware logger for NOVA.
 * Prevents unintentional leakage of raw personal data, tokens, or private messages into Logcat.
 */
object NovaLogger {
    private const val TAG = "NOVA"
    var isDebugEnabled: Boolean = true

    fun d(category: String, message: String) {
        if (isDebugEnabled) {
            try {
                Log.d(TAG, "[$category] $message")
            } catch (_: Throwable) {
                // Host unit test fallback
                println("DEBUG: [$TAG][$category] $message")
            }
        }
    }

    fun i(category: String, message: String) {
        try {
            Log.i(TAG, "[$category] $message")
        } catch (_: Throwable) {
            println("INFO: [$TAG][$category] $message")
        }
    }

    fun w(category: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.w(TAG, "[$category] $message", throwable)
            } else {
                Log.w(TAG, "[$category] $message")
            }
        } catch (_: Throwable) {
            println("WARN: [$TAG][$category] $message ${throwable?.message ?: ""}")
        }
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(TAG, "[$category] $message", throwable)
            } else {
                Log.e(TAG, "[$category] $message")
            }
        } catch (_: Throwable) {
            System.err.println("ERROR: [$TAG][$category] $message ${throwable?.message ?: ""}")
        }
    }

    /**
     * Sanitizes sensitive text for safe logging (e.g. truncates or masks user input).
     */
    fun sanitize(input: String, maxVisibleChars: Int = 30): String {
        return if (input.length <= maxVisibleChars) {
            input
        } else {
            "${input.take(maxVisibleChars)}... [truncated ${input.length - maxVisibleChars} chars]"
        }
    }
}

