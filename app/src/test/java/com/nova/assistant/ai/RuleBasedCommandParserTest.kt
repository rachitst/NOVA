package com.nova.assistant.ai

import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.ai.parser.RuleBasedCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleBasedCommandParserTest {

    private lateinit var parser: RuleBasedCommandParser

    @Before
    fun setUp() {
        parser = RuleBasedCommandParser()
    }

    // --- 1. App Launching Tests ---

    @Test
    fun parse_openChrome_returnsOpenAppChrome() {
        val variations = listOf(
            "Open Chrome",
            "open chrome",
            "Launch Chrome",
            "start chrome",
            "run chrome",
            "open Google Chrome",
            "Open Chrome!",
            "  open chrome   "
        )
        for (input in variations) {
            val action = parser.parse(input)
            assertTrue("Expected OpenApp for '$input' but was $action", action is NovaAction.OpenApp)
            val openApp = action as NovaAction.OpenApp
            assertEquals(AppTarget.KnownApp.CHROME, openApp.target)
        }
    }

    @Test
    fun parse_knownApps_returnsCorrectTarget() {
        val testCases = mapOf(
            "open youtube" to AppTarget.KnownApp.YOUTUBE,
            "launch maps" to AppTarget.KnownApp.MAPS,
            "open settings" to AppTarget.KnownApp.SETTINGS,
            "start whatsapp" to AppTarget.KnownApp.WHATSAPP
        )
        for ((input, expectedTarget) in testCases) {
            val action = parser.parse(input)
            assertTrue("Expected OpenApp for '$input' but was $action", action is NovaAction.OpenApp)
            assertEquals(expectedTarget, (action as NovaAction.OpenApp).target)
        }
    }

    @Test
    fun parse_genericNamedApp_returnsNamedAppTarget() {
        val action = parser.parse("open spotify")
        assertTrue(action is NovaAction.OpenApp)
        val openApp = action as NovaAction.OpenApp
        assertTrue(openApp.target is AppTarget.NamedApp)
        assertEquals("spotify", (openApp.target as AppTarget.NamedApp).rawName)
    }

    // --- 2. Time Query Tests ---

    @Test
    fun parse_timeQueries_returnsGetTime() {
        val variations = listOf(
            "What time is it?",
            "what time is it",
            "what is the time?",
            "What is the time",
            "whats the time",
            "what's the time?",
            "tell me the time",
            "current time",
            "time please",
            "check the time"
        )
        for (input in variations) {
            val action = parser.parse(input)
            assertEquals("Expected GetTime for '$input'", NovaAction.GetTime, action)
        }
    }

    // --- 3. Web Search Tests ---

    @Test
    fun parse_webSearchQueries_returnsWebSearchWithExtractedQuery() {
        val testCases = mapOf(
            "Search Google for Kotlin" to "kotlin",
            "search google for android architecture" to "android architecture",
            "Google search Jetpack Compose" to "jetpack compose",
            "google search for best hiking shoes" to "best hiking shoes",
            "search for easy treks on google" to "easy treks",
            "search weather in Tokyo" to "weather in tokyo",
            "google local restaurants" to "local restaurants"
        )
        for ((input, expectedQuery) in testCases) {
            val action = parser.parse(input)
            assertTrue("Expected WebSearch for '$input' but was $action", action is NovaAction.WebSearch)
            assertEquals(expectedQuery, (action as NovaAction.WebSearch).query)
        }
    }

    // --- 4. Unknown & Edge Case Tests ---

    @Test
    fun parse_emptyOrWhitespace_returnsUnknown() {
        assertEquals(NovaAction.Unknown(""), parser.parse(""))
        assertEquals(NovaAction.Unknown("   "), parser.parse("   "))
        assertEquals(NovaAction.Unknown("???"), parser.parse("???"))
    }

    @Test
    fun parse_unrecognizedCommands_returnsUnknown() {
        val unknowns = listOf(
            "sing me a lullaby",
            "tell me a joke",
            "who is the president",
            "make me coffee",
            "fly to the moon"
        )
        for (input in unknowns) {
            val action = parser.parse(input)
            assertTrue("Expected Unknown for '$input'", action is NovaAction.Unknown)
        }
    }

    @Test
    fun parse_emptySearchQuery_returnsUnknown() {
        val action = parser.parse("search google for")
        assertTrue(action is NovaAction.Unknown)
    }
}
