# NOVA — Security & Privacy

## 1. Purpose

NOVA has access to potentially sensitive capabilities:

* Microphone
* Notifications
* Contacts
* Phone
* Accessibility
* Bluetooth
* Calendar
* Device state

Security and privacy are therefore core architectural requirements.

---

# 2. Fundamental Rule

> User permission gives NOVA access to a capability; it does not give NOVA unrestricted control over Android.

NOVA must respect Android's permission and security model.

---

# 3. AI Security Boundary

The AI must never directly execute arbitrary code.

Bad:

```text
LLM → execute arbitrary shell command
```

Correct:

```text
LLM
 ↓
Structured Action
 ↓
Validation
 ↓
ActionManager
 ↓
Approved Android API
```

---

# 4. Action Allowlist

Every action NOVA can execute must be registered.

Example:

```text
OPEN_APP
CALL_CONTACT
SET_ALARM
SET_TIMER
WEB_SEARCH
READ_NOTIFICATIONS
SEND_MESSAGE
```

Unknown actions must be rejected.

---

# 5. Permission Model

Permissions should be requested only when needed.

NOVA should not request all permissions during first launch.

Example:

```text
User enables notifications
        ↓
Explain why
        ↓
User grants access
        ↓
NOVA activates notification features
```

---

# 6. Voice Authentication

NOVA may use speaker verification to determine whether the user is authorized.

Voice verification should be treated as an additional security layer rather than an absolute authentication mechanism.

High-risk actions should require stronger authentication.

---

# 7. High-Risk Actions

Examples:

* Financial operations
* Security changes
* Deleting important data
* Account changes
* Sending sensitive information

These require explicit confirmation.

Potentially:

```text
Voice
 ↓
Confirmation
 ↓
Android biometric/device authentication
 ↓
Action
```

---

# 8. Confirmation Model

Examples:

Low risk:

"Open Chrome."

→ Execute directly.

Medium risk:

"Send this message to Rahul."

→ Ask for confirmation if configured.

High risk:

"Transfer money."

→ Require explicit confirmation and stronger authentication.

---

# 9. Microphone Privacy

NOVA must never secretly record the user.

The application must:

* Respect microphone permission
* Respect Android microphone indicators
* Stop recording when appropriate
* Avoid unnecessary recording
* Avoid uploading audio without explicit consent

Wake-word detection should preferably occur locally.

---

# 10. Notification Privacy

Notifications can contain:

* Private messages
* OTPs
* Financial information
* Emails
* Personal conversations

NOVA must minimize storage.

The notification store should support:

* Encryption where appropriate
* Automatic deletion
* Application filtering
* User-controlled retention
* Complete deletion

---

# 11. Logs

Logs must never contain sensitive information by default.

Do not log:

* Full notification contents
* Passwords
* OTPs
* Authentication tokens
* Full message contents
* Voice recordings
* Contact data unnecessarily

Debug logs should use redaction.

---

# 12. Secrets

Never commit:

* API keys
* Tokens
* Passwords
* Private certificates
* Signing keys

Use local configuration mechanisms and environment variables where necessary.

---

# 13. Local AI

Local AI is preferred for sensitive commands.

The application should not send private information to cloud AI unless the user has explicitly enabled cloud processing.

---

# 14. Third-Party Applications

NOVA must not attempt to:

* Bypass encryption
* Bypass authentication
* Circumvent security controls
* Extract protected application data
* Circumvent application security

Accessibility should only be used for legitimate user-authorized interaction.

---

# 15. Accessibility

Accessibility is powerful and must be treated as a privileged capability.

NOVA should:

* Clearly explain its use
* Only perform user-requested operations
* Avoid hidden actions
* Avoid credential harvesting
* Avoid extracting unnecessary content
* Avoid arbitrary automation

---

# 16. Data Minimization

NOVA should store the minimum information necessary.

If information does not need to be stored, it should not be stored.

---

# 17. User Controls

NOVA should eventually provide:

* Enable/disable microphone
* Enable/disable wake word
* Enable/disable speaker verification
* Enable/disable notifications
* Enable/disable accessibility features
* Clear stored data
* Disable cloud AI
* Offline-only mode
* View granted capabilities

---

# 18. Safe Failure

If NOVA is uncertain, it should ask.

Example:

"I found three contacts named Rahul. Which one?"

It must not guess.

---

# 19. Security Principle

> **When NOVA is uncertain, it asks. When NOVA is unauthorized, it refuses. When NOVA is capable, it validates before acting.**
