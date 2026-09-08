package com.nova.assistant.actions.model

/**
 * Target application to open or interact with.
 */
sealed interface AppTarget {
    val displayName: String

    /**
     * Pre-defined well-known system/popular application.
     */
    data class KnownApp(
        val identifier: String,
        override val displayName: String,
        val primaryPackage: String,
        val fallbackPackages: List<String> = emptyList()
    ) : AppTarget {
        companion object {
            val CHROME = KnownApp(
                identifier = "chrome",
                displayName = "Google Chrome",
                primaryPackage = "com.android.chrome"
            )
            val YOUTUBE = KnownApp(
                identifier = "youtube",
                displayName = "YouTube",
                primaryPackage = "com.google.android.youtube"
            )
            val MAPS = KnownApp(
                identifier = "maps",
                displayName = "Google Maps",
                primaryPackage = "com.google.android.apps.maps"
            )
            val SETTINGS = KnownApp(
                identifier = "settings",
                displayName = "Settings",
                primaryPackage = "com.android.settings"
            )
            val WHATSAPP = KnownApp(
                identifier = "whatsapp",
                displayName = "WhatsApp",
                primaryPackage = "com.whatsapp"
            )

            val ALL = listOf(CHROME, YOUTUBE, MAPS, SETTINGS, WHATSAPP)

            fun findByIdentifier(query: String): KnownApp? {
                val normalized = query.trim().lowercase()
                return ALL.firstOrNull { app ->
                    app.identifier == normalized ||
                    app.displayName.lowercase() == normalized ||
                    app.displayName.lowercase().replace("google ", "") == normalized
                }
            }
        }
    }

    /**
     * Generic application requested by name (to be resolved dynamically via PackageManager).
     */
    data class NamedApp(
        val rawName: String
    ) : AppTarget {
        override val displayName: String get() = rawName
    }
}

/**
 * Strongly typed action representing a validated user intent.
 * The AI or command parser produces this structured action instead of executing raw commands.
 */
sealed interface NovaAction {
    /**
     * Launch an application on the device.
     */
    data class OpenApp(
        val target: AppTarget
    ) : NovaAction

    /**
     * Get the current local time.
     */
    data object GetTime : NovaAction

    /**
     * Search the web via default browser or Google search intent.
     */
    data class WebSearch(
        val query: String
    ) : NovaAction

    /**
     * Place a phone call to a named contact or phone number.
     */
    data class CallContact(
        val recipient: String
    ) : NovaAction

    /**
     * Send a direct message (SMS or WhatsApp) to a contact.
     */
    data class SendMessage(
        val recipient: String,
        val messageText: String? = null,
        val platform: MessagePlatform = MessagePlatform.DEFAULT
    ) : NovaAction

    /**
     * Set a system alarm for a specified time.
     */
    data class SetAlarm(
        val rawTime: String,
        val hour: Int? = null,
        val minute: Int? = null,
        val label: String? = null
    ) : NovaAction

    /**
     * Set a system countdown timer.
     */
    data class SetTimer(
        val durationSeconds: Int,
        val label: String? = null
    ) : NovaAction

    /**
     * Read aloud pending notifications.
     */
    data class ReadNotifications(
        val appFilter: String? = null
    ) : NovaAction

    /**
     * Control on-device hardware state (flashlight, volume, etc.).
     */
    data class DeviceControl(
        val feature: DeviceFeature,
        val state: Boolean
    ) : NovaAction

    /**
     * Request assistance or capabilities overview.
     */
    data object Help : NovaAction

    /**
     * Fallback for unknown, unsupported, or ambiguous natural language queries.
     */
    data class Unknown(
        val rawText: String
    ) : NovaAction
}

/**
 * Supported messaging platforms.
 */
enum class MessagePlatform {
    DEFAULT,
    SMS,
    WHATSAPP
}

/**
 * Controllable on-device hardware features.
 */
enum class DeviceFeature {
    FLASHLIGHT,
    VOLUME_MUTE,
    BLUETOOTH,
    WIFI
}
