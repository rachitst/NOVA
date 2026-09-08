# NOVA — AI Development Rules

This document defines how AI coding agents must work on NOVA.

The project is developed with AI assistance, but the human developer remains responsible for architecture, security, testing, and final approval.

---

# 1. General Rule

Do not implement large changes without first understanding the existing architecture.

Before modifying code:

1. Inspect the repository.
2. Read relevant documentation.
3. Identify affected components.
4. Explain the proposed approach.
5. Implement the smallest appropriate change.
6. Build and test.
7. Report results.

---

# 2. Never Rewrite the Project Without Approval

Do not:

* Replace the architecture
* Replace Kotlin
* Replace Compose
* Replace Gradle configuration
* Replace major libraries
* Reorganize the entire repository

unless explicitly requested.

---

# 3. Dependency Rules

Before adding a dependency:

1. Explain why it is needed.
2. Check whether Android provides an official alternative.
3. Check whether the dependency is maintained.
4. Check its license.
5. Check compatibility with the project's min/target SDK.
6. Prefer open-source dependencies when practical.

Do not add dependencies simply because they are convenient.

---

# 4. Android API Rules

Prefer official Android APIs when available.

Examples:

* Android Intents
* AlarmManager
* Contacts Provider
* NotificationListenerService
* Bluetooth APIs
* AccessibilityService
* Android TTS

Do not implement unofficial workarounds when an official API exists.

---

# 5. Permission Rules

Never add a permission without a feature requiring it.

Every new permission must have:

* Purpose
* User explanation
* Runtime handling if applicable
* Failure behavior

---

# 6. AI Rules

The LLM must never directly execute arbitrary code.

Correct:

```text
User
 ↓
Speech
 ↓
LLM
 ↓
Structured Action
 ↓
ActionManager
 ↓
Validation
 ↓
Android API
```

Incorrect:

```text
User
 ↓
LLM
 ↓
Shell command
```

---

# 7. Security Rules

Never:

* Hardcode secrets
* Commit API keys
* Store passwords in plaintext
* Log sensitive notification contents
* Upload audio without explicit consent
* Bypass Android security
* Bypass third-party application authentication
* Circumvent encryption
* Extract protected application data

---

# 8. Accessibility Rules

AccessibilityService must only be used for legitimate user-authorized functionality.

Do not:

* Build credential harvesting
* Read passwords unnecessarily
* Bypass authentication
* Extract unrelated private information
* Perform hidden actions
* Create arbitrary surveillance functionality

Prefer semantic UI elements over screen coordinates.

---

# 9. Code Quality

Prefer:

* Small classes
* Clear interfaces
* Strong typing
* Immutable state where practical
* Coroutines for asynchronous operations
* Dependency injection only when justified
* Testable components
* Clear error handling

Avoid:

* Giant classes
* Global mutable state
* Hardcoded device-specific behavior
* Magic numbers
* Duplicate logic

---

# 10. Android Version Compatibility

Never assume an API works identically on all Android versions.

When using a newer API:

* Check its minimum API
* Provide compatibility behavior
* Use runtime checks when necessary
* Document limitations

Primary test device:

Android 12 / API 31.

Development target:

Compile SDK 37 / Target SDK 37 (Minimum SDK 26).

---

# 11. Build Requirements

After meaningful changes:

```text
Build
 ↓
Unit tests
 ↓
Lint/static analysis where appropriate
 ↓
Install/run on device when relevant
```

Do not declare a feature complete if the project does not compile.

---

# 12. Testing

Every important feature should have appropriate tests.

Examples:

Intent parser:

```text
"Call Mom"
→ CALL_CONTACT
```

Alarm parser:

```text
"Wake me at 7"
→ SET_ALARM
```

Unknown contact:

```text
"Call John"
→ MultipleContactsFound
```

---

# 13. Logging

Logs must be useful but privacy-safe.

Never log:

* Passwords
* OTPs
* Full notification contents
* Full private messages
* Authentication tokens
* Raw microphone recordings

Use structured logging where appropriate.

---

# 14. Git Rules

Do not automatically rewrite history.

Do not force-push unless explicitly instructed.

Do not delete branches without approval.

Prefer small commits.

Examples:

```text
feat: add microphone permission flow
feat: add speech capture
feat: add command parser
fix: handle missing microphone permission
test: add command parser tests
docs: update architecture
```

---

# 15. Branch Rules

Development should normally happen on a feature branch.

Example:

```text
main
dev
feature/voice-prototype
feature/wake-word
feature/local-llm
feature/notifications
```

Do not directly modify `main` for experimental work.

---

# 16. Do Not Over-Engineer

Do not create multiple modules, abstractions, interfaces, or frameworks unless they solve a real problem.

The initial NOVA prototype should remain simple.

Architecture can evolve as requirements become real.

---

# 17. Documentation

When architecture changes, update:

* README.md
* PROJECT_SPEC.md
* ARCHITECTURE.md
* ROADMAP.md
* SECURITY.md

as appropriate.

Documentation must describe the actual implementation rather than the desired implementation.

---

# 18. Research Rule

AI agents must not assume that a library or Android API is current.

For:

* Android APIs
* Android permissions
* Gradle
* Kotlin
* Compose
* ML libraries
* GitHub dependencies
* AI models

verify current documentation before making major implementation decisions.

---

# 19. Model Selection

Do not download or integrate an AI model without checking:

* License
* Model size
* RAM requirements
* Android compatibility
* Quantization
* Performance
* Language support

A model being available on Hugging Face does not automatically mean it is suitable for redistribution or commercial use.

---

# 20. Native Code

NOVA may eventually use:

* C++
* C
* JNI
* Android NDK
* CMake

for components such as local AI inference.

Native code must be isolated from the Kotlin application layer.

Do not introduce native code unless required.

---

# 21. Human Approval

The AI agent must stop and request human approval before:

* Changing core architecture
* Adding a major dependency
* Adding cloud services
* Adding permissions unrelated to the current feature
* Changing security boundaries
* Changing the AI action model
* Removing tests
* Disabling security checks
* Introducing potentially destructive automation

---

# 22. Implementation Philosophy

The preferred order is:

```text
Understand
 ↓
Plan
 ↓
Implement
 ↓
Build
 ↓
Test
 ↓
Review
 ↓
Commit
```

Never:

```text
Guess
 ↓
Generate hundreds of files
 ↓
Hope it works
```

---

# 23. Final Rule

> **NOVA is an AI-assisted project, not an AI-controlled project.**

The AI agent writes code.

The developer controls the architecture, permissions, security model, and final decisions.
