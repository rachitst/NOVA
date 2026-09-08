package com.nova.assistant.voice.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Android TextToSpeech implementation of [TextToSpeechEngine].
 */
class AndroidTextToSpeechEngine(
    context: Context
) : TextToSpeechEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Uninitialized)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val activeCallbacks = mutableMapOf<String, () -> Unit>()
    private var pendingUtterance: Pair<String, (() -> Unit)?>? = null

    init {
        NovaLogger.d("TtsEngine", "Initializing Android TextToSpeech engine...")
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    val tts = textToSpeech
                    if (tts != null) {
                        val result = tts.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            NovaLogger.w("TtsEngine", "Default locale not supported, falling back to Locale.US")
                            tts.language = Locale.US
                        }
                        setupProgressListener(tts)
                        isInitialized = true
                        _ttsState.value = TtsState.Ready
                        NovaLogger.i("TtsEngine", "TextToSpeech successfully initialized.")

                        // Execute pending utterance if any was requested during startup
                        pendingUtterance?.let { (text, onDone) ->
                            pendingUtterance = null
                            speak(text, onDone)
                        }
                    }
                } else {
                    NovaLogger.e("TtsEngine", "TextToSpeech initialization failed with code: $status")
                    isInitialized = false
                    _ttsState.value = TtsState.Error("TTS initialization failed (code $status).")
                    pendingUtterance?.second?.invoke()
                    pendingUtterance = null
                }
            }
        }
    }

    private fun setupProgressListener(tts: TextToSpeech) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                NovaLogger.d("TtsEngine", "Utterance started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    NovaLogger.d("TtsEngine", "Utterance finished: $utteranceId")
                    _ttsState.value = TtsState.Ready
                    if (utteranceId != null) {
                        val callback = activeCallbacks.remove(utteranceId)
                        callback?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    NovaLogger.w("TtsEngine", "Utterance error: $utteranceId")
                    _ttsState.value = TtsState.Error("TTS utterance failed.")
                    if (utteranceId != null) {
                        val callback = activeCallbacks.remove(utteranceId)
                        callback?.invoke()
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    NovaLogger.w("TtsEngine", "Utterance error (code $errorCode): $utteranceId")
                    _ttsState.value = TtsState.Error("TTS error code $errorCode.")
                    if (utteranceId != null) {
                        val callback = activeCallbacks.remove(utteranceId)
                        callback?.invoke()
                    }
                }
            }
        })
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onDone?.invoke()
            return
        }

        mainHandler.post {
            if (!isInitialized) {
                NovaLogger.w("TtsEngine", "TTS not initialized yet. Enqueueing pending utterance.")
                pendingUtterance = trimmed to onDone
                return@post
            }

            val tts = textToSpeech
            if (tts == null) {
                _ttsState.value = TtsState.Error("TTS engine unavailable.")
                onDone?.invoke()
                return@post
            }

            val utteranceId = UUID.randomUUID().toString()
            if (onDone != null) {
                activeCallbacks[utteranceId] = onDone
            }

            _ttsState.value = TtsState.Speaking(trimmed)
            NovaLogger.i("TtsEngine", "Speaking: \"${NovaLogger.sanitize(trimmed)}\" (id: $utteranceId)")
            val result = tts.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                NovaLogger.e("TtsEngine", "tts.speak returned failure code: $result")
                _ttsState.value = TtsState.Error("Failed to synthesize speech.")
                activeCallbacks.remove(utteranceId)?.invoke()
            }
        }
    }

    override fun stop() {
        mainHandler.post {
            textToSpeech?.stop()
            activeCallbacks.clear()
            _ttsState.value = if (isInitialized) TtsState.Ready else TtsState.Uninitialized
            NovaLogger.d("TtsEngine", "TTS playback stopped.")
        }
    }

    override fun shutdown() {
        mainHandler.post {
            activeCallbacks.clear()
            pendingUtterance = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            _ttsState.value = TtsState.Uninitialized
            NovaLogger.d("TtsEngine", "TTS engine shut down.")
        }
    }
}
