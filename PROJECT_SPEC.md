# NOVA — Project Specification

## 1. Project Overview

NOVA is a privacy-first, offline-first personal voice assistant for Android.

The goal is to allow an authorized user to control their Android phone using natural spoken language.

NOVA should be able to understand commands such as:

* "Open Chrome."
* "Search Google for snow trekking shoes."
* "Call Mom."
* "Set an alarm for 7 AM."
* "Read my notifications."
* "Message Rahul that I'll be 20 minutes late."
* "Open WhatsApp."
* "Turn the volume down."
* "What do I have on my calendar today?"

The long-term objective is:

> If the user can reasonably perform an action on their Android phone, NOVA should attempt to let them perform that action through voice, subject to Android's permissions, APIs, security model, and restrictions.

NOVA must never attempt to bypass Android security mechanisms.

---

# 2. Core Goals

## 2.1 Voice-first interaction

The primary interface should eventually be the user's voice.

The user should not need to open the NOVA application for normal interaction once Android permits the required background behavior.

---

## 2.2 Offline-first operation

Core functionality should work locally whenever technically practical.

The following should eventually be capable of working without internet:

* Wake-word detection
* Speech recognition
* Speaker verification
* Command understanding
* Basic commands
* Calls
* Alarms
* Timers
* Notification reading
* Device operations supported by Android

Internet-dependent operations naturally require connectivity.

---

## 2.3 Privacy

NOVA should not send microphone recordings, notifications, contacts, or other sensitive data to external servers unless the user explicitly enables a feature requiring it.

Cloud AI must be optional.

---

## 2.4 Personalization

NOVA should learn configuration rather than secretly learning sensitive personal information.

Examples:

* Trusted Bluetooth devices
* User's preferred name
* Contact aliases
* Preferred response style
* Preferred voice
* Enabled capabilities

---

# 3. Primary User

The initial version is designed for a single primary user.

NOVA should recognize an authorized voice where practical.

The application should not assume that every person speaking to the phone is authorized to perform sensitive operations.

---

# 4. Command Pipeline

Every command should follow this conceptual pipeline:

User speech

↓

Wake-word detection

↓

Speaker verification when required

↓

Speech-to-text

↓

Intent / command understanding

↓

Structured action

↓

Action validation

↓

Permission check

↓

Risk assessment

↓

Android execution

↓

Result

↓

Text-to-speech response

---

# 5. Supported Command Categories

## 5.1 Applications

Examples:

* Open Chrome
* Open YouTube
* Open WhatsApp
* Open Settings
* Open Maps

NOVA should prefer Android intents and official APIs.

---

## 5.2 Communication

Potential functionality:

* Call contact
* Open contact
* Send SMS
* Read supported notification replies
* Interact with messaging applications where technically and legally appropriate

Third-party application security must never be bypassed.

---

## 5.3 Alarms and Timers

Examples:

* Set alarm
* Cancel alarm
* Set timer
* Cancel timer

Use Android's supported APIs.

---

## 5.4 Calendar and Reminders

Potential functionality:

* Create calendar event
* Read calendar
* Create reminder
* Read upcoming events

Permissions must only be requested when required.

---

## 5.5 Notifications

NOVA should eventually support:

* Read notifications
* Summarize notifications
* Filter notifications by application
* Read relevant notifications aloud
* Respond where Android/application support permits

Notification access must be explicitly granted by the user.

---

## 5.6 Device Controls

Where Android allows:

* Volume
* Bluetooth
* Wi-Fi/settings
* Flashlight
* Brightness
* Do Not Disturb
* Relevant Settings pages

If Android does not permit direct control, NOVA should open the appropriate Settings page instead.

---

## 5.7 Web

Examples:

* Search Google
* Open website
* Search YouTube
* Search Maps
* Open a URL

Web-dependent operations require internet access.

---

# 6. Third-Party Application Interaction

NOVA may eventually use AccessibilityService for supported cross-application interaction.

Potential example:

User:

"Open WhatsApp and message Rahul that I'll be late."

Possible flow:

1. Resolve Rahul.
2. Open WhatsApp.
3. Locate the appropriate conversation.
4. Enter the message.
5. Ask for confirmation if required.
6. Send.

This must not be implemented by bypassing application security.

Accessibility behavior should be treated as application-specific and may fail if an application's UI changes.

---

# 7. Voice Requirements

NOVA should eventually support:

### Wake word

Example:

"Hey NOVA"

### Speech recognition

Local speech-to-text.

Initial candidate:

whisper.cpp.

The upstream whisper.cpp project continues to maintain Android examples and native Android integration.

### Speaker verification

Local voice identity verification.

### Text-to-speech

Initially Android's built-in TTS.

A local neural TTS engine may be added later.

---

# 8. AI Requirements

The AI should understand natural language but should not directly control Android.

Example:

User:

"Wake me up tomorrow at 7."

AI output:

```json
{
  "action": "SET_ALARM",
  "time": "07:00",
  "date": "tomorrow"
}
```

The Action Manager then validates and executes it.

---

# 9. AI Model Strategy

NOVA should support local inference.

The initial local LLM runtime candidate is llama.cpp.

The llama.cpp project currently provides Android-specific build/integration documentation and Android examples.

The actual model should be selected through benchmarking rather than hardcoded into the architecture.

The model must:

* Run on the target device
* Have acceptable latency
* Have acceptable RAM usage
* Understand structured commands
* Have an appropriate license

---

# 10. Android Compatibility

Initial development target:

* Compile SDK: Android 16 / API 36
* Target SDK: Android 16 / API 36
* Minimum SDK: API 26
* Primary test device: Android 12 / API 31

The application must account for behavior differences between Android versions.

Android 16 is currently a stable Android release.

---

# 11. Permission Philosophy

NOVA should request permissions progressively.

Do not request every permission during first launch.

Example:

Initial installation:

* No sensitive permission

When microphone functionality is enabled:

* Request microphone

When notification functionality is enabled:

* Explain and request notification access

When accessibility functionality is enabled:

* Explain and direct the user to Accessibility Settings

Permissions must always have an identifiable purpose.

---

# 12. User Experience

NOVA should clearly communicate:

* What it is doing
* Why a permission is needed
* Whether a command succeeded
* Whether an action failed
* When confirmation is required

NOVA should not silently perform potentially destructive operations.

---

# 13. High-Risk Actions

Examples:

* Financial transactions
* Deleting significant data
* Changing security settings
* Account changes
* Sending sensitive information

These should require explicit confirmation and potentially device authentication.

---

# 14. MVP

The first MVP should NOT include every feature.

MVP:

1. Android app
2. Microphone permission
3. Manual voice activation
4. Local speech-to-text
5. Basic command parser
6. Text-to-speech
7. Open applications
8. Basic logging
9. Basic settings

First successful command:

"Open Chrome."

---

# 15. Definition of Done

A feature is complete only when:

* It works on a real Android device.
* Required permissions are correctly handled.
* Android version restrictions are considered.
* Errors are handled.
* Logs are useful.
* Tests exist where practical.
* No unrelated code was modified.
* Documentation is updated.
* The application builds successfully.
