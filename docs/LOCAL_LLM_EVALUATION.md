# NOVA Technical Evaluation: On-Device Small Language Models (SLMs) for Intent Parsing

## 1. Executive Summary

This evaluation analyzes the feasibility, performance, memory overhead, and architecture of embedding an on-device Small Language Model (SLM) into NOVA for converting unstructured user speech transcripts into strongly typed, validated `NovaAction` structures.

---

## 2. Core Architectural Principles

1. **Strict Separation of Concerns**:
   - **AI / SLM Role**: Intent Understanding only. Translates natural language into a structured JSON/data object schema.
   - **Android Role (`ActionManager`)**: Security Boundary and Execution. Validates permissions, checks parameter bounds, verifies device state, and executes approved Android APIs.
   - **Zero Direct Execution**: An LLM must NEVER have direct access to Android system services, shell commands, or file operations.

2. **Two-Tier Hybrid Intent Pipeline**:
   - **Tier 1 (0ms - 5ms)**: `NaturalLanguageIntentParser` (deterministic regex/semantic slot filling). Handles 95%+ of standard voice commands (calls, messages, alarms, timers, app launches, flashlight, web search) with zero memory/compute footprint.
   - **Tier 2 (150ms - 600ms)**: Pluggable on-device quantized SLM. Invoked only when Tier 1 produces `NovaAction.Unknown` for ambiguous, compound, or conversational phrasing.

```
Speech Transcript
       │
       ▼
┌─────────────────────────────────┐
│ Tier 1: Fast Semantic Parser    │ ──(Matched)──► Validated NovaAction
└─────────────────────────────────┘                       │
       │ (Ambiguous / Unknown)                             │
       ▼                                                   ▼
┌─────────────────────────────────┐               ┌──────────────────┐
│ Tier 2: Local SLM (ONNX/GGUF)   │               │ ActionManager    │
│ Strict JSON Schema Output Only  │               │ (Security & Exec)│
└─────────────────────────────────┘               └──────────────────┘
       │                                                   ▲
       └────────────── Structured NovaAction ──────────────┘
```

---

## 3. Evaluated On-Device SLM Candidates

| Model | Parameters | Quantization | Size (RAM / Disk) | Inference Latency (Snapdragon 8 / Dimensity) | License | Offline / Privacy |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SmolLM-135M-Instruct** | 135 Million | INT4 / Q4_K_M | ~85 MB / 120 MB RAM | ~80 - 150 ms | Apache 2.0 | 100% On-device |
| **SmolLM-360M-Instruct** | 360 Million | INT4 / Q4_K_M | ~220 MB / 310 MB RAM | ~180 - 320 ms | Apache 2.0 | 100% On-device |
| **Qwen2.5-0.5B-Instruct** | 490 Million | Q4_K_M / INT4 | ~350 MB / 480 MB RAM | ~220 - 450 ms | Apache 2.0 | 100% On-device |
| **Gemma-2B-IT** | 2.0 Billion | INT4 | ~1.4 GB / 1.8 GB RAM | ~600 - 1400 ms | Gemma Open Terms | 100% On-device |

---

## 4. Android Runtimes Compared

1. **ONNX Runtime Mobile (ORT GenAI)**:
   - **Pros**: Direct NNAPI / QNN / Vulkan acceleration on Android, official Microsoft support, compact runtime (~12 MB).
   - **Cons**: Model quantization pipeline requires ONNX toolchain.

2. **MediaPipe LLM Inference (Google)**:
   - **Pros**: Native Android library with GPU/NPU acceleration, optimized for Gemma 2B, Qwen, and Phi.
   - **Cons**: Model format is tied to MediaPipe bundle.

3. **llama.cpp / ExecuTorch (Native C++ via JNI)**:
   - **Pros**: Pure C++ with negligible APK overhead (<5 MB binary), direct GGUF file loading from internal storage.
   - **Cons**: Requires custom JNI bridging and memory mapping.

---

## 5. Intent JSON Schema Definition

When the local SLM is invoked, it is constrained to output only this strict JSON schema:

```json
{
  "action": "CALL_CONTACT | SEND_MESSAGE | SET_ALARM | SET_TIMER | OPEN_APP | DEVICE_CONTROL | WEB_SEARCH | HELP | UNKNOWN",
  "recipient": "string (optional)",
  "messageText": "string (optional)",
  "platform": "SMS | WHATSAPP | DEFAULT (optional)",
  "hour": 0,
  "minute": 0,
  "durationSeconds": 0,
  "targetApp": "string (optional)",
  "deviceFeature": "FLASHLIGHT | VOLUME_MUTE | WIFI | BLUETOOTH (optional)",
  "state": true,
  "query": "string (optional)"
}
```

---

## 6. Recommendation for NOVA Roadmap

1. **Current Phase**: Use high-speed on-device `NaturalLanguageIntentParser` which delivers deterministic, instant (<5ms), zero-RAM intent classification across all core actions.
2. **Next Milestone**: Integrate **SmolLM-135M** or **SmolLM-360M** via **ONNX Runtime Mobile** or **llama.cpp** as an optional downloadable add-on module (to keep base APK lightweight and avoid RAM overhead on entry-level Android devices).
3. **No Cloud LLMs**: NOVA preserves complete privacy by maintaining zero cloud dependencies for user voice or intent processing.
