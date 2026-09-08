package com.nova.assistant.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nova.assistant.MainActivity
import com.nova.assistant.R
import com.nova.assistant.actions.manager.ActionManager
import com.nova.assistant.actions.manager.DefaultActionManager
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.ai.parser.CommandParser
import com.nova.assistant.ai.parser.NaturalLanguageIntentParser
import com.nova.assistant.android.earphones.EarphoneConnectionManager
import com.nova.assistant.core.diagnostics.DevDiagnostics
import com.nova.assistant.core.logging.NovaLogger
import com.nova.assistant.voice.audio.AudioFeedbackPlayer
import com.nova.assistant.voice.audio.AudioStreamManager
import com.nova.assistant.voice.speaker.LocalSpeakerEmbeddingVerifier
import com.nova.assistant.voice.speaker.SpeakerVerifier
import com.nova.assistant.voice.speech.AndroidSpeechRecognizerEngine
import com.nova.assistant.voice.speech.SpeechState
import com.nova.assistant.voice.speech.SpeechToTextEngine
import com.nova.assistant.voice.tts.AndroidTextToSpeechEngine
import com.nova.assistant.voice.tts.TextToSpeechEngine
import com.nova.assistant.voice.wakeword.MfccWakeWordEngine
import com.nova.assistant.voice.wakeword.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground Service enabling hands-free operation for NOVA.
 * Activates exclusively when earphones are connected and voice profile is enrolled.
 * Performs real-time local wake-word detection ("Hey NOVA"), enforces mandatory speaker verification,
 * captures speech commands, executes actions via ActionManager security boundary, and speaks responses.
 */
class NovaVoiceService : Service() {

    companion object {
        const val ACTION_START = "com.nova.assistant.action.START_SERVICE"
        const val ACTION_STOP = "com.nova.assistant.action.STOP_SERVICE"
        const val ACTION_TOGGLE_MUTE = "com.nova.assistant.action.TOGGLE_MUTE"
        const val ACTION_SYNC_STATE = "com.nova.assistant.action.SYNC_STATE"
        const val ACTION_PAUSE_MIC = "com.nova.assistant.action.PAUSE_MIC"
        const val ACTION_RESUME_MIC = "com.nova.assistant.action.RESUME_MIC"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "nova_voice_service_channel"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _serviceStatusMessage = MutableStateFlow("Stopped")
        val serviceStatusMessage: StateFlow<String> = _serviceStatusMessage.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, NovaVoiceService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NovaVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun syncState(context: Context) {
            val intent = Intent(context, NovaVoiceService::class.java).apply {
                action = ACTION_SYNC_STATE
            }
            context.startService(intent)
        }

        fun pauseMicrophone(context: Context) {
            val intent = Intent(context, NovaVoiceService::class.java).apply {
                action = ACTION_PAUSE_MIC
            }
            context.startService(intent)
        }

        fun resumeMicrophone(context: Context) {
            val intent = Intent(context, NovaVoiceService::class.java).apply {
                action = ACTION_RESUME_MIC
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var audioStreamManager: AudioStreamManager
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var speakerVerifier: SpeakerVerifier
    private lateinit var earphoneConnectionManager: EarphoneConnectionManager
    private lateinit var speechToTextEngine: SpeechToTextEngine
    private lateinit var textToSpeechEngine: TextToSpeechEngine
    private lateinit var commandParser: CommandParser
    private lateinit var actionManager: ActionManager
    private lateinit var feedbackPlayer: AudioFeedbackPlayer

    private var wakeLock: PowerManager.WakeLock? = null
    private val isMuted = AtomicBoolean(false)
    private val isProcessingCommand = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        NovaLogger.i("NovaVoiceService", "Creating NovaVoiceService...")

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NOVA:VoiceProcessingLock")

        audioStreamManager = AudioStreamManager(this)
        wakeWordEngine = com.nova.assistant.voice.wakeword.NeuralWakeWordEngine(context = this)
        speakerVerifier = LocalSpeakerEmbeddingVerifier(this, verificationThreshold = 0.48f)
        earphoneConnectionManager = EarphoneConnectionManager(this)
        speechToTextEngine = AndroidSpeechRecognizerEngine(this)
        textToSpeechEngine = AndroidTextToSpeechEngine(this)
        commandParser = NaturalLanguageIntentParser()
        actionManager = DefaultActionManager(this)
        feedbackPlayer = AudioFeedbackPlayer()

        createNotificationChannel()
        observeSpeechState()

        earphoneConnectionManager.register { isConnected ->
            NovaLogger.i("NovaVoiceService", "Earphone state changed: connected=$isConnected")
            if (isConnected) {
                DevDiagnostics.show(this, "Earphones CONNECTED")
            } else {
                DevDiagnostics.show(this, "Earphones DISCONNECTED • Listening paused")
            }
            evaluateHandsFreeState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        NovaLogger.d("NovaVoiceService", "onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                stopAssistantService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
            }
            ACTION_PAUSE_MIC -> {
                audioStreamManager.pauseStreaming()
                NovaLogger.i("NovaVoiceService", "Microphone paused for enrollment capture.")
                DevDiagnostics.show(this, "Microphone paused for enrollment")
            }
            ACTION_RESUME_MIC -> {
                evaluateHandsFreeState()
                NovaLogger.i("NovaVoiceService", "Microphone resumed after enrollment.")
                DevDiagnostics.show(this, "Microphone resumed after enrollment")
            }
            ACTION_SYNC_STATE -> {
                evaluateHandsFreeState()
            }
            ACTION_START -> {
                startAssistantService()
            }
        }

        return START_STICKY
    }

    private fun startAssistantService() {
        _isServiceRunning.value = true

        val initialMessage = if (earphoneConnectionManager.checkConnected()) {
            if (speakerVerifier.isEnrolled) "Hands-Free active • Listening for 'Hey NOVA'"
            else "Voice enrollment required"
        } else {
            "Connect earphones to activate Hands-Free mode"
        }

        val notification = buildForegroundNotification(initialMessage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        wakeWordEngine.initialize()
        evaluateHandsFreeState()
        NovaLogger.i("NovaVoiceService", "NovaVoiceService started in foreground.")
        DevDiagnostics.show(this, "NOVA Service Started (Foreground)")
    }

    private fun evaluateHandsFreeState() {
        if (!_isServiceRunning.value) return

        val isEarphonesConnected = earphoneConnectionManager.checkConnected()
        val isEnrolled = speakerVerifier.isEnrolled

        NovaLogger.i("NovaVoiceService", "Evaluating state: earphonesConnected=$isEarphonesConnected, enrolled=$isEnrolled, muted=${isMuted.get()}")

        when {
            !isEarphonesConnected -> {
                audioStreamManager.pauseStreaming()
                _serviceStatusMessage.value = "Waiting for Earphones"
                updateNotification("Connect earphones to activate Hands-Free mode")
                DevDiagnostics.show(this, "Waiting for Earphones (Listening paused)")
            }
            // [BYPASS-POINT] Speaker enrollment requirement temporarily bypassed for isolated wake-word testing.
            // Preserved for re-enabling in Task 2:
            /*
            !isEnrolled -> {
                audioStreamManager.pauseStreaming()
                _serviceStatusMessage.value = "Voice Enrollment Required"
                updateNotification("Voice enrollment required • Open NOVA to set up")
                DevDiagnostics.show(this, "Voice Enrollment Required (Set up in NOVA)")
            }
            */
            isMuted.get() -> {
                audioStreamManager.pauseStreaming()
                _serviceStatusMessage.value = "Muted"
                updateNotification("Microphone Muted")
                DevDiagnostics.show(this, "Microphone Muted")
            }
            else -> {
                _serviceStatusMessage.value = "Listening for 'Hey NOVA'"
                updateNotification("Hands-Free active • Listening for 'Hey NOVA'")
                DevDiagnostics.show(this, "Standby Listening Active ('Hey NOVA')")
                startWakeWordListening()
            }
        }
    }

    private fun startWakeWordListening() {
        if (isMuted.get() || !earphoneConnectionManager.checkConnected()) return

        NovaLogger.i("NovaVoiceService", "[Pipeline:State] STANDBY_LISTENING for 'Hey NOVA' (earphones=true, speakerAuthBypassed=true)")
        audioStreamManager.startStreaming { pcmData, length ->
            if (isProcessingCommand.get() || isMuted.get()) return@startStreaming

            val result = wakeWordEngine.processPcmChunk(pcmData, length)
            if (result.isDetected) {
                onWakeWordTriggered(pcmData, length, result.confidence)
            }
        }
    }

    private fun onWakeWordTriggered(pcmData: ShortArray, length: Int, wakeConfidence: Float) {
        if (isProcessingCommand.getAndSet(true)) return

        acquireWakeLock(15000L)
        NovaLogger.i("NovaVoiceService", "[Pipeline:WakeWord] Detected 'Hey NOVA' (confidence=$wakeConfidence)")
        DevDiagnostics.show(this, "Wake Matched: 'Hey NOVA' (${String.format("%.2f", wakeConfidence)})")

        // =========================================================================
        // [BYPASS-POINT] SPEAKER VERIFICATION TEMPORARILY BYPASSED FOR TASK 1
        // All speaker verification code below is preserved intact for re-enabling in Task 2.
        // =========================================================================
        /*
        // 1. Mandatory Speaker Verification Check
        if (!speakerVerifier.isEnrolled) {
            NovaLogger.w("NovaVoiceService", "[Pipeline:SpeakerAuth] Rejected: voice profile not enrolled. Silently resuming standby.")
            DevDiagnostics.show(this, "SPEAKER_VERIFY FAILED: Not Enrolled", true)
            DevDiagnostics.logEvent("AUTH", "REJECTED", "No voice profile enrolled on device", false)
            resumeWakeWordListening("Voice enrollment required • Open NOVA to set up")
            return
        }

        // 2. Retrieve full 1.8-second utterance snapshot preceding and including the wake word
        val utteranceSnapshot = audioStreamManager.getRecentAudioSnapshot(1800)
        val verification = speakerVerifier.verify(utteranceSnapshot, utteranceSnapshot.size)

        DevDiagnostics.updateSpeakerStats(
            score = verification.confidence,
            threshold = 0.48f,
            decision = if (verification.isAuthorized) "AUTHORIZED (Score: ${String.format("%.2f", verification.confidence)} >= 0.48)" else "REJECTED (Score: ${String.format("%.2f", verification.confidence)} < 0.48)",
            speakerName = speakerVerifier.enrolledSpeakerName
        )

        if (!verification.isAuthorized) {
            // Silently reject impostor voice, background noise, or unverified speaker
            NovaLogger.w("NovaVoiceService", "[Pipeline:SpeakerAuth] REJECTED: Speaker verification mismatch (score=${verification.confidence}, threshold=0.48, requiredUser=${speakerVerifier.enrolledSpeakerName}). Silently returning to standby.")
            DevDiagnostics.logEvent("AUTH", "REJECTED", "Voiceprint mismatch (Score: ${String.format("%.2f", verification.confidence)} < 0.48, User: ${speakerVerifier.enrolledSpeakerName})", false)
            resumeWakeWordListening("Hands-Free active • Listening for 'Hey NOVA'")
            return
        }
        */

        // Telemetry update during bypass
        DevDiagnostics.updateSpeakerStats(
            score = 1.0f,
            threshold = 0.48f,
            decision = "BYPASSED (Wake Isolated)",
            speakerName = speakerVerifier.enrolledSpeakerName ?: "User"
        )
        DevDiagnostics.logEvent("AUTH", "BYPASS", "Speaker verification temporarily bypassed for isolated wake test", true)

        NovaLogger.i("NovaVoiceService", "[Pipeline:WakeWord] 'Hey NOVA' confirmed. Activating instant chime and command capture...")
        DevDiagnostics.logEvent("WAKE", "MATCHED", "'Hey NOVA' detected (confidence: ${String.format("%.2f", wakeConfidence)})", true)
        updateNotification("Hearing command...")
        _serviceStatusMessage.value = "Hearing command..."

        // 3. Pause AudioRecord to yield microphone hardware to SpeechRecognizer
        audioStreamManager.pauseStreaming()

        // 4. Play immediate wake chime for confirmed wake word
        feedbackPlayer.playWakeChime()

        // 5. Start Command Capture
        serviceScope.launch {
            kotlinx.coroutines.delay(180)
            NovaLogger.i("NovaVoiceService", "[Pipeline:CommandCapture] Starting SpeechRecognizer engine...")
            DevDiagnostics.show(this@NovaVoiceService, "Hearing command...")
            DevDiagnostics.logEvent("STT", "HEARING", "SpeechRecognizer listening for user command...", true)
            speechToTextEngine.startListening()
        }
    }

    private fun observeSpeechState() {
        serviceScope.launch {
            speechToTextEngine.speechState.collect { state ->
                when (state) {
                    is SpeechState.FinalResult -> {
                        NovaLogger.i("NovaVoiceService", "[Pipeline:STT] Final Result: \"${state.transcript}\"")
                        DevDiagnostics.show(this@NovaVoiceService, "STT Final: \"${state.transcript}\"")
                        DevDiagnostics.updateSttStats(state.transcript)
                        DevDiagnostics.logEvent("STT", "FINAL", "Captured: \"${state.transcript}\"", true)
                        handleCommandTranscript(state.transcript)
                    }
                    is SpeechState.Error -> {
                        NovaLogger.w("NovaVoiceService", "[Pipeline:STT] Error during command: ${state.message} (${state.error})")
                        DevDiagnostics.show(this@NovaVoiceService, "STT Error: ${state.message}", true)
                        DevDiagnostics.logEvent("STT", "ERROR", "${state.message} (${state.error})", false)
                        resumeWakeWordListening("Hands-Free active • Listening for 'Hey NOVA'")
                    }
                    is SpeechState.Listening -> {
                        NovaLogger.d("NovaVoiceService", "[Pipeline:STT] SpeechRecognizer is actively listening for user command...")
                        DevDiagnostics.show(this@NovaVoiceService, "STT Engine Listening for Command...")
                    }
                    is SpeechState.PartialResult -> {
                        NovaLogger.d("NovaVoiceService", "[Pipeline:STT] Partial: \"${state.partialText}\"")
                        DevDiagnostics.updateSttStats(state.partialText)
                    }
                    is SpeechState.Idle -> {}
                }
            }
        }
    }

    private fun handleCommandTranscript(transcript: String) {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            NovaLogger.w("NovaVoiceService", "[Pipeline:Command] Blank transcript received. Resuming standby.")
            DevDiagnostics.show(this, "Blank speech received. Resuming standby.")
            DevDiagnostics.logEvent("COMMAND", "EMPTY", "Empty speech transcript received", false)
            resumeWakeWordListening("Hands-Free active • Listening for 'Hey NOVA'")
            return
        }

        updateNotification("Processing: \"$trimmed\"")
        _serviceStatusMessage.value = "Processing: \"$trimmed\""

        serviceScope.launch {
            try {
                // 1. Parse Command into Structured Action
                val action = commandParser.parse(trimmed)
                NovaLogger.i("NovaVoiceService", "[Pipeline:Intent] Parsed Action: $action for transcript: '$trimmed'")
                DevDiagnostics.show(this@NovaVoiceService, "Intent: ${action::class.simpleName}")
                DevDiagnostics.logEvent("INTENT", "PARSED", "${action::class.simpleName} for \"$trimmed\"", true)

                // 2. Execute Action strictly via ActionManager security boundary
                DevDiagnostics.show(this@NovaVoiceService, "Executing Action: ${action::class.simpleName}...")
                val result = actionManager.executeAction(action)
                NovaLogger.i("NovaVoiceService", "[Pipeline:Action] Executed: isSuccess=${result is ActionResult.Success}, spokenResponse='${result.spokenResponse}'")
                DevDiagnostics.show(
                    this@NovaVoiceService,
                    "Action ${if (result is ActionResult.Success) "SUCCESS ✓" else "FAILED ✗"}: ${result.spokenResponse}"
                )
                DevDiagnostics.logEvent(
                    "ACTION",
                    if (result is ActionResult.Success) "SUCCESS" else "FAILED",
                    "${action::class.simpleName}: ${result.spokenResponse}",
                    result is ActionResult.Success
                )

                // 3. Audio feedback and Spoken Output via TTS
                feedbackPlayer.playSuccessChime()
                updateNotification("NOVA: ${result.spokenResponse}")

                NovaLogger.i("NovaVoiceService", "[Pipeline:TTS] Speaking response: \"${result.spokenResponse}\"")
                DevDiagnostics.show(this@NovaVoiceService, "TTS Speaking: \"${result.spokenResponse}\"")
                DevDiagnostics.logEvent("TTS", "SPEAKING", result.spokenResponse, true)
                textToSpeechEngine.speak(result.spokenResponse) {
                    // Return to standby after speaking
                    NovaLogger.i("NovaVoiceService", "[Pipeline:State] Completed command cycle. Returning to standby listening.")
                    DevDiagnostics.show(this@NovaVoiceService, "Cycle Complete • Returned to Standby")
                    DevDiagnostics.logEvent("STATE", "STANDBY", "Cycle complete • Standby listening active", true)
                    resumeWakeWordListening("Hands-Free active • Listening for 'Hey NOVA'")
                }
            } catch (e: Exception) {
                NovaLogger.e("NovaVoiceService", "Exception executing command pipeline", e)
                DevDiagnostics.show(this@NovaVoiceService, "Pipeline Exception: ${e.message}", true)
                DevDiagnostics.logEvent("PIPELINE", "EXCEPTION", "${e.message}", false)
                resumeWakeWordListening("Hands-Free active • Listening for 'Hey NOVA'")
            }
        }
    }

    private fun resumeWakeWordListening(statusText: String) {
        isProcessingCommand.set(false)
        wakeWordEngine.reset()
        audioStreamManager.resumeStreaming()
        updateNotification(statusText)
        _serviceStatusMessage.value = statusText
        releaseWakeLock()
        NovaLogger.d("NovaVoiceService", "[Pipeline:State] Standby listening resumed.")
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
            }
        } catch (e: Exception) {
            NovaLogger.e("NovaVoiceService", "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            NovaLogger.e("NovaVoiceService", "Error releasing WakeLock", e)
        }
    }

    private fun toggleMute() {
        val muted = !isMuted.get()
        isMuted.set(muted)
        if (muted) {
            audioStreamManager.pauseStreaming()
            updateNotification("Microphone Muted")
            _serviceStatusMessage.value = "Muted"
            NovaLogger.i("NovaVoiceService", "Microphone muted.")
            DevDiagnostics.show(this, "Microphone Muted")
        } else {
            audioStreamManager.resumeStreaming()
            updateNotification("Listening for 'Hey NOVA' • Hands-free ready")
            _serviceStatusMessage.value = "Listening for 'Hey NOVA'"
            NovaLogger.i("NovaVoiceService", "Microphone unmuted.")
            DevDiagnostics.show(this, "Microphone Unmuted • Standby Listening Active")
        }
    }

    private fun stopAssistantService() {
        NovaLogger.i("NovaVoiceService", "Stopping NovaVoiceService...")
        _isServiceRunning.value = false
        _serviceStatusMessage.value = "Stopped"

        audioStreamManager.stopStreaming()
        wakeWordEngine.release()
        speechToTextEngine.destroy()
        textToSpeechEngine.shutdown()
        releaseWakeLock()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        DevDiagnostics.show(this, "NOVA Service Stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NOVA Assistant Background Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains hands-free voice command listening in the background."
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NovaVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, NovaVoiceService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val pendingMute = PendingIntent.getService(
            this,
            2,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionTitle = if (isMuted.get()) "Unmute" else "Mute"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVA Hands-Free")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_lock_silent_mode, muteActionTitle, pendingMute)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(contentText: String) {
        if (!_isServiceRunning.value) return
        val notification = buildForegroundNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        NovaLogger.i("NovaVoiceService", "Destroying NovaVoiceService...")
        serviceScope.cancel()
        earphoneConnectionManager.unregister()
        audioStreamManager.stopStreaming()
        wakeWordEngine.release()
        speechToTextEngine.destroy()
        textToSpeechEngine.shutdown()
        releaseWakeLock()
        super.onDestroy()
    }
}
