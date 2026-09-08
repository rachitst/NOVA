package com.nova.assistant.actions

import com.nova.assistant.actions.executors.AppLauncher
import com.nova.assistant.actions.executors.CallExecutor
import com.nova.assistant.actions.executors.ClockExecutor
import com.nova.assistant.actions.executors.DeviceControlExecutor
import com.nova.assistant.actions.executors.MessageExecutor
import com.nova.assistant.actions.executors.NotificationExecutor
import com.nova.assistant.actions.manager.DefaultActionManager
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.actions.model.DeviceFeature
import com.nova.assistant.actions.model.MessagePlatform
import com.nova.assistant.actions.model.NovaAction
import com.nova.assistant.android.security.DeviceLockManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionManagerTest {

    private lateinit var fakeAppLauncher: FakeAppLauncher
    private lateinit var fakeCallExecutor: FakeCallExecutor
    private lateinit var fakeMessageExecutor: FakeMessageExecutor
    private lateinit var fakeClockExecutor: FakeClockExecutor
    private lateinit var fakeDeviceControlExecutor: FakeDeviceControlExecutor
    private lateinit var fakeNotificationExecutor: FakeNotificationExecutor
    private lateinit var actionManager: DefaultActionManager

    class FakeAppLauncher : AppLauncher {
        val installedApps = mutableSetOf<String>()
        var lastLaunchedTarget: AppTarget? = null

        override fun isAppInstalled(target: AppTarget): Boolean {
            return target.displayName in installedApps
        }

        override fun launchApp(target: AppTarget): ActionResult {
            return if (isAppInstalled(target)) {
                lastLaunchedTarget = target
                ActionResult.Success(
                    spokenResponse = "Opening ${target.displayName}.",
                    displayFeedback = "Launched ${target.displayName}"
                )
            } else {
                ActionResult.Failure(
                    spokenResponse = "${target.displayName} is not installed.",
                    displayFeedback = "App not found",
                    errorType = ActionErrorType.APP_NOT_FOUND
                )
            }
        }
    }

    class FakeCallExecutor : CallExecutor {
        var lastRecipient: String? = null
        override fun callContact(recipient: String): ActionResult {
            lastRecipient = recipient
            return ActionResult.Success(
                spokenResponse = "Calling $recipient.",
                displayFeedback = "Calling $recipient"
            )
        }
    }

    class FakeMessageExecutor : MessageExecutor {
        var lastRecipient: String? = null
        var lastMessage: String? = null
        var lastPlatform: MessagePlatform? = null

        override fun sendMessage(recipient: String, messageText: String?, platform: MessagePlatform): ActionResult {
            lastRecipient = recipient
            lastMessage = messageText
            lastPlatform = platform
            return ActionResult.Success(
                spokenResponse = "Sending message to $recipient.",
                displayFeedback = "Message: $recipient"
            )
        }
    }

    class FakeClockExecutor : ClockExecutor {
        var lastAlarmHour: Int? = null
        var lastAlarmMinute: Int? = null
        var lastTimerSeconds: Int? = null

        override fun setAlarm(hour: Int, minute: Int, label: String?, rawTimeText: String): ActionResult {
            lastAlarmHour = hour
            lastAlarmMinute = minute
            return ActionResult.Success(
                spokenResponse = "Alarm set for $hour:$minute.",
                displayFeedback = "Alarm: $hour:$minute"
            )
        }

        override fun setTimer(durationSeconds: Int, label: String?): ActionResult {
            lastTimerSeconds = durationSeconds
            return ActionResult.Success(
                spokenResponse = "Timer set for $durationSeconds seconds.",
                displayFeedback = "Timer: ${durationSeconds}s"
            )
        }
    }

    class FakeDeviceControlExecutor : DeviceControlExecutor {
        var lastFeature: DeviceFeature? = null
        var lastState: Boolean? = null

        override fun setFeatureState(feature: DeviceFeature, state: Boolean): ActionResult {
            lastFeature = feature
            lastState = state
            return ActionResult.Success(
                spokenResponse = "${feature.name} set to $state.",
                displayFeedback = "DeviceControl"
            )
        }
    }

    class FakeNotificationExecutor : NotificationExecutor {
        override fun readNotifications(appFilter: String?): ActionResult {
            return ActionResult.Success(
                spokenResponse = "No unread notifications.",
                displayFeedback = "No notifications"
            )
        }
    }

    @Before
    fun setUp() {
        fakeAppLauncher = FakeAppLauncher()
        fakeCallExecutor = FakeCallExecutor()
        fakeMessageExecutor = FakeMessageExecutor()
        fakeClockExecutor = FakeClockExecutor()
        fakeDeviceControlExecutor = FakeDeviceControlExecutor()
        fakeNotificationExecutor = FakeNotificationExecutor()

        val fakeContext = FakeContext()
        actionManager = DefaultActionManager(
            context = fakeContext,
            appLauncher = fakeAppLauncher,
            callExecutor = fakeCallExecutor,
            messageExecutor = fakeMessageExecutor,
            clockExecutor = fakeClockExecutor,
            deviceControlExecutor = fakeDeviceControlExecutor,
            notificationExecutor = fakeNotificationExecutor,
            deviceLockManager = DeviceLockManager(fakeContext)
        )
    }

    @Test
    fun executeAction_getTime_returnsSuccessWithFormattedTime() = runBlocking {
        val result = actionManager.executeAction(NovaAction.GetTime)
        assertTrue("Expected Success for GetTime but was $result", result is ActionResult.Success)
        assertTrue("Spoken response should start with 'It is'", result.spokenResponse.startsWith("It is"))
    }

    @Test
    fun executeAction_openApp_installedApp_returnsSuccess() = runBlocking {
        fakeAppLauncher.installedApps.add("Google Chrome")
        val action = NovaAction.OpenApp(AppTarget.KnownApp.CHROME)
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals("Opening Google Chrome.", result.spokenResponse)
        assertEquals(AppTarget.KnownApp.CHROME, fakeAppLauncher.lastLaunchedTarget)
    }

    @Test
    fun executeAction_callContact_dispatchesToCallExecutor() = runBlocking {
        val action = NovaAction.CallContact("Mom")
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals("Mom", fakeCallExecutor.lastRecipient)
    }

    @Test
    fun executeAction_sendMessage_dispatchesToMessageExecutor() = runBlocking {
        val action = NovaAction.SendMessage("Alex", "See you soon", MessagePlatform.WHATSAPP)
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals("Alex", fakeMessageExecutor.lastRecipient)
        assertEquals("See you soon", fakeMessageExecutor.lastMessage)
        assertEquals(MessagePlatform.WHATSAPP, fakeMessageExecutor.lastPlatform)
    }

    @Test
    fun executeAction_setAlarm_validTime_dispatchesToClockExecutor() = runBlocking {
        val action = NovaAction.SetAlarm(rawTime = "7:30 AM", hour = 7, minute = 30)
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals(7, fakeClockExecutor.lastAlarmHour)
        assertEquals(30, fakeClockExecutor.lastAlarmMinute)
    }

    @Test
    fun executeAction_setTimer_validDuration_dispatchesToClockExecutor() = runBlocking {
        val action = NovaAction.SetTimer(durationSeconds = 600)
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals(600, fakeClockExecutor.lastTimerSeconds)
    }

    @Test
    fun executeAction_deviceControl_dispatchesToDeviceControlExecutor() = runBlocking {
        val action = NovaAction.DeviceControl(DeviceFeature.FLASHLIGHT, true)
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Success)
        assertEquals(DeviceFeature.FLASHLIGHT, fakeDeviceControlExecutor.lastFeature)
        assertEquals(true, fakeDeviceControlExecutor.lastState)
    }

    @Test
    fun executeAction_help_returnsConciseCapabilities() = runBlocking {
        val result = actionManager.executeAction(NovaAction.Help)
        assertTrue(result is ActionResult.Success)
        assertTrue(result.spokenResponse.contains("apps") || result.spokenResponse.contains("time"))
    }

    @Test
    fun executeAction_unknownAction_returnsConciseError() = runBlocking {
        val action = NovaAction.Unknown("play jazz music")
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Failure)
        val failure = result as ActionResult.Failure
        assertEquals(ActionErrorType.UNSUPPORTED_ACTION, failure.errorType)
        assertEquals("I didn't understand.", failure.spokenResponse)
    }

    @Test
    fun executeAction_webSearch_emptyQuery_returnsInvalidParameterFailure() = runBlocking {
        val action = NovaAction.WebSearch(query = "   ")
        val result = actionManager.executeAction(action)

        assertTrue(result is ActionResult.Failure)
        val failure = result as ActionResult.Failure
        assertEquals(ActionErrorType.INVALID_PARAMETER, failure.errorType)
    }

    @Test
    fun deviceLockManager_safeActionsAllowedWhileLocked() {
        val lockManager = DeviceLockManager(FakeContext())
        assertTrue(lockManager.isActionAllowedWhileLocked(NovaAction.GetTime))
        assertTrue(lockManager.isActionAllowedWhileLocked(NovaAction.SetAlarm("7:00 AM", 7, 0)))
        assertTrue(lockManager.isActionAllowedWhileLocked(NovaAction.SetTimer(60)))
        assertTrue(lockManager.isActionAllowedWhileLocked(NovaAction.DeviceControl(DeviceFeature.FLASHLIGHT, true)))
        assertTrue(lockManager.isActionAllowedWhileLocked(NovaAction.Help))
    }

    @Test
    fun deviceLockManager_sensitiveActionsBlockedWhileLocked() {
        val lockManager = DeviceLockManager(FakeContext())
        assertFalse(lockManager.isActionAllowedWhileLocked(NovaAction.CallContact("Mom")))
        assertFalse(lockManager.isActionAllowedWhileLocked(NovaAction.SendMessage("Mom", "hello", MessagePlatform.SMS)))
        assertFalse(lockManager.isActionAllowedWhileLocked(NovaAction.ReadNotifications()))
        assertFalse(lockManager.isActionAllowedWhileLocked(NovaAction.OpenApp(AppTarget.KnownApp.CHROME)))
        assertFalse(lockManager.isActionAllowedWhileLocked(NovaAction.WebSearch("Kotlin")))
    }

    @Test
    fun deviceLockManager_conciseExplanationsProvided() {
        val lockManager = DeviceLockManager(FakeContext())
        assertEquals("Please unlock your device to call Mom.", lockManager.getLockedExplanation(NovaAction.CallContact("Mom")))
        assertEquals("Please unlock your device to send messages.", lockManager.getLockedExplanation(NovaAction.SendMessage("Mom", "hi", MessagePlatform.SMS)))
        assertEquals("Please unlock your device to read notifications.", lockManager.getLockedExplanation(NovaAction.ReadNotifications()))
        assertEquals("Please unlock your device to open apps.", lockManager.getLockedExplanation(NovaAction.OpenApp(AppTarget.KnownApp.CHROME)))
    }

    private class FakeContext : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = this
        override fun getSystemService(name: String): Any? = null
    }
}
