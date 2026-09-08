package com.nova.assistant.ai.parser

import com.nova.assistant.actions.model.NovaAction

/**
 * Interface for converting natural-language speech transcripts into structured NovaActions.
 */
interface CommandParser {
    /**
     * Parses the given transcript into a structured [NovaAction].
     */
    fun parse(transcript: String): NovaAction
}
