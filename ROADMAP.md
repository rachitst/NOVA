# NOVA — Development Roadmap

## Status

🟡 Project initialization

---

# Phase 0 — Foundation

### Goal

Create a clean, documented Android project.

### Tasks

* [x] Create Android project
* [ ] Verify Gradle build
* [ ] Verify app launches
* [ ] Initialize Git
* [ ] Create GitHub repository
* [ ] Add project documentation
* [ ] Configure development rules
* [ ] Connect physical Android device
* [ ] Verify ADB
* [ ] Install debug APK on device

---

# Phase 1 — Voice Prototype

### Goal

Build the first working voice interaction.

### Features

* [ ] Microphone permission
* [ ] Manual microphone activation
* [ ] Audio capture
* [ ] Speech-to-text
* [ ] Basic command parser
* [ ] Text-to-speech
* [ ] Basic logs

### First command

```text
"Open Chrome."
```

### Success condition

User speaks a command and NOVA successfully performs it on the physical Android phone.

---

# Phase 2 — Wake Word

### Goal

Allow hands-free activation.

### Features

* [ ] Wake-word engine
* [ ] "Hey NOVA"
* [ ] Local detection
* [ ] False-positive testing
* [ ] Battery testing
* [ ] Microphone lifecycle management

---

# Phase 3 — Local AI

### Goal

Replace rigid command matching with natural-language understanding.

### Features

* [ ] llama.cpp integration
* [ ] Select small local model
* [ ] GGUF model support
* [ ] Intent extraction
* [ ] Structured actions
* [ ] Action validation
* [ ] Context management

---

# Phase 4 — Android Actions

### Goal

Give NOVA useful phone capabilities.

### Features

* [ ] Open applications
* [ ] Calls
* [ ] Contacts
* [ ] Alarms
* [ ] Timers
* [ ] Web search
* [ ] Calendar
* [ ] Supported device controls

---

# Phase 5 — Notification Assistant

### Goal

Allow NOVA to understand notifications.

### Features

* [ ] NotificationListenerService
* [ ] Notification repository
* [ ] App filtering
* [ ] Read notifications
* [ ] Notification summaries
* [ ] Notification retention controls

---

# Phase 6 — Speaker Verification

### Goal

Identify the authorized user.

### Features

* [ ] Voice enrollment
* [ ] Speaker embeddings
* [ ] Local verification
* [ ] Threshold tuning
* [ ] False acceptance testing
* [ ] False rejection testing

---

# Phase 7 — Bluetooth / Earbuds

### Goal

Connect NOVA's availability to trusted earbuds.

### Features

* [ ] Bluetooth device detection
* [ ] Trusted device registration
* [ ] Connection events
* [ ] Audio routing
* [ ] NOVA availability state

---

# Phase 8 — Accessibility

### Goal

Interact with supported third-party applications.

### Features

* [ ] Accessibility service
* [ ] UI inspection
* [ ] Semantic element detection
* [ ] Click
* [ ] Text input
* [ ] Scroll
* [ ] Gestures
* [ ] Application adapters

---

# Phase 9 — Messaging

### Goal

Support messaging where Android/application capabilities permit.

### Features

* [ ] SMS
* [ ] Notification replies
* [ ] WhatsApp experimentation
* [ ] Messaging confirmation
* [ ] Error handling

---

# Phase 10 — Performance

### Goal

Make NOVA practical for daily use.

Measure:

* [ ] RAM
* [ ] CPU
* [ ] Battery
* [ ] Wake-word latency
* [ ] Speech latency
* [ ] LLM latency
* [ ] TTS latency

---

# Phase 11 — Security Hardening

### Features

* [ ] Action allowlist
* [ ] Risk levels
* [ ] Confirmation system
* [ ] Secure storage
* [ ] Log redaction
* [ ] Data deletion
* [ ] Offline mode
* [ ] Cloud AI opt-out

---

# Phase 12 — NOVA v1.0

NOVA v1.0 should provide:

* Voice activation
* Speaker verification
* Local speech recognition
* Local AI
* Android actions
* Notifications
* Bluetooth integration
* Safe accessibility automation
* Natural conversations
* TTS responses
* Privacy controls

---

# Future Ideas

Potential future features:

* Personalized routines
* Location-aware commands
* Smart-home integration
* Wearable integration
* Car/Bluetooth mode
* Custom skills
* Plugin system
* Multiple users
* On-device memory
* Advanced planning
* Local vision
* Camera-based commands

These are not part of the initial implementation.
