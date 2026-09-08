package com.nova.assistant.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Android SpeechRecognizer implementation of [SpeechToTextEngine].
 *
 * NOTE ON OFFLINE BEHAVIOR:
 * Uses [RecognizerIntent.EXTRA_PREFER_OFFLINE] to request on-device recognition where supported by
 * the installed system speech recognition engine (e.g., Speech Services by Google on Android 12+).
 * However, full offline capability is not guaranteed across all Android devices / manufacturers.
 */
class AndroidSpeechRecognizerEngine(
    private val context: Context
) : SpeechToTextEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening() {
        mainHandler.post {
            if (isCurrentlyListening) {
                NovaLogger.w("SpeechEngine", "startListening called while already listening. Cancelling prior session.")
                cancelListeningInternal()
            }

            if (!isRecognitionAvailable()) {
                NovaLogger.e("SpeechEngine", "Speech recognition service is not available on this device.")
                _speechState.value = SpeechState.Error(
                    SpeechError.RECOGNITION_UNAVAILABLE,
                    "Speech recognition is not available on this device."
                )
                return@post
            }

            ensureRecognizerInitialized()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Request on-device recognition where supported
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            try {
                speechRecognizer?.startListening(intent)
                isCurrentlyListening = true
                _speechState.value = SpeechState.Listening
                NovaLogger.i("SpeechEngine", "Speech recognition started.")
            } catch (e: Exception) {
                NovaLogger.e("SpeechEngine", "Exception starting SpeechRecognizer", e)
                isCurrentlyListening = false
                _speechState.value = SpeechState.Error(
                    SpeechError.UNKNOWN,
                    "Failed to start speech recognition: ${e.localizedMessage}"
                )
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            if (isCurrentlyListening) {
                try {
                    speechRecognizer?.stopListening()
                    NovaLogger.d("SpeechEngine", "Speech recognition stopped listening.")
                } catch (e: Exception) {
                    NovaLogger.w("SpeechEngine", "Exception stopping SpeechRecognizer", e)
                }
            }
        }
    }

    override fun cancelListening() {
        mainHandler.post {
            cancelListeningInternal()
        }
    }

    private fun cancelListeningInternal() {
        if (isCurrentlyListening) {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                NovaLogger.w("SpeechEngine", "Exception cancelling SpeechRecognizer", e)
            }
            isCurrentlyListening = false
            _speechState.value = SpeechState.Idle
            NovaLogger.d("SpeechEngine", "Speech recognition cancelled.")
        }
    }

    override fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                NovaLogger.w("SpeechEngine", "Exception destroying SpeechRecognizer", e)
            }
            speechRecognizer = null
            isCurrentlyListening = false
            _speechState.value = SpeechState.Idle
            NovaLogger.d("SpeechEngine", "Speech recognition engine destroyed.")
        }
    }

    private fun ensureRecognizerInitialized() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                NovaLogger.d("SpeechEngine", "onReadyForSpeech")
                _speechState.value = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {
                NovaLogger.d("SpeechEngine", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // RMS amplitude change - not stored in state to prevent high-frequency re-compositions
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer
            }

            override fun onEndOfSpeech() {
                NovaLogger.d("SpeechEngine", "onEndOfSpeech")
                isCurrentlyListening = false
            }

            override fun onError(errorCode: Int) {
                isCurrentlyListening = false
                val (speechError, message) = mapErrorCode(errorCode)
                NovaLogger.w("SpeechEngine", "onError: code=$errorCode, type=$speechError, msg=$message")
                _speechState.value = SpeechState.Error(speechError, message)
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim() ?: ""
                NovaLogger.i("SpeechEngine", "onResults: recognized '${NovaLogger.sanitize(transcript)}'")
                if (transcript.isNotEmpty()) {
                    _speechState.value = SpeechState.FinalResult(transcript)
                } else {
                    _speechState.value = SpeechState.Error(
                        SpeechError.NO_SPEECH,
                        "No speech was recognized."
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim() ?: ""
                if (partial.isNotEmpty()) {
                    _speechState.value = SpeechState.PartialResult(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Vendor-specific events
            }
        }
    }

    private fun mapErrorCode(errorCode: Int): Pair<SpeechError, String> {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH ->
                SpeechError.NO_SPEECH to "No speech was recognized. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                SpeechError.TIMEOUT to "Speech timed out. Please try speaking again."
            SpeechRecognizer.ERROR_AUDIO ->
                SpeechError.AUDIO_ERROR to "Audio recording error occurred."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                SpeechError.PERMISSION_DENIED to "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                SpeechError.NETWORK_ERROR to "Network error during speech recognition."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                SpeechError.CLIENT_ERROR to "Speech recognition is currently busy."
            SpeechRecognizer.ERROR_CLIENT ->
                SpeechError.CLIENT_ERROR to "Speech recognition client error."
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                SpeechError.RECOGNITION_UNAVAILABLE to "Speech recognition service unavailable."
            else ->
                SpeechError.UNKNOWN to "Recognition error occurred ($errorCode)."
        }
    }
}
