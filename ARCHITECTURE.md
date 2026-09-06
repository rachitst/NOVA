# NOVA — Architecture

## 1. Architecture Philosophy

NOVA will use a modular architecture.

The system should keep:

* Voice processing
* AI
* Android capabilities
* Security
* UI
* Data

separated from each other.

The AI must never have unrestricted access to Android.

---

# 2. High-Level Architecture

```text
                         USER
                           |
                           v
                    +-------------+
                    | Audio Input |
                    +------+------+
                           |
                           v
                    +-------------+
                    | Wake Word   |
                    +------+------+
                           |
                           v
                    +-------------+
                    |   Speaker   |
                    | Verification|
                    +------+------+
                           |
                           v
                    +-------------+
                    | Speech to   |
                    |    Text     |
                    +------+------+
                           |
                           v
                    +-------------+
                    | Intent / AI |
                    +------+------+
                           |
                           v
                    +-------------+
                    |   Action    |
                    |   Manager   |
                    +------+------+
                           |
              +------------+------------+
              |            |            |
              v            v            v
          Android      Accessibility  Notifications
            APIs          Service        Service
              |            |            |
              +------------+------------+
                           |
                           v
                    +-------------+
                    |   Result    |
                    +------+------+
                           |
                           v
                    +-------------+
                    |    TTS      |
                    +-------------+
```

---

# 3. Application Layers

## UI Layer

Responsible for:

* Setup
* Permissions
* Settings
* Status
* Logs
* Debug information
* Manual microphone control

The UI must not contain business logic.

---

# 4. Core Layer

Responsible for:

* Application state
* Configuration
* Permissions
* Security
* Logging
* Common utilities

---

# 5. Voice Layer

Components:

```text
voice/
├── wakeword/
├── speech/
├── speaker/
└── tts/
```

Responsibilities:

### Wake Word

Detect:

"Hey NOVA"

### Speech

Convert audio to text.

Initial candidate:

whisper.cpp.

### Speaker

Verify whether the speaker is authorized.

### TTS

Convert NOVA responses into speech.

---

# 6. AI Layer

Components:

```text
ai/
├── model/
├── inference/
├── prompts/
├── intent/
└── context/
```

Responsibilities:

* Natural-language understanding
* Intent extraction
* Entity extraction
* Context
* Structured output

The AI must produce structured commands.

---

# 7. Action Layer

Components:

```text
actions/
├── apps/
├── calls/
├── messages/
├── alarms/
├── calendar/
├── device/
└── web/
```

Each action should have:

* Input schema
* Validation
* Permission requirements
* Risk level
* Execution method
* Result
* Error handling

---

# 8. Android Integration Layer

```text
android/
├── accessibility/
├── notifications/
├── bluetooth/
└── services/
```

These components connect NOVA with Android-specific capabilities.

---

# 9. Action Manager

The Action Manager is the security boundary.

Example:

```text
AI:

{
  "action": "CALL_CONTACT",
  "contact": "Mom"
}

        |
        v

ActionManager
        |
        +-- Validate action
        +-- Resolve contact
        +-- Check permission
        +-- Check risk
        +-- Request confirmation if needed
        |
        v

Android API
```

The Action Manager must reject:

* Unknown actions
* Malformed parameters
* Unauthorized operations
* Missing permissions
* Unsafe actions

---

# 10. Structured Action Model

Actions should use strongly typed Kotlin models.

Conceptual example:

```kotlin
sealed interface NovaAction

data class OpenApp(
    val packageName: String
) : NovaAction

data class CallContact(
    val contactId: String
) : NovaAction

data class SetAlarm(
    val hour: Int,
    val minute: Int
) : NovaAction

data class WebSearch(
    val query: String
) : NovaAction
```

The exact implementation may change.

---

# 11. Repository Structure

Recommended:

```text
NOVA/
│
├── README.md
├── PROJECT_SPEC.md
├── ARCHITECTURE.md
├── SECURITY.md
├── ROADMAP.md
├── DEVELOPMENT.md
│
├── app/
│
├── core/
│
├── voice/
│
├── ai/
│
├── actions/
│
├── android/
│
├── data/
│
└── ui/
```

The initial Android project may remain a single Gradle module.

Additional modules should only be introduced when they provide a clear architectural benefit.

---

# 12. Data Flow

Example:

```text
Audio
 ↓
WakeWordDetector
 ↓
SpeakerVerifier
 ↓
SpeechRecognizer
 ↓
CommandProcessor
 ↓
LLM / IntentParser
 ↓
ActionParser
 ↓
ActionManager
 ↓
Android Capability
 ↓
ActionResult
 ↓
ResponseGenerator
 ↓
TextToSpeech
```

---

# 13. Error Flow

Every layer must return useful errors.

Example:

```text
SpeechRecognitionError
PermissionDenied
ContactNotFound
MultipleContactsFound
UnsupportedAction
AccessibilityUnavailable
ApplicationNotFound
ActionCancelled
ActionFailed
```

Errors should not crash NOVA.

---

# 14. Offline / Online Boundary

Local:

* Wake word
* Speech recognition
* Speaker verification
* Local LLM
* Basic actions
* TTS

Online:

* Web search
* Internet-dependent services
* Optional cloud AI

The architecture should keep the boundary explicit.

---

# 15. Background Architecture

NOVA must respect Android's current background execution and foreground-service rules.

Microphone access should only occur through supported Android mechanisms and after appropriate permission/user authorization.

The architecture must never depend on secretly starting a microphone service from an arbitrary background state.

---

# 16. Bluetooth Architecture

```text
BluetoothManager
       |
       v
TrustedDeviceRegistry
       |
       v
DeviceConnected
       |
       v
NovaAvailabilityController
```

NOVA should only treat registered devices as trusted NOVA devices.

---

# 17. Notification Architecture

```text
NotificationListenerService
          |
          v
NotificationRepository
          |
          v
NotificationProcessor
          |
          v
User Request
          |
          v
NotificationResponse
```

Notification data should be minimized and deleted according to user settings.

---

# 18. Accessibility Architecture

Accessibility should be isolated.

```text
AccessibilityController
        |
        +-- UI inspection
        +-- Element lookup
        +-- Click
        +-- Text input
        +-- Scroll
        +-- Gesture
```

The rest of NOVA should not directly manipulate AccessibilityNodeInfo objects.

---

# 19. Extensibility

New actions should be addable without modifying the core AI system.

Example:

```text
Add Spotify support

→ New SpotifyAction
→ Register action
→ Define permissions
→ Define validation
→ Implement execution
```

The LLM should not require a complete rewrite.

---

# 20. Architecture Rule

> **AI decides what the user wants. Android code decides whether and how it can be done.**
