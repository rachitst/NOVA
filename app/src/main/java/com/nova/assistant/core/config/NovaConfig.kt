package com.nova.assistant.core.config

/**
 * Central configuration for NOVA Assistant.
 *
 * [DEV] flag controls development diagnostics:
 * - When `DEV = true`: Visible on-screen Toasts and real-time state alerts are displayed for all pipeline transitions.
 * - When `DEV = false`: All diagnostic Toasts are completely silenced without needing to remove diagnostic code.
 */
object NovaConfig {
    var DEV: Boolean = true

    /**
     * DEV-only diagnostic override: force the built-in microphone instead of the earphone mic.
     * Used to isolate earbud-hardware audio quality from the software pipeline during testing.
     * MUST remain false in production.
     */
    var DEV_FORCE_BUILTIN_MIC: Boolean = false
}
