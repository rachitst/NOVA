package com.nova.assistant.voice.wakeword

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.nova.assistant.core.config.NovaConfig
import com.nova.assistant.core.diagnostics.DevDiagnostics
import com.nova.assistant.core.logging.NovaLogger

/**
 * On-device neural keyword spotting engine for "Hey NOVA" backed by sherpa-onnx
 * (zipformer transducer KWS, English GigaSpeech model, int8 encoder).
 *
 * Only the exact keyword result "HEY_NOVA" triggers a wake event.
 * Competitor keywords ("OK_NOVA", "HI_NOVA") are declared in the keywords file so that
 * acoustically similar phrases are decoded as competitors instead of the wake phrase;
 * competitor results are silently ignored and NOVA remains in standby.
 *
 * Runs fully offline. Decode latency is well below real time on modern phone CPUs.
 */
class SherpaOnnxWakeWordEngine(
    private val context: Context? = null,
    override val wakeWordPhrase: String = "Hey NOVA",
    private val sensitivity: Float = 0.75f
) : WakeWordEngine {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val WAKE_KEYWORD = "HEY_NOVA"
        private const val KEYWORDS_ASSET = "hey_nova_keywords.txt"

        private val MODEL_FILES = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                    joiner = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                ),
                tokens = "tokens.txt",
                modelType = "zipformer2",
                numThreads = 1
            ),
            keywordsFile = KEYWORDS_ASSET,
            keywordsScore = 1.5f,
            keywordsThreshold = 0.35f,
            numTrailingBlanks = 2
        )
    }

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var isInitialized = false

    // DEV diagnostics: track decode activity
    private var decodeCount = 0
    private var lastDecodeDiagMs = 0L

    override fun initialize(): Boolean {
        return try {
            val assets = context?.assets
            spotter = if (assets != null) {
                KeywordSpotter(assetManager = assets, config = MODEL_FILES)
            } else {
                KeywordSpotter(config = MODEL_FILES)
            }
            stream = spotter?.createStream()
            isInitialized = spotter != null && stream != null
            NovaLogger.i("SherpaKwsWake", "sherpa-onnx KWS initialized for '$wakeWordPhrase' (initialized=$isInitialized, sensitivity=$sensitivity)")
            isInitialized
        } catch (e: Exception) {
            NovaLogger.e("SherpaKwsWake", "Failed to initialize sherpa-onnx KeywordSpotter", e)
            DevDiagnostics.show(context, "Wake engine init FAILED: ${e.message}", true)
            isInitialized = false
            false
        }
    }

    override fun processPcmChunk(pcmData: ShortArray, length: Int): WakeWordResult {
        val kws = spotter
        val s = stream
        if (!isInitialized || kws == null || s == null || length <= 0) return WakeWordResult.NONE

        val samples = FloatArray(length)
        for (i in 0 until length) {
            samples[i] = pcmData[i] / 32768.0f
        }

        try {
            s.acceptWaveform(samples, SAMPLE_RATE)
            while (kws.isReady(s)) {
                kws.decode(s)
                decodeCount++
                val result = kws.getResult(s)
                val keyword = result.keyword
                if (keyword.isNotBlank()) {
                    kws.reset(s)
                    return if (keyword == WAKE_KEYWORD) {
                        NovaLogger.i("SherpaKwsWake", "WAKE_CONFIRMED: '$wakeWordPhrase' ($keyword)")
                        DevDiagnostics.logEvent("WAKE", "CONFIRMED", "Neural KWS detected '$wakeWordPhrase'", true)
                        WakeWordResult.detected(confidence = 1.0f, keyword = wakeWordPhrase)
                    } else {
                        // Competitor keyword (e.g. "OK NOVA", "HI NOVA") — never a wake event.
                        if (NovaConfig.DEV) {
                            NovaLogger.d("SherpaKwsWake", "Competitor keyword ignored: $keyword")
                            DevDiagnostics.updateWakeStats(0.0f, 0.35f, "REJECTED: competitor '$keyword'", true)
                        }
                        WakeWordResult.NONE
                    }
                }
            }
        } catch (e: Exception) {
            NovaLogger.e("SherpaKwsWake", "Exception in KWS processing", e)
        }

        if (NovaConfig.DEV) {
            val now = System.currentTimeMillis()
            if (now - lastDecodeDiagMs > 5000L) {
                if (lastDecodeDiagMs != 0L) {
                    NovaLogger.d("SherpaKwsWake", "[DEV] KWS decode alive: $decodeCount decodes in last ${(now - lastDecodeDiagMs) / 1000.0}s, no keyword result")
                }
                decodeCount = 0
                lastDecodeDiagMs = now
            }
        }
        return WakeWordResult.NONE
    }

    override fun reset() {
        val kws = spotter
        val s = stream ?: return
        try {
            synchronized(this) {
                kws?.reset(s)
            }
        } catch (e: Exception) {
            NovaLogger.w("SherpaKwsWake", "Reset failed, recreating stream: ${e.message}")
            try {
                stream?.release()
            } catch (_: Exception) {}
            stream = try {
                kws?.createStream()
            } catch (e: Exception) {
                NovaLogger.e("SherpaKwsWake", "Stream recreation failed", e)
                null
            }
        }
    }

    override fun release() {
        isInitialized = false
        try {
            stream?.release()
        } catch (_: Exception) {}
        try {
            spotter?.release()
        } catch (_: Exception) {}
        stream = null
        spotter = null
        NovaLogger.d("SherpaKwsWake", "sherpa-onnx KWS released.")
    }
}
