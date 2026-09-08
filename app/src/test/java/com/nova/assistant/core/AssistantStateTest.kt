package com.nova.assistant.core

import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.core.model.AssistantState
import com.nova.assistant.ui.dashboard.ActionHistoryItem
import com.nova.assistant.ui.dashboard.AssistantUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStateTest {

    @Test
    fun assistantState_defaultUiState_isIdleAndUnpermitted() {
        val defaultState = AssistantUiState()
        assertEquals(AssistantState.Idle, defaultState.assistantState)
        assertFalse(defaultState.hasMicrophonePermission)
        assertFalse(defaultState.isPermissionPermanentlyDenied)
        assertEquals("", defaultState.liveTranscript)
        assertTrue(defaultState.actionHistory.isEmpty())
    }

    @Test
    fun assistantState_stateTransitions_behaveAsExpected() {
        val listeningState: AssistantState = AssistantState.Listening
        assertTrue(listeningState is AssistantState.Listening)

        val processingState: AssistantState = AssistantState.Processing("open chrome")
        assertEquals("open chrome", (processingState as AssistantState.Processing).rawTranscript)

        val speakingState: AssistantState = AssistantState.Speaking("Opening Google Chrome.", NovaAction.GetTime)
        assertEquals("Opening Google Chrome.", (speakingState as AssistantState.Speaking).spokenResponse)

        val errorState: AssistantState = AssistantState.Error("Network error", canRetry = true)
        assertEquals("Network error", (errorState as AssistantState.Error).message)
        assertTrue(errorState.canRetry)
    }

    @Test
    fun actionHistoryItem_creationAndProperties() {
        val historyItem = ActionHistoryItem(
            transcript = "what time is it",
            action = NovaAction.GetTime,
            result = ActionResult.Success("It is 10:00 AM.", "It is 10:00 AM.")
        )

        assertEquals("what time is it", historyItem.transcript)
        assertEquals(NovaAction.GetTime, historyItem.action)
        assertTrue(historyItem.result is ActionResult.Success)
        assertEquals("It is 10:00 AM.", historyItem.result.spokenResponse)
    }
}
