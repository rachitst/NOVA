package com.nova.assistant.ai

import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.actions.model.DeviceFeature
import com.nova.assistant.actions.model.MessagePlatform
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.ai.parser.NaturalLanguageIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying natural language parsing and intent mapping across conversational queries.
 */
class NaturalLanguageIntentParserTest {

    private lateinit var parser: NaturalLanguageIntentParser

    @Before
    fun setUp() {
        parser = NaturalLanguageIntentParser()
    }

    @Test
    fun `parse time query variants`() {
        val queries = listOf(
            "What time is it?",
            "Hey NOVA, what is the time?",
            "Can you tell me the time please?",
            "What's the current time?",
            "Tell me the time",
            "time please"
        )

        for (query in queries) {
            val action = parser.parse(query)
            assertEquals("Failed on: $query", NovaAction.GetTime, action)
        }
    }

    @Test
    fun `parse phone call variants`() {
        val testCases = mapOf(
            "Call Mom" to "Mom",
            "Hey NOVA, can you call Dad?" to "Dad",
            "Would you please call John Doe?" to "John Doe",
            "Dial +1234567890" to "+1234567890",
            "Please make a call to Alice" to "Alice",
            "Phone Bob" to "Bob"
        )

        for ((query, expectedRecipient) in testCases) {
            val action = parser.parse(query)
            assertTrue("Expected CallContact for '$query', got $action", action is NovaAction.CallContact)
            assertEquals(expectedRecipient, (action as NovaAction.CallContact).recipient)
        }
    }

    @Test
    fun `parse messaging queries for SMS and WhatsApp`() {
        // WhatsApp
        val waAction = parser.parse("Hey NOVA, send a whatsapp message to Mom saying I will be home soon")
        assertTrue(waAction is NovaAction.SendMessage)
        val wa = waAction as NovaAction.SendMessage
        assertEquals("Mom", wa.recipient)
        assertEquals("i will be home soon", wa.messageText)
        assertEquals(MessagePlatform.WHATSAPP, wa.platform)

        val directWa = parser.parse("whatsapp Alex see you at 5")
        assertTrue(directWa is NovaAction.SendMessage)
        val dwa = directWa as NovaAction.SendMessage
        assertEquals("Alex", dwa.recipient)
        assertEquals("see you at 5", dwa.messageText)
        assertEquals(MessagePlatform.WHATSAPP, dwa.platform)

        // SMS
        val smsAction = parser.parse("Send a message to Sarah saying running late")
        assertTrue(smsAction is NovaAction.SendMessage)
        val sms = smsAction as NovaAction.SendMessage
        assertEquals("Sarah", sms.recipient)
        assertEquals("running late", sms.messageText)
        assertEquals(MessagePlatform.SMS, sms.platform)

        val textAction = parser.parse("Text Dad hello there")
        assertTrue(textAction is NovaAction.SendMessage)
        val txt = textAction as NovaAction.SendMessage
        assertEquals("Dad", txt.recipient)
        assertEquals("hello there", txt.messageText)
    }

    @Test
    fun `parse alarm and timer queries`() {
        // Alarms
        val alarm1 = parser.parse("Set an alarm for 7:30 AM")
        assertTrue(alarm1 is NovaAction.SetAlarm)
        val a1 = alarm1 as NovaAction.SetAlarm
        assertEquals(7, a1.hour)
        assertEquals(30, a1.minute)

        val alarm2 = parser.parse("Wake me up at 6 PM")
        assertTrue(alarm2 is NovaAction.SetAlarm)
        val a2 = alarm2 as NovaAction.SetAlarm
        assertEquals(18, a2.hour)
        assertEquals(0, a2.minute)

        // Timers
        val timer1 = parser.parse("Set a timer for 5 minutes")
        assertTrue(timer1 is NovaAction.SetTimer)
        assertEquals(300, (timer1 as NovaAction.SetTimer).durationSeconds)

        val timer2 = parser.parse("Set a 30 second timer")
        assertTrue(timer2 is NovaAction.SetTimer)
        assertEquals(30, (timer2 as NovaAction.SetTimer).durationSeconds)
    }

    @Test
    fun `parse device control queries`() {
        val torchOn = parser.parse("Turn on flashlight")
        assertTrue(torchOn is NovaAction.DeviceControl)
        assertEquals(DeviceFeature.FLASHLIGHT, (torchOn as NovaAction.DeviceControl).feature)
        assertTrue(torchOn.state)

        val torchOff = parser.parse("Turn off torch")
        assertTrue(torchOff is NovaAction.DeviceControl)
        assertEquals(DeviceFeature.FLASHLIGHT, (torchOff as NovaAction.DeviceControl).feature)
        assertEquals(false, torchOff.state)

        val mute = parser.parse("Mute volume")
        assertTrue(mute is NovaAction.DeviceControl)
        assertEquals(DeviceFeature.VOLUME_MUTE, (mute as NovaAction.DeviceControl).feature)
        assertTrue(mute.state)
    }

    @Test
    fun `parse app launch queries`() {
        val chrome = parser.parse("Hey NOVA, can you please open Chrome?")
        assertTrue(chrome is NovaAction.OpenApp)
        assertEquals("chrome", (chrome as NovaAction.OpenApp).target.displayName.lowercase().let { if (it.contains("chrome")) "chrome" else it })

        val youtube = parser.parse("Launch YouTube")
        assertTrue(youtube is NovaAction.OpenApp)
        assertEquals(AppTarget.KnownApp.YOUTUBE.identifier, ((youtube as NovaAction.OpenApp).target as AppTarget.KnownApp).identifier)
    }

    @Test
    fun `parse web search queries`() {
        val search = parser.parse("Search Google for quantum computing")
        assertTrue(search is NovaAction.WebSearch)
        assertEquals("quantum computing", (search as NovaAction.WebSearch).query)

        val search2 = parser.parse("Google latest space news")
        assertTrue(search2 is NovaAction.WebSearch)
        assertEquals("latest space news", (search2 as NovaAction.WebSearch).query)
    }

    @Test
    fun `parse help query`() {
        val help = parser.parse("What can you do?")
        assertEquals(NovaAction.Help, help)
    }

    @Test
    fun `parse unknown query fallback`() {
        val unknown = parser.parse("Flapdoodle nonsensexyz")
        assertTrue(unknown is NovaAction.Unknown)
        assertEquals("Flapdoodle nonsensexyz", (unknown as NovaAction.Unknown).rawText)
    }
}
