# OSAF JARVIS 2.0 — Next-Generation Personal AI Assistant

**OSAF JARVIS 2.0** is a native Kotlin + Jetpack Compose personal AI assistant built for Android 8.0+ (API 26–36) with pure-JVM/Kotlin lightweight architecture tailored for 2GB–3GB RAM devices.

## Key Upgrades in 2.0

1. **Human-Like Neural Voice Engine (`NeuralTtsClient` + `VoiceEngine`)**:
   - **Online Neural TTS**: Supports OpenAI Neural Voice (`gpt-4o-mini-tts` / `tts-1`, default voice `onyx`) and ElevenLabs streaming (`eleven_multilingual_v2`) plus custom OpenAI-compatible HTTPS TTS endpoints.
   - **Sentence-by-Sentence Streaming Playback (`SpeechSentenceChunker`)**: Speaks the very first sentence as SSE tokens arrive for minimal latency.
   - **Best Offline Fallback**: Automatically ranks installed local/network `ur-PK` and `en` device TTS voices when offline — without misrepresenting basic Android TTS as neural human speech.
2. **Real-Time Conversation & Barge-In**:
   - Supports continuous follow-up turn listening and instant **barge-in interruption** via hardware `AcousticEchoCanceler` voice detection or tapping the orb/mic button mid-reply.
3. **Emotion-Aware Personality & Long-Term Memory (`PersonalityEngine` + Room DB v2)**:
   - Detects user mood (`HAPPY`, `SAD`, `FRUSTRATED`, `CONFUSED`, `TIRED`, `NEUTRAL`) across **English**, **Urdu**, and **Roman Urdu**.
   - Remembers user preferences, active projects, personal facts, and standing rules (`MemoryFactEntity`), tracks frequent commands (`CommandStatEntity`), and offers a warm 24-hour inactivity check-in in the app UI (zero spam notifications).
4. **Live Web Knowledge, Vision & Documents**:
   - `WebSearchTool` fetches live HTTPS answers from DuckDuckGo Instant Answer and Wikipedia REST APIs.
   - `AttachmentHelper` + Android Photo Picker (`PickVisualMedia`) enable multimodal image inspection and local text/PDF document analysis.
5. **Simple Premium Dark UI & Official Branding**:
   - Deep black `#05080F` surface with subtle electric blue lighting, animated central orb, 1 prominent mic button, minimal conversation snippet, launch splash, adaptive icon, and About section featuring the official OSAF JARVIS logo.

## API Configuration Guide

1. Open **Settings** (gear icon on the Home screen).
2. Under **Online AI Brain (OpenAI-Compatible)**:
   - Set **HTTPS Chat Completions Endpoint** (default `https://api.openai.com/v1/chat/completions`).
   - Enter your **API Key** (encrypted locally with Android Keystore AES-GCM).
   - Set **Model Name** (e.g. `gpt-4o-mini`, `gpt-4o`), then tap **Save Config** and **Test Link**.
3. Under **Human Voice & Neural TTS**:
   - Choose **OpenAI Neural Voice** (reuses your main OpenAI key automatically or accepts a dedicated TTS key) or **ElevenLabs Human Voice** (with your `xi-api-key`).
   - Select **Best Device Offline TTS** for zero-network usage.

## Building & Installing

1. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`
2. Build a signed release APK (set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables first):
   ```bash
   gradle :app:assembleRelease
   ```
3. Run pure-JVM unit tests:
   ```bash
   gradle :app:testDebugUnitTest --tests "com.osaf.jarvis.JarvisCoreTests" --tests "com.example.ExampleUnitTest"
   ```
4. Transfer `app-debug.apk` to an Android 8.0+ phone and tap to install.
