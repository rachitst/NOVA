package com.nova.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nova.assistant.core.logging.NovaLogger
import com.nova.assistant.ui.dashboard.AssistantDashboardScreen
import com.nova.assistant.ui.dashboard.AssistantViewModel
import com.nova.assistant.ui.theme.NOVATheme
import com.nova.assistant.voice.service.NovaVoiceService

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AssistantViewModel

    private var hasRequestedPermissionAtLeastOnce = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRequestedPermissionAtLeastOnce = true
        val permanentlyDenied = !isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        NovaLogger.i("MainActivity", "RECORD_AUDIO result: isGranted=$isGranted, permanentlyDenied=$permanentlyDenied")
        viewModel.onPermissionStatusChanged(isGranted, permanentlyDenied)
        if (isGranted && !NovaVoiceService.isServiceRunning.value) {
            NovaLogger.i("MainActivity", "Auto-starting NovaVoiceService (hands-free mode ON by default)")
            NovaVoiceService.startService(this)
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        NovaLogger.i("MainActivity", "POST_NOTIFICATIONS result: isGranted=$isGranted")
    }

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        NovaLogger.i("MainActivity", "BLUETOOTH_CONNECT result: isGranted=$isGranted")
    }

    private val deviceAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            NovaLogger.i("MainActivity", "Device authentication passed. Opening voice enrollment.")
            viewModel.openEnrollmentDialog()
        } else {
            NovaLogger.w("MainActivity", "Device authentication cancelled or failed.")
        }
    }

    private fun requestDeviceAuthForEnrollment() {
        val keyguardManager = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguardManager != null && keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Secure Voice Enrollment",
                "Confirm your device PIN, pattern, or password to setup your voice profile."
            )
            if (intent != null) {
                deviceAuthLauncher.launch(intent)
                return
            }
        }
        // Device has no lock set; proceed directly
        viewModel.openEnrollmentDialog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[AssistantViewModel::class.java]

        updatePermissionStatus()

        // Auto-request notifications if needed for foreground service
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            NOVATheme {
                val uiState by viewModel.uiState.collectAsState()

                AssistantDashboardScreen(
                    uiState = uiState,
                    onRequestPermissionClick = {
                        hasRequestedPermissionAtLeastOnce = true
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onOpenSettingsClick = {
                        openAppSettings()
                    },
                    onToggleBackgroundService = {
                        viewModel.toggleBackgroundService(
                            context = this@MainActivity,
                            onNeedNotificationPermission = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                                        != PackageManager.PERMISSION_GRANTED) {
                                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    },
                    onOpenVoiceEnrollment = {
                        requestDeviceAuthForEnrollment()
                    },
                    onStartVoiceEnrollment = { name ->
                        viewModel.startVoiceEnrollment(name)
                    },
                    onRecordEnrollmentSample = {
                        viewModel.recordEnrollmentSample()
                    },
                    onCancelVoiceEnrollment = {
                        viewModel.cancelVoiceEnrollment()
                    },
                    onDismissVoiceEnrollment = {
                        viewModel.dismissEnrollmentDialog()
                    },
                    onClearVoiceEnrollment = {
                        viewModel.clearVoiceEnrollment()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val permanentlyDenied = if (hasRequestedPermissionAtLeastOnce) {
            !isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        } else {
            false
        }
        viewModel.onPermissionStatusChanged(isGranted, permanentlyDenied)

        if (isGranted && !NovaVoiceService.isServiceRunning.value) {
            NovaLogger.i("MainActivity", "Auto-starting NovaVoiceService on startup/resume (hands-free ON by default)")
            NovaVoiceService.startService(this)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            NovaLogger.e("MainActivity", "Failed to open application settings", e)
        }
    }
}