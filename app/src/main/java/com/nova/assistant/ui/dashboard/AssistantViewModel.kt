package com.nova.assistant.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.assistant.actions.manager.ActionManager
import com.nova.assistant.actions.manager.DefaultActionManager
import com.nova.assistant.ai.parser.CommandParser
import com.nova.assistant.ai.parser.NaturalLanguageIntentParser
import com.nova.assistant.android.earphones.EarphoneConnectionManager
import com.nova.assistant.core.logging.NovaLogger
import com.nova.assistant.core.model.AssistantState
import com.nova.assistant.voice.service.NovaVoiceService
import com.nova.assistant.voice.speaker.EnrollmentState
import com.nova.assistant.voice.speaker.LocalSpeakerEmbeddingVerifier
import com.nova.assistant.voice.speaker.SpeakerVerifier
import com.nova.assistant.voice.speaker.VoiceEnrollmentManager
import com.nova.assistant.voice.speech.AndroidSpeechRecognizerEngine
import com.nova.assistant.voice.speech.SpeechError
import com.nova.assistant.voice.speech.SpeechState
import com.nova.assistant.voice.speech.SpeechToTextEngine
import com.nova.assistant.voice.tts.AndroidTextToSpeechEngine
import com.nova.assistant.voice.tts.TextToSpeechEngine
import com.nova.assistant.voice.tts.TtsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating the unified Hands-Free NOVA assistant dashboard,
 * earphone connectivity monitoring, and secure voice enrollment workflows.
 */
class AssistantViewModel @JvmOverloads constructor(
    application: Application,
    private val speechToTextEngine: SpeechToTextEngine = AndroidSpeechRecognizerEngine(application),
    private val textToSpeechEngine: TextToSpeechEngine = AndroidTextToSpeechEngine(application),
    private val commandParser: CommandParser = NaturalLanguageIntentParser(),
    private val actionManager: ActionManager = DefaultActionManager(application),
    private val speakerVerifier: SpeakerVerifier = LocalSpeakerEmbeddingVerifier(application),
    private val earphoneConnectionManager: EarphoneConnectionManager = EarphoneConnectionManager(application),
    private val enrollmentManager: VoiceEnrollmentManager = VoiceEnrollmentManager(application, speakerVerifier)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            isEarphonesConnected = earphoneConnectionManager.checkConnected(),
            isSpeakerEnrolled = speakerVerifier.isEnrolled,
            enrolledSpeakerName = speakerVerifier.enrolledSpeakerName
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        observeSpeechEngine()
        observeTtsEngine()
        observeBackgroundService()
        observeEnrollmentState()
        observeEarphoneConnection()
        observeDevTelemetry()
    }

    private fun observeDevTelemetry() {
        viewModelScope.launch {
            com.nova.assistant.core.diagnostics.DevDiagnostics.telemetry.collect { telemetry ->
                _uiState.update { it.copy(devTelemetry = telemetry) }
            }
        }
    }

    private fun observeEarphoneConnection() {
        earphoneConnectionManager.register { isConnected ->
            _uiState.update { it.copy(isEarphonesConnected = isConnected) }
            NovaLogger.i("ViewModel", "Earphone status updated in UI: connected=$isConnected")
        }
        viewModelScope.launch {
            earphoneConnectionManager.isEarphonesConnected.collect { isConnected ->
                _uiState.update { it.copy(isEarphonesConnected = isConnected) }
            }
        }
    }

    private fun observeBackgroundService() {
        viewModelScope.launch {
            NovaVoiceService.isServiceRunning.collect { isRunning ->
                _uiState.update { it.copy(isBackgroundServiceRunning = isRunning) }
            }
        }
        viewModelScope.launch {
            NovaVoiceService.serviceStatusMessage.collect { status ->
                _uiState.update { it.copy(serviceStatusMessage = status) }
            }
        }
    }

    private fun observeEnrollmentState() {
        viewModelScope.launch {
            enrollmentManager.enrollmentState.collect { state ->
                val isEnrolled = speakerVerifier.isEnrolled
                val name = speakerVerifier.enrolledSpeakerName
                _uiState.update {
                    it.copy(
                        enrollmentState = state,
                        isSpeakerEnrolled = isEnrolled,
                        enrolledSpeakerName = name
                    )
                }
                if (state is EnrollmentState.Success) {
                    NovaVoiceService.syncState(getApplication())
                }
            }
        }
    }

    fun openEnrollmentDialog() {
        NovaVoiceService.pauseMicrophone(getApplication())
        _uiState.update { it.copy(isEnrollmentDialogOpen = true) }
    }

    fun dismissEnrollmentDialog() {
        enrollmentManager.cancelEnrollment()
        _uiState.update { it.copy(isEnrollmentDialogOpen = false) }
        NovaVoiceService.resumeMicrophone(getApplication())
    }

    fun startVoiceEnrollment(speakerName: String) {
        enrollmentManager.startEnrollmentSession(speakerName)
    }

    fun recordEnrollmentSample() {
        enrollmentManager.recordNextSample()
    }

    fun cancelVoiceEnrollment() {
        enrollmentManager.cancelEnrollment()
        NovaVoiceService.resumeMicrophone(getApplication())
    }

    fun clearVoiceEnrollment() {
        speakerVerifier.clearEnrollment()
        _uiState.update {
            it.copy(
                isSpeakerEnrolled = false,
                enrolledSpeakerName = null
            )
        }
        NovaVoiceService.syncState(getApplication())
    }

    fun toggleBackgroundService(context: Context, onNeedNotificationPermission: () -> Unit) {
        if (!_uiState.value.hasMicrophonePermission) {
            NovaLogger.w("ViewModel", "Cannot start background service without microphone permission.")
            return
        }

        if (_uiState.value.isBackgroundServiceRunning) {
            NovaLogger.i("ViewModel", "Stopping background NovaVoiceService from UI toggle.")
            NovaVoiceService.stopService(context)
        } else {
            NovaLogger.i("ViewModel", "Starting background NovaVoiceService from UI toggle.")
            onNeedNotificationPermission()
            NovaVoiceService.startService(context)
        }
    }

    private fun observeSpeechEngine() {
        viewModelScope.launch {
            speechToTextEngine.speechState.collect { speechState ->
                when (speechState) {
                    is SpeechState.Idle -> {
                        if (_uiState.value.assistantState is AssistantState.Listening) {
                            _uiState.update { it.copy(assistantState = AssistantState.Idle) }
                        }
                    }
                    is SpeechState.Listening -> {
                        _uiState.update {
                            it.copy(
                                assistantState = AssistantState.Listening,
                                liveTranscript = ""
                            )
                        }
                    }
                    is SpeechState.PartialResult -> {
                        _uiState.update {
                            it.copy(liveTranscript = speechState.partialText)
                        }
                    }
                    is SpeechState.FinalResult -> {
                        handleFinalTranscript(speechState.transcript)
                    }
                    is SpeechState.Error -> {
                        handleSpeechError(speechState.error, speechState.message)
                    }
                }
            }
        }
    }

    private fun observeTtsEngine() {
        viewModelScope.launch {
            textToSpeechEngine.ttsState.collect { ttsState ->
                val isReady = ttsState !is TtsState.Uninitialized && ttsState !is TtsState.Error
                _uiState.update { it.copy(isTtsReady = isReady) }
            }
        }
    }

    fun onPermissionStatusChanged(hasPermission: Boolean, isPermanentlyDenied: Boolean = false) {
        _uiState.update {
            it.copy(
                hasMicrophonePermission = hasPermission,
                isPermissionPermanentlyDenied = isPermanentlyDenied
            )
        }
    }

    fun onMicButtonClicked(requestPermissionCallback: () -> Unit) {
        val state = _uiState.value
        if (!state.hasMicrophonePermission) {
            NovaLogger.i("ViewModel", "Microphone permission not granted. Triggering permission request.")
            requestPermissionCallback()
            return
        }

        when (state.assistantState) {
            is AssistantState.Idle, is AssistantState.Error -> {
                textToSpeechEngine.stop()
                speechToTextEngine.startListening()
            }
            is AssistantState.Listening -> {
                speechToTextEngine.stopListening()
            }
            is AssistantState.Speaking -> {
                textToSpeechEngine.stop()
                _uiState.update { it.copy(assistantState = AssistantState.Idle) }
            }
            is AssistantState.Processing -> {}
        }
    }

    fun onCancelClicked() {
        speechToTextEngine.cancelListening()
        textToSpeechEngine.stop()
        _uiState.update {
            it.copy(
                assistantState = AssistantState.Idle,
                liveTranscript = ""
            )
        }
    }

    private fun handleFinalTranscript(transcript: String) {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(assistantState = AssistantState.Error("No speech detected.", canRetry = true))
            }
            return
        }

        _uiState.update {
            it.copy(
                assistantState = AssistantState.Processing(trimmed),
                liveTranscript = trimmed,
                lastTranscript = trimmed
            )
        }

        viewModelScope.launch {
            val action = commandParser.parse(trimmed)
            val result = actionManager.executeAction(action)

            val historyItem = ActionHistoryItem(
                transcript = trimmed,
                action = action,
                result = result
            )

            _uiState.update {
                it.copy(
                    assistantState = AssistantState.Speaking(result.spokenResponse, action),
                    lastAction = action,
                    lastResult = result,
                    actionHistory = listOf(historyItem) + it.actionHistory.take(19)
                )
            }

            textToSpeechEngine.speak(result.spokenResponse) {
                _uiState.update { current ->
                    if (current.assistantState is AssistantState.Speaking) {
                        current.copy(assistantState = AssistantState.Idle)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun handleSpeechError(error: SpeechError, message: String) {
        NovaLogger.w("ViewModel", "Speech error: $error ($message)")
        _uiState.update {
            it.copy(
                assistantState = AssistantState.Error("I didn't catch that.", canRetry = true)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechToTextEngine.destroy()
        textToSpeechEngine.shutdown()
        NovaLogger.d("ViewModel", "AssistantViewModel cleared.")
    }
}
