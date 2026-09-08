package com.nova.assistant.ai.parser

import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.actions.model.DeviceFeature
import com.nova.assistant.actions.model.MessagePlatform
import com.nova.assistant.actions.model.NovaAction
import java.util.Locale

/**
 * Natural language intent parser for NOVA.
 * Normalizes speech input, extracts intent semantics, and maps conversational requests
 * (e.g. "Hey NOVA, can you call Mom?", "Would you call Mom?") to strongly typed NovaAction models.
 *
 * Runs 100% locally on-device with sub-5ms latency.
 */
class NaturalLanguageIntentParser : CommandParser {

    override fun parse(transcript: String): NovaAction {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) {
            return NovaAction.Unknown(transcript)
        }

        // 1. Help & Capabilities
        if (isHelpQuery(normalized)) {
            return NovaAction.Help
        }

        // 2. Time Queries
        if (isTimeQuery(normalized)) {
            return NovaAction.GetTime
        }

        // 3. Phone Calls
        val callAction = parseCallQuery(normalized)
        if (callAction != null) return callAction

        // 4. Messaging (WhatsApp & SMS)
        val messageAction = parseMessageQuery(normalized)
        if (messageAction != null) return messageAction

        // 5. Alarms & Wake-up
        val alarmAction = parseAlarmQuery(normalized)
        if (alarmAction != null) return alarmAction

        // 6. Timers
        val timerAction = parseTimerQuery(normalized)
        if (timerAction != null) return timerAction

        // 7. Device Control (Flashlight, Mute, Wi-Fi, Bluetooth)
        val deviceControlAction = parseDeviceControlQuery(normalized)
        if (deviceControlAction != null) return deviceControlAction

        // 8. Notifications
        val notificationAction = parseNotificationQuery(normalized)
        if (notificationAction != null) return notificationAction

        // 9. App Launching
        val appAction = parseAppLaunchQuery(normalized)
        if (appAction != null) return appAction

        // 10. Web Search
        val searchAction = parseWebSearchQuery(normalized)
        if (searchAction != null) return searchAction

        // 11. Fallback for unrecognized commands
        return NovaAction.Unknown(transcript)
    }

    private fun normalize(text: String): String {
        var cleaned = text.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[?!.,;\"']"), " ") // keep colons for time parsing
            .replace(Regex("\\s+"), " ")
            .trim()

        // Strip wake phrases and conversational prefixes
        val prefixes = listOf(
            "hey nova can you please",
            "hey nova could you please",
            "hey nova would you please",
            "hey nova can you",
            "hey nova could you",
            "hey nova would you",
            "hey nova please",
            "hey nova",
            "ok nova",
            "hi nova",
            "nova can you please",
            "nova could you please",
            "nova would you please",
            "nova can you",
            "nova could you",
            "nova would you",
            "nova please",
            "nova",
            "can you please",
            "could you please",
            "would you please",
            "will you please",
            "can you",
            "could you",
            "would you",
            "will you",
            "please",
            "kindly",
            "i want you to",
            "i need you to",
            "i want to",
            "go ahead and",
            "help me to",
            "help me"
        )

        for (prefix in prefixes) {
            if (cleaned.startsWith("$prefix ")) {
                cleaned = cleaned.removePrefix("$prefix ").trim()
                break
            }
        }

        // Strip conversational suffixes
        val suffixes = listOf(
            "for me please",
            "please",
            "for me",
            "right now",
            "now",
            "thanks",
            "thank you",
            "nova"
        )

        for (suffix in suffixes) {
            if (cleaned.endsWith(" $suffix")) {
                cleaned = cleaned.removeSuffix(" $suffix").trim()
                break
            }
        }

        return cleaned
    }

    private fun isHelpQuery(text: String): Boolean {
        return text in setOf(
            "help",
            "what can you do",
            "what can i say",
            "show commands",
            "commands",
            "capabilities",
            "what are your features"
        )
    }

    private fun isTimeQuery(text: String): Boolean {
        val timePhrases = setOf(
            "what time is it",
            "what is the time",
            "whats the time",
            "what s the time",
            "current time",
            "time please",
            "check the time",
            "tell me the time",
            "tell me time",
            "the time",
            "time"
        )
        return text in timePhrases ||
               text.startsWith("what time") ||
               text.startsWith("tell me the time") ||
               (text.contains("time") && (text.contains("what") || text.contains("check") || text.contains("tell") || text.contains("current")))
    }

    private fun parseCallQuery(text: String): NovaAction? {
        val patterns = listOf(
            Regex("^call (.+)$"),
            Regex("^phone (.+)$"),
            Regex("^dial (.+)$"),
            Regex("^make a call to (.+)$"),
            Regex("^make a phone call to (.+)$"),
            Regex("^place a call to (.+)$")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val recipient = match.groupValues[1].trim()
                if (recipient.isNotBlank() && recipient !in setOf("me", "a call", "phone")) {
                    return NovaAction.CallContact(capitalizeWords(recipient))
                }
            }
        }
        return null
    }

    private fun parseMessageQuery(text: String): NovaAction? {
        // WhatsApp patterns
        if (text.contains("whatsapp")) {
            val cleaned = text.replace("on whatsapp", "").replace("via whatsapp", "").trim()
            val patterns = listOf(
                Regex("^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?(?:\\s+to)?\\s+(.+?)(?:\\s+saying|\\s+with|\\s+text)?\\s+(.+)$"),
                Regex("^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?(?:\\s+to)?\\s+(.+)$")
            )
            for (pattern in patterns) {
                val match = pattern.find(cleaned)
                if (match != null) {
                    val recipient = match.groupValues[1].trim()
                    val message = if (match.groupValues.size > 2) match.groupValues[2].trim() else null
                    if (recipient.isNotBlank()) {
                        return NovaAction.SendMessage(
                            recipient = capitalizeWords(recipient),
                            messageText = message?.ifBlank { null },
                            platform = MessagePlatform.WHATSAPP
                        )
                    }
                }
            }
        }

        // SMS / General Message patterns
        val smsPatterns = listOf(
            Regex("^(?:send a message|send message|send a text|send text|text|message) to (.+?) saying (.+)$"),
            Regex("^(?:send a message|send message|send a text|send text|text|message) to (.+?) that (.+)$"),
            Regex("^text (.+?) (.+)$"),
            Regex("^(?:send a message|send message|send a text|send text|text|message) to (.+)$")
        )

        for (pattern in smsPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val recipient = match.groupValues[1].trim()
                val message = if (match.groupValues.size > 2) match.groupValues[2].trim() else null
                if (recipient.isNotBlank() && recipient !in setOf("me", "a message", "a text")) {
                    return NovaAction.SendMessage(
                        recipient = capitalizeWords(recipient),
                        messageText = message?.ifBlank { null },
                        platform = MessagePlatform.SMS
                    )
                }
            }
        }
        return null
    }

    private fun parseAlarmQuery(text: String): NovaAction? {
        val alarmPatterns = listOf(
            Regex("^(?:set an alarm|set alarm|alarm) for (.+)$"),
            Regex("^(?:set an alarm|set alarm|alarm) at (.+)$"),
            Regex("^wake me up at (.+)$"),
            Regex("^wake me up for (.+)$")
        )

        for (pattern in alarmPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val timePart = match.groupValues[1].trim()
                val (hour, minute) = parseTimeString(timePart)
                return NovaAction.SetAlarm(
                    rawTime = timePart,
                    hour = hour,
                    minute = minute,
                    label = null
                )
            }
        }
        return null
    }

    private fun parseTimeString(text: String): Pair<Int?, Int?> {
        val isPm = text.contains("pm") || text.contains("p m") || text.contains("evening") || text.contains("night")
        val isAm = text.contains("am") || text.contains("a m") || text.contains("morning")
        val clean = text.replace(Regex("[^0-9:]"), " ").trim()

        if (clean.contains(":")) {
            val parts = clean.split(":")
            var h = parts[0].trim().toIntOrNull()
            val m = parts.getOrNull(1)?.trim()?.split(" ")?.firstOrNull()?.toIntOrNull() ?: 0
            if (h != null) {
                if (isPm && h < 12) h += 12
                if (isAm && h == 12) h = 0
                return Pair(h, m)
            }
        } else {
            val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                var h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull() ?: 0
                if (h != null) {
                    if (isPm && h < 12) h += 12
                    if (isAm && h == 12) h = 0
                    return Pair(h, m)
                }
            } else if (parts.size == 1) {
                var h = parts[0].toIntOrNull()
                if (h != null) {
                    if (isPm && h < 12) h += 12
                    if (isAm && h == 12) h = 0
                    return Pair(h, 0)
                }
            }
        }
        return Pair(null, null)
    }

    private fun parseTimerQuery(text: String): NovaAction? {
        val timerPatterns = listOf(
            Regex("^(?:set a timer|set timer|timer) for (\\d+)\\s*(minute|minutes|min|mins|second|seconds|sec|secs|hour|hours|hr|hrs)$"),
            Regex("^(?:set a |set )?(\\d+)\\s*(minute|minutes|min|mins|second|seconds|sec|secs|hour|hours|hr|hrs) timer$")
        )

        for (pattern in timerPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: continue
                val unit = match.groupValues[2].lowercase(Locale.ROOT)
                val totalSeconds = when {
                    unit.startsWith("hour") || unit.startsWith("hr") -> value * 3600
                    unit.startsWith("min") -> value * 60
                    else -> value
                }
                return NovaAction.SetTimer(durationSeconds = totalSeconds)
            }
        }
        return null
    }

    private fun parseDeviceControlQuery(text: String): NovaAction? {
        // Flashlight / Torch
        if (text in setOf("turn on flashlight", "turn on the flashlight", "turn on torch", "flashlight on", "torch on", "enable flashlight")) {
            return NovaAction.DeviceControl(DeviceFeature.FLASHLIGHT, true)
        }
        if (text in setOf("turn off flashlight", "turn off the flashlight", "turn off torch", "flashlight off", "torch off", "disable flashlight")) {
            return NovaAction.DeviceControl(DeviceFeature.FLASHLIGHT, false)
        }

        // Volume Mute / Unmute
        if (text in setOf("mute", "mute volume", "mute phone", "silence", "mute sound")) {
            return NovaAction.DeviceControl(DeviceFeature.VOLUME_MUTE, true)
        }
        if (text in setOf("unmute", "unmute volume", "unmute phone", "unmute sound")) {
            return NovaAction.DeviceControl(DeviceFeature.VOLUME_MUTE, false)
        }

        // Wi-Fi
        if (text in setOf("turn on wifi", "turn on wi fi", "enable wifi", "enable wi fi", "wifi on", "open wifi")) {
            return NovaAction.DeviceControl(DeviceFeature.WIFI, true)
        }
        if (text in setOf("turn off wifi", "turn off wi fi", "disable wifi", "disable wi fi", "wifi off")) {
            return NovaAction.DeviceControl(DeviceFeature.WIFI, false)
        }

        // Bluetooth
        if (text in setOf("turn on bluetooth", "enable bluetooth", "bluetooth on", "open bluetooth")) {
            return NovaAction.DeviceControl(DeviceFeature.BLUETOOTH, true)
        }
        if (text in setOf("turn off bluetooth", "disable bluetooth", "bluetooth off")) {
            return NovaAction.DeviceControl(DeviceFeature.BLUETOOTH, false)
        }

        return null
    }

    private fun parseNotificationQuery(text: String): NovaAction? {
        val notificationPhrases = setOf(
            "read my notifications",
            "read notifications",
            "check notifications",
            "what are my notifications",
            "notifications",
            "read messages"
        )
        if (text in notificationPhrases) {
            return NovaAction.ReadNotifications()
        }

        val appNotificationPattern = Regex("^(?:read|check|show) (.+?) notifications$")
        val match = appNotificationPattern.find(text)
        if (match != null) {
            val app = match.groupValues[1].trim()
            return NovaAction.ReadNotifications(appFilter = app)
        }

        return null
    }

    private fun parseAppLaunchQuery(text: String): NovaAction? {
        val prefixes = listOf("open ", "launch ", "start ", "run ")
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                val appName = text.removePrefix(prefix).trim()
                if (appName.isNotBlank()) {
                    val knownApp = AppTarget.KnownApp.findByIdentifier(appName)
                    return if (knownApp != null) {
                        NovaAction.OpenApp(knownApp)
                    } else {
                        NovaAction.OpenApp(AppTarget.NamedApp(appName))
                    }
                }
            }
        }

        // Direct known app identifier
        val knownApp = AppTarget.KnownApp.findByIdentifier(text)
        if (knownApp != null) {
            return NovaAction.OpenApp(knownApp)
        }

        return null
    }

    private fun parseWebSearchQuery(text: String): NovaAction? {
        val emptyPrefixes = setOf(
            "search google for",
            "search for",
            "search google",
            "google search for",
            "google search",
            "search",
            "google"
        )
        if (text in emptyPrefixes) return null

        val patterns = listOf(
            Regex("^search google for (.+)$"),
            Regex("^google search for (.+)$"),
            Regex("^google search (.+)$"),
            Regex("^search for (.+) on google$"),
            Regex("^search for (.+)$"),
            Regex("^search (.+) on google$"),
            Regex("^search (.+)$"),
            Regex("^google (.+)$"),
            Regex("^who is (.+)$"),
            Regex("^who was (.+)$")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val query = match.groupValues[1].trim()
                if (text == "google chrome") return null
                if (query.isNotEmpty() && query !in setOf("on google", "google", "for")) {
                    return NovaAction.WebSearch(query = query)
                }
            }
        }
        return null
    }

    private fun capitalizeWords(str: String): String {
        return str.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
