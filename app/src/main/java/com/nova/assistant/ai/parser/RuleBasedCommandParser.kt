package com.nova.assistant.ai.parser

import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.actions.model.NovaAction
import java.util.Locale

/**
 * Deterministic rule- and regex-based command parser for Phase 1.
 * Safely parses basic commands without external ML or LLM dependencies.
 */
class RuleBasedCommandParser : CommandParser {

    override fun parse(transcript: String): NovaAction {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) {
            return NovaAction.Unknown(transcript)
        }

        // 1. Time Queries
        if (isTimeQuery(normalized)) {
            return NovaAction.GetTime
        }

        // 2. Web Search Queries
        val searchQuery = extractSearchQuery(normalized)
        if (searchQuery != null) {
            return if (searchQuery.isNotBlank()) {
                NovaAction.WebSearch(query = searchQuery)
            } else {
                NovaAction.Unknown(transcript)
            }
        }

        // 3. App Launch Queries
        val appName = extractAppLaunchName(normalized)
        if (appName != null) {
            return if (appName.isNotBlank()) {
                val knownApp = AppTarget.KnownApp.findByIdentifier(appName)
                if (knownApp != null) {
                    NovaAction.OpenApp(knownApp)
                } else {
                    NovaAction.OpenApp(AppTarget.NamedApp(appName))
                }
            } else {
                NovaAction.Unknown(transcript)
            }
        }

        // 4. Fallback for unrecognized commands
        return NovaAction.Unknown(transcript)
    }

    private fun normalize(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[?!.,;:\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isTimeQuery(text: String): Boolean {
        val timePhrases = setOf(
            "what time is it",
            "what is the time",
            "whats the time",
            "what s the time",
            "tell me the time",
            "current time",
            "time please",
            "check the time",
            "what is current time",
            "what s current time",
            "the time"
        )
        if (text in timePhrases) return true
        return text.startsWith("what time") ||
               text.startsWith("tell me the time") ||
               (text.contains("time") && (text.contains("what") || text.contains("tell")))
    }

    private fun extractSearchQuery(text: String): String? {
        val trimmed = text.trim()
        val emptyPrefixes = setOf(
            "search google for",
            "search for",
            "search google",
            "google search for",
            "google search",
            "search",
            "google"
        )
        if (trimmed in emptyPrefixes) {
            return null
        }

        val patterns = listOf(
            Regex("^search google for (.+)$"),
            Regex("^google search for (.+)$"),
            Regex("^google search (.+)$"),
            Regex("^search for (.+) on google$"),
            Regex("^search for (.+)$"),
            Regex("^search (.+) on google$"),
            Regex("^search (.+)$"),
            Regex("^google (.+)$")
        )

        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val query = match.groupValues[1].trim()
                // Avoid matching "google chrome" as search when it's an app request
                if (trimmed == "google chrome") return null
                if (query.isNotEmpty() && query !in setOf("on google", "google", "for")) {
                    return query
                }
            }
        }
        return null
    }

    private fun extractAppLaunchName(text: String): String? {
        val prefixes = listOf("open ", "launch ", "start ", "run ")
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                return text.removePrefix(prefix).trim()
            }
        }
        // Direct known app name check (e.g. user just said "chrome" or "google chrome")
        val knownApp = AppTarget.KnownApp.findByIdentifier(text)
        if (knownApp != null) {
            return knownApp.identifier
        }
        return null
    }
}
