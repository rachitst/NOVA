# NOVA

### Personal Offline-First Voice Assistant for Android

> **NOVA** is a personal, voice-controlled Android assistant designed to let the user operate their phone naturally through spoken commands.

NOVA is intended to be a **privacy-first, offline-first, highly customizable personal assistant** capable of understanding the user's voice, identifying the authorized user, interacting with Android system features, reading notifications, launching applications, and — where Android permits — interacting with third-party applications.

The long-term goal is to create a personal Android agent that feels less like a traditional voice assistant and more like a **hands-free control layer for the user's phone**.

---

# 1. Vision

NOVA should allow the user to interact with their Android phone without manually touching it.

For example:

> **User:** "Hey NOVA, call Mom."

NOVA should identify the user, understand the command, find the contact, and initiate the call.

> **User:** "Hey NOVA, search Google for the best snow trekking shoes."

NOVA should open the appropriate application/browser and perform the search.

> **User:** "Hey NOVA, what notifications do I have?"

NOVA should inspect notifications available through Android's notification access system and read relevant notifications aloud.

> **User:** "Set an alarm for 7 AM tomorrow."

NOVA should create the alarm.

> **User:** "Message Rahul that I'll be 20 minutes late."

NOVA should attempt to perform the requested messaging action using the safest Android mechanism available.

The objective is **not** to bypass Android security.

Instead, NOVA should combine Android's official APIs, local machine-learning models, accessibility capabilities where appropriate, and application-specific integrations to provide the maximum amount of useful automation that Android legitimately allows.

---

# 2. Core Principles

NOVA will follow these principles:

### Privacy First

Whenever practical, voice processing and command understanding should happen locally on the device.

The user's microphone recordings, notifications, contacts, and commands should not be sent to external servers unless the user explicitly enables a cloud-based feature.

### Offline First

NOVA should remain functional without an internet connection for core commands.

Internet access should only be required for tasks that inherently require it, such as:

* Google searches
* Web browsing
* Online services
* Cloud AI, if enabled
* Internet-dependent applications

### User First

NOVA should only execute commands from the authorized user.

Voice authentication should be implemented as an additional security layer.

### Explicit Permissions

NOVA should clearly explain why it needs sensitive permissions.

No permission should be requested simply because it is technically available.

### Safe Execution

The AI should **never directly control Android arbitrarily**.

Instead:

```text
Voice
  ↓
Speech Recognition
  ↓
Command Understanding
  ↓
Structured Action
  ↓
Action Validation
  ↓
Android Capability
  ↓
Execution
```

This makes NOVA safer, easier to debug, and easier to extend.

---

# 3. Target Platform

## Primary Platform

**Android**

The initial implementation will target modern Android versions, with Android 16 compatibility as a major development target.

Android's current foreground-service system requires appropriate service declarations and permissions, particularly for microphone use. Android 14+ introduced additional requirements for foreground-service types, and current Android versions impose restrictions on starting microphone foreground services from the background.

Therefore, NOVA's architecture must be designed around these restrictions rather than assuming that an application can silently start microphone access whenever it wants.

---

# 4. High-Level Architecture

```text
                         ┌─────────────────────┐
                         │       USER          │
                         │       🎙️            │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Audio Capture     │
                         │     Android         │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Wake Word Engine  │
                         │   "Hey NOVA"        │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │  Speaker/User       │
                         │  Verification       │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ Speech Recognition  │
                         │    whisper.cpp      │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ Command / Intent    │
                         │ Understanding       │
                         │ Local LLM           │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Action Manager    │
                         │ Validation + Safety  │
                         └──────────┬──────────┘
                                    │
               ┌────────────────────┼────────────────────┐
               │                    │                    │
               ▼                    ▼                    ▼
        Android APIs        Accessibility         Notifications
               │               Service                  │
               │                    │                    │
               └────────────────────┼────────────────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Android Device   │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │    Text-to-Speech   │
                         │   NOVA's Response   │
                         └─────────────────────┘
```

---

# 5. Technology Stack

## Android Application

**Language:** Kotlin

**IDE:** Android Studio

**Build system:** Gradle

**UI:** Jetpack Compose

**Minimum Android version:** To be determined during implementation based on the APIs we require.

**Target:** Current Android API level.

---

# 6. Speech Recognition

## Primary Technology: whisper.cpp

NOVA will use **whisper.cpp** for local speech-to-text.

whisper.cpp is a C/C++ implementation of OpenAI's Whisper model and supports running Whisper locally. The project includes Android support/examples and continues to receive releases.

Advantages:

* Open source
* Local inference
* No API cost
* No mandatory internet connection
* Multilingual
* Good support for accents/noisy environments
* Can be integrated into Android using native code/NDK

Possible models will be tested for:

* Speed
* RAM usage
* Accuracy
* Battery usage
* English performance
* Indian accents
* Mixed-language speech

NOVA should start with a relatively small model and allow larger models to be added later.

---

# 7. Wake Word Detection

NOVA should not continuously send microphone audio to a remote server.

Instead, a lightweight local wake-word detector should continuously monitor for the activation phrase.

Example:

> "Hey NOVA"

## Primary candidate: openWakeWord

`openWakeWord` is an open-source wake-word detection framework with pre-trained models and support for training custom wake words.

Android implementations are also available, including Kotlin/ONNX-based projects.

The desired flow:

```text
Microphone
    ↓
Local wake-word detector
    ↓
"Hey NOVA" detected
    ↓
Begin command capture
```

The wake-word system should consume as little CPU and battery as possible.

---

# 8. Speaker Verification

Wake-word detection alone is not enough.

NOVA should attempt to determine whether the person speaking is the authorized user.

Example:

```text
Person A:
"Hey NOVA, send ₹10,000 to Rahul."

NOVA:
Unauthorized voice → ignore / request authentication
```

Whereas:

```text
Authorized user:
"Hey NOVA, send ₹10,000 to Rahul."

NOVA:
Authorized → continue
```

Speaker verification will be implemented separately from speech recognition.

Possible approaches will be evaluated during development, including lightweight on-device speaker-embedding models.

### Important

Voice authentication should **not** be considered sufficient security for extremely sensitive actions.

For high-risk operations, NOVA should be able to require:

* Device unlock
* Biometric authentication
* Confirmation
* PIN/passcode
* Explicit on-screen approval

---

# 9. Local AI / Command Understanding

NOVA needs an AI model capable of converting natural language into structured commands.

Example:

User:

> "Call Mom."

Model output:

```json
{
  "action": "CALL_CONTACT",
  "contact": "Mom"
}
```

User:

> "Set an alarm tomorrow at 7 in the morning."

Model output:

```json
{
  "action": "SET_ALARM",
  "time": "07:00",
  "date": "tomorrow"
}
```

User:

> "Open Chrome and search for trekking shoes."

Model output:

```json
{
  "action": "WEB_SEARCH",
  "query": "trekking shoes"
}
```

## Local inference

The initial implementation should investigate **llama.cpp** for running quantized local LLMs on-device.

llama.cpp is an open-source C/C++ inference framework designed for local LLM execution and has Android integrations/projects demonstrating on-device inference with GGUF models.

Candidate models can include appropriately licensed small/efficient models such as:

* Qwen-family models
* Gemma-family models
* Llama-family models
* Other small instruction-following GGUF models

The exact model will be selected after benchmarking on the target phone.

### Important distinction

The LLM should **not** directly perform actions.

It should generate a structured intent.

```text
Natural language
       ↓
      LLM
       ↓
Structured command
       ↓
Validation
       ↓
Action Manager
       ↓
Android
```

---

# 10. Action Manager

The Action Manager is one of NOVA's most important components.

It acts as a security boundary between the AI and Android.

Example:

```text
LLM:

{
  "action": "CALL_CONTACT",
  "contact": "Mom"
}

        ↓

Action Manager

1. Is CALL_CONTACT allowed?
2. Is "Mom" resolved?
3. Is a phone number available?
4. Does this action require confirmation?
5. Execute appropriate Android API.
```

The LLM will never receive unrestricted access to the device.

---

# 11. Android System Actions

NOVA should implement direct Android APIs whenever possible.

Potential commands:

### Applications

```text
Open YouTube
Open Chrome
Open WhatsApp
Open Settings
Open Maps
```

### Phone

```text
Call Mom
Call Rahul
Open recent calls
```

### Alarms

```text
Set an alarm for 7 AM
Set a timer for 20 minutes
Cancel my 7 AM alarm
```

### Reminders / Calendar

```text
Remind me at 5 PM
Create a calendar event
What's on my calendar today?
```

### Device Controls

Where Android permits:

```text
Turn Bluetooth on/off
Turn Wi-Fi on/off
Change volume
Open Bluetooth settings
Open Wi-Fi settings
Open display settings
```

Some system controls are restricted on modern Android and may require opening the appropriate Settings screen rather than directly changing the setting.

NOVA must detect these restrictions and fall back gracefully.

---

# 12. Notification Access

NOVA will use Android's:

`NotificationListenerService`

This API allows an application with user-granted notification access to receive callbacks when notifications are posted or removed.

This will enable functionality such as:

> "What notifications do I have?"

> "Read my WhatsApp notifications."

> "Did I get any messages?"

> "What did Rahul send me?"

Architecture:

```text
Android Notification
        ↓
NotificationListenerService
        ↓
NOVA Notification Store
        ↓
Command
        ↓
Filter relevant notifications
        ↓
LLM / formatter
        ↓
Text-to-Speech
```

NOVA should store the minimum notification data necessary and provide a setting to disable notification storage entirely.

---

# 13. Accessibility Service

NOVA may use Android's `AccessibilityService` for interaction with supported applications.

Android accessibility services can receive UI events, inspect active-window content when configured, and interact with apps on the user's behalf. Android's documentation specifically describes voice-control systems as an example of accessibility services.

Potential uses:

```text
Open WhatsApp
      ↓
Find chat
      ↓
Open chat
      ↓
Enter text
      ↓
Press Send
```

or:

```text
Open Instagram
      ↓
Find Profile
      ↓
Click Profile
```

### Important limitation

NOVA must **not assume that AccessibilityService provides unrestricted control of every application**.

Applications can have different UI structures, permissions, security restrictions, dynamic interfaces, and anti-automation behavior.

Additionally, Google Play has specific requirements around sensitive APIs and accessibility services. Accessibility should therefore be used only where it is legitimately appropriate for NOVA's stated functionality.

The initial development environment may therefore use APK sideloading for experimentation, while Play Store compatibility will be treated as a separate requirement.

---

# 14. WhatsApp Integration

WhatsApp is a special case.

NOVA should use the following priority order:

### Level 1 — Official Android intents/deep links

Use them whenever the requested operation is supported.

### Level 2 — Notification access

Use notification content for reading incoming messages where available.

### Level 3 — Accessibility

Where appropriate and permitted, NOVA may interact with the visible WhatsApp UI.

### Level 4 — Official APIs

If a legitimate WhatsApp/Meta API is appropriate for a particular use case, it may be integrated separately.

NOVA will **not attempt to bypass WhatsApp's security, encryption, authentication, or application protections.**

---

# 15. Earphone / Bluetooth Integration

One of NOVA's defining features is automatic activation when the user's earphones connect.

Desired flow:

```text
Bluetooth device connected
        ↓
Is device registered as NOVA headset?
        ↓
YES
        ↓
NOVA becomes available
```

The application should maintain a list of trusted Bluetooth audio devices.

Example:

```text
My Galaxy Buds
My Nothing Ear
My Bluetooth headset
```

NOVA should not automatically activate for every Bluetooth device.

---

# 16. Automatic Startup

The original goal is:

> "When I connect my earphones, NOVA automatically starts."

This needs to be implemented within Android's current background execution restrictions.

Modern Android restricts background foreground-service startup, and microphone foreground services have additional while-in-use restrictions.

Therefore, NOVA should use an architecture based on:

```text
User enables NOVA
        ↓
Required permissions granted
        ↓
NOVA service / supported Android mechanism
        ↓
Bluetooth/headset state
        ↓
NOVA becomes ready
```

The exact startup mechanism will be tested against the Android version and device manufacturer.

NOVA must never secretly record audio.

Android's microphone privacy indicators and permissions must remain respected.

---

# 17. Text-to-Speech

NOVA needs a natural voice.

Initial implementation can use:

### Option A — Android Text-to-Speech

Simplest approach.

Advantages:

* Easy integration
* No large model
* No extra processing requirements

### Option B — Local neural TTS

Open-source local TTS options such as Piper-based/Sherpa-based implementations can be evaluated.

Piper is a local neural TTS project, and Android implementations based on its voice models are available.

NOVA should initially prioritize reliability and low latency over having an extremely realistic voice.

---

# 18. Conversation Context

NOVA should eventually support short-term conversational context.

Example:

```text
User:
"Search Google for trekking shoes."

NOVA:
"Here are the results."

User:
"Open the first one."

NOVA:
Opens the first result.
```

Another example:

```text
User:
"Message Rahul."

NOVA:
"What should I say?"

User:
"Tell him I'll reach at 8."

NOVA:
Sends the message.
```

This requires a conversation/session manager.

---

# 19. Command Categories

NOVA will eventually support commands in several categories.

### General

* What time is it?
* What's today's date?
* Open an app
* Close/leave an app
* Search the web

### Communication

* Call a contact
* Send SMS
* Read notifications
* Reply to notifications where supported
* WhatsApp interaction where possible

### Productivity

* Set alarm
* Set timer
* Create reminder
* Create calendar event
* Read calendar
* Create notes

### Device

* Bluetooth
* Wi-Fi/settings
* Volume
* Brightness where permitted
* Flashlight where permitted
* Open system settings

### Applications

* Open applications
* Navigate applications
* Search inside supported applications
* Enter text
* Click UI elements
* Scroll
* Perform gestures where supported

### Information

* Read notifications
* Summarize notifications
* Search the web
* Answer general questions

---

# 20. Security Model

NOVA will use multiple security layers.

```text
                 Voice
                   ↓
              Wake Word
                   ↓
          Speaker Verification
                   ↓
           Intent Validation
                   ↓
          Permission Checking
                   ↓
         Action Risk Assessment
                   ↓
              Execution
```

## Risk levels

### LOW

Examples:

```text
Open Chrome
What time is it?
Open YouTube
Search Google
```

No confirmation normally required.

### MEDIUM

Examples:

```text
Send a message
Delete a reminder
Change important settings
```

May require confirmation depending on configuration.

### HIGH

Examples:

```text
Financial transactions
Deleting important data
Changing security settings
Account/security actions
```

Require explicit confirmation and potentially device authentication.

---

# 21. Privacy Architecture

NOVA should follow a local-first architecture.

```text
                 ┌─────────────────┐
                 │     NOVA        │
                 │    Android      │
                 └────────┬────────┘
                          │
              ┌───────────┴───────────┐
              │                       │
          LOCAL DATA             OPTIONAL CLOUD
              │                       │
       Speech recognition        Complex queries
       Wake word                Web services
       LLM                     Optional AI
       Notifications
       Voice verification
```

Cloud functionality should be **opt-in**.

The user should be able to run NOVA in a strict offline mode.

---

# 22. No Mandatory Paid APIs

The core NOVA system should not require paid APIs.

Preferred stack:

```text
Android/Kotlin          → Free
Android Studio          → Free
whisper.cpp             → Open source
llama.cpp               → Open source
openWakeWord            → Open source
Local GGUF models       → Depends on model license
Android TTS             → Built-in
Piper/Sherpa options    → Open source options
Room/SQLite             → Free
Git                     → Free
```

The licensing of **individual AI models and voice models must always be checked separately** before redistribution or commercial use.

"Open-source framework" does not automatically mean that every model distributed through it has identical licensing.

---

# 23. Internet Usage

NOVA should classify commands into two categories.

### Offline commands

```text
Call Mom
Set alarm
Set timer
Read notifications
Open application
Change supported device settings
Basic questions
```

These should work without internet wherever possible.

### Online commands

```text
Search Google
Open a website
Check live weather
Search online
Online messaging services
Cloud AI
```

These naturally require internet connectivity.

---

# 24. Battery Efficiency

Always-listening systems can consume significant battery.

Therefore NOVA should use a layered approach:

```text
Low-power wake-word detection
             ↓
Only after wake word:
             ↓
Activate speech recognition
             ↓
Only when necessary:
             ↓
Run local LLM
```

The large language model should never continuously run in the background just to wait for commands.

Performance will be measured using:

* CPU usage
* RAM
* battery drain
* wake-word latency
* transcription latency
* LLM latency
* TTS latency

---

# 25. Modular Architecture

The project should be divided into modules/components.

Suggested architecture:

```text
nova/
│
├── app/
│
├── core/
│   ├── audio/
│   ├── permissions/
│   ├── security/
│   ├── logging/
│   └── settings/
│
├── voice/
│   ├── wakeword/
│   ├── speech/
│   ├── speaker/
│   └── tts/
│
├── ai/
│   ├── model/
│   ├── inference/
│   ├── prompts/
│   ├── intent/
│   └── context/
│
├── actions/
│   ├── apps/
│   ├── calls/
│   ├── messages/
│   ├── alarms/
│   ├── calendar/
│   ├── device/
│   └── web/
│
├── android/
│   ├── accessibility/
│   ├── notifications/
│   ├── bluetooth/
│   └── services/
│
├── data/
│   ├── database/
│   ├── models/
│   └── repositories/
│
└── ui/
    ├── setup/
    ├── settings/
    ├── permissions/
    └── dashboard/
```

---

# 26. Initial Development Roadmap

## Phase 0 — Project Setup

* Create Android project
* Kotlin
* Gradle
* Git repository
* Basic UI
* Permission architecture
* Logging
* Build/debug configuration

---

## Phase 1 — Basic Voice Assistant

Goal:

> User opens NOVA and speaks a command.

Implement:

* Microphone permission
* Audio capture
* Speech recognition
* Basic command parser
* TTS response

Example:

```text
User:
"What time is it?"

NOVA:
"It is 11:45 PM."
```

---

## Phase 2 — Wake Word

Implement:

* openWakeWord
* Custom "NOVA" wake word
* Low-power audio monitoring
* Wake-word confidence threshold
* False-positive testing

Goal:

```text
"Hey NOVA"
      ↓
NOVA activates
```

---

## Phase 3 — Android Actions

Implement:

* Open apps
* Phone calls
* Contacts
* Alarms
* Timers
* Web searches
* Basic settings

---

## Phase 4 — Local LLM

Integrate:

* llama.cpp
* GGUF model
* Intent extraction
* Structured action format
* Action validation

Goal:

NOVA understands natural language rather than requiring fixed commands.

---

## Phase 5 — Notifications

Implement:

* NotificationListenerService
* Notification database/cache
* Notification filtering
* Read notifications
* Summaries
* Application-specific handling

---

## Phase 6 — Speaker Verification

Implement:

* Voice enrollment
* Speaker embeddings
* Local verification
* Confidence threshold
* Unauthorized-user handling

---

## Phase 7 — Accessibility

Implement carefully:

* UI inspection
* Text entry
* Click actions
* Scrolling
* Gestures
* App-specific adapters

The system should prefer semantic UI elements rather than screen coordinates whenever possible.

---

## Phase 8 — Messaging

Implement:

* SMS
* Notification replies where supported
* WhatsApp experiments
* Other messaging applications

Do not bypass application security.

---

## Phase 9 — Bluetooth / Earbuds

Implement:

* Trusted device registration
* Bluetooth connection detection
* NOVA availability state
* Audio routing
* Earbud-specific behavior

---

## Phase 10 — Optimization

Measure:

* Battery
* CPU
* RAM
* Latency
* Accuracy
* False wake-ups
* Voice authentication accuracy

Then optimize the local models.

---

# 27. Example User Experience

### Starting NOVA

```text
User connects earbuds.

NOVA:
Ready.
```

### Basic command

```text
User:
"Hey NOVA, open Chrome."

NOVA:
"Opening Chrome."
```

### Search

```text
User:
"Hey NOVA, search Google for easy snow treks in Uttarakhand."

NOVA:
"Searching Google."
```

### Call

```text
User:
"Hey NOVA, call Mom."

NOVA:
"Calling Mom."
```

### Alarm

```text
User:
"Hey NOVA, wake me up at 7 tomorrow."

NOVA:
"Alarm set for 7 AM."
```

### Notifications

```text
User:
"Hey NOVA, what did I miss?"

NOVA:
"You have three notifications.
Two WhatsApp messages and one Gmail notification."
```

### Conversation

```text
User:
"Hey NOVA, message Rahul."

NOVA:
"What should I say?"

User:
"Tell him I'll reach in 20 minutes."

NOVA:
"Okay. Send it?"

User:
"Yes."

NOVA:
"Message sent."
```

---

# 28. Failure Handling

NOVA must fail safely.

Examples:

### Unknown contact

```text
User:
"Call John."

NOVA:
"I found three contacts named John. Which one?"
```

### Insufficient permission

```text
NOVA:
"I need notification access to read your notifications."
```

### Unsupported operation

```text
NOVA:
"I can't directly change that setting on this Android version, but I can open the relevant Settings page."
```

### Uncertain command

```text
User:
"Send it to him."

NOVA:
"Who do you mean?"
```

NOVA should never guess when an action could cause harm or unintended consequences.

---

# 29. Testing Strategy

Testing will happen at multiple levels.

### Unit tests

Test:

* Intent parsing
* Date/time parsing
* Contact resolution
* Action validation
* Permission handling

### Integration tests

Test:

* Speech → intent
* Intent → action
* Notification → NOVA
* Bluetooth → NOVA state
* Accessibility → application interaction

### Real-device testing

At least one physical Android device will be required for:

* Microphone
* Bluetooth
* Accessibility
* Notifications
* Battery
* Background behavior
* Audio routing

Emulators cannot accurately reproduce every real-world voice/Bluetooth/background behavior.

---

# 30. Security and Privacy Requirements

NOVA must:

* Never secretly record the user.
* Never silently upload microphone recordings.
* Never transmit notification contents without user consent.
* Never expose stored notification data unnecessarily.
* Never execute arbitrary code generated by an LLM.
* Validate every AI-generated action.
* Require confirmation for risky operations.
* Respect Android permission systems.
* Provide a way to disable NOVA.
* Provide a way to delete local NOVA data.
* Clearly indicate when microphone access is active.
* Keep cloud AI optional.

---

# 31. Current Android Limitations

NOVA is intentionally designed around Android's current security model.

Modern Android restricts:

* Background foreground-service startup
* Background microphone access
* Certain system settings
* Some application interactions
* Sensitive permissions
* Certain foreground-service types

For Android 14+, foreground services must declare appropriate types and permissions. Microphone foreground services specifically require microphone permissions and are subject to while-in-use/background-start restrictions.

Therefore:

> **NOVA will not promise unrestricted control of Android.**

Instead, it will implement the maximum functionality available through supported Android mechanisms.

---

# 32. Google Play Considerations

NOVA will initially be developed and tested as a personal Android application.

If NOVA is eventually distributed through Google Play, additional policy requirements will need to be satisfied.

Google Play currently requires sensitive permissions/APIs to be necessary for the app's core functionality and appropriately disclosed.

AccessibilityService usage is particularly important because Android documents accessibility services as specialized assistive tools rather than a generic automation mechanism.

Therefore the project will maintain two goals:

```text
Development goal:
Maximum legitimate functionality

Distribution goal:
Full compliance with Android/Google Play policies
```

---

# 33. Project Philosophy

NOVA is **not intended to be a copy of Google Assistant, Siri, or Alexa.**

The goal is different.

NOVA should be:

* Personal
* Local-first
* Privacy-focused
* Customizable
* Extensible
* Developer-controlled
* Voice-first
* Android-native
* Modular
* Open-source wherever possible

The user should ultimately be able to decide what NOVA can and cannot do.

---

# 34. Long-Term Vision

The eventual NOVA experience should look like:

```text
                    ┌───────────────┐
                    │     NOVA      │
                    └───────┬───────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
       Voice             Context          Device
          │                 │                 │
      Listening        Conversation       Android
          │                 │                 │
          └─────────────────┼─────────────────┘
                            │
                       AI Agent
                            │
             ┌──────────────┼──────────────┐
             │              │              │
          Search          Apps          Actions
             │              │              │
          Internet      Accessibility   Android APIs
```

Eventually NOVA should be able to understand multi-step instructions such as:

> "I'm going to sleep. Set an alarm for 7, turn my volume down, turn on Do Not Disturb, and remind me tomorrow at 10 to call Rahul."

NOVA should break that into individual validated actions:

```text
SET_ALARM
     ↓
SET_VOLUME
     ↓
ENABLE_DND
     ↓
CREATE_REMINDER
```

and execute them safely.

---

# 35. Definition of Success

NOVA will be considered successful when the user can:

1. Connect their trusted earbuds.
2. Activate NOVA naturally using the wake word.
3. Speak normally without rigid commands.
4. Have NOVA understand the request.
5. Verify the authorized speaker when required.
6. Convert the request into a structured intent.
7. Validate the intent.
8. Execute the action using the appropriate Android capability.
9. Receive a spoken response.
10. Perform the entire interaction hands-free.

The ultimate objective:

> **"If I can normally do it on my Android phone, I want to be able to ask NOVA to do it — within the permissions and security boundaries Android provides."**

---

# 36. Initial Technology Decisions

| Component             | Initial Choice                         |
| --------------------- | -------------------------------------- |
| Platform              | Android                                |
| Language              | Kotlin                                 |
| UI                    | Jetpack Compose                        |
| Build                 | Gradle                                 |
| Speech-to-text        | whisper.cpp                            |
| Wake word             | openWakeWord                           |
| Local LLM             | llama.cpp + suitable GGUF model        |
| TTS                   | Android TTS initially                  |
| Neural TTS            | Piper/Sherpa option later              |
| Notifications         | NotificationListenerService            |
| Cross-app interaction | AccessibilityService where appropriate |
| Bluetooth             | Android Bluetooth APIs                 |
| Calls                 | Android Telecom/intent mechanisms      |
| Alarms                | Android Alarm APIs                     |
| Storage               | Room/SQLite                            |
| Voice verification    | Local speaker-verification model       |
| Cloud AI              | Optional                               |
| Required paid API     | None for core architecture             |

These choices are based on the current state of the projects and Android platform documentation checked in September 2026.

---

# 37. Current Status

**Project:** NOVA

**Status:** 🟡 Planning / Architecture

### Planned first milestone

> **NOVA v0.1 — Local Voice Command Prototype**

Features:

* Android application
* Microphone permission
* Voice capture
* Local speech-to-text
* Basic command parser
* Text-to-speech response
* First Android action
* Basic UI/logging

First target command:

```text
"Hey NOVA, open Chrome."
```

Once this works reliably, the architecture will be expanded rather than rewriting the project later.

---

# 38. License

The NOVA project's final license has not yet been selected.

Third-party dependencies and AI models may have their own licenses.

Before distributing NOVA, every dependency and model must be reviewed individually for:

* Commercial use
* Redistribution
* Modification
* Attribution requirements
* Model/voice licensing
* Google Play compatibility

---

# 39. Project Motto

> **NOVA — Your phone. Your voice. Your control.**
