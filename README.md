# OSAF JARVIS — Next-Generation Personal AI Assistant

**OSAF JARVIS** is a native Kotlin + Jetpack Compose Android AI assistant featuring an Iron-Man-inspired futuristic HUD, bilingual/multilingual support (**English**, **Urdu**, and **Roman Urdu**), an offline command & math/memory core, an OpenAI-compatible HTTPS Online AI engine with streaming, real Android device controls, and Android Keystore-backed API key encryption.

## Architecture Overview

- `com.example.MainActivity`: Edge-to-edge host activity.
- `com.osaf.jarvis.JarvisApplication` / `AppContainer`: Lightweight manual dependency container (optimized for 2GB–3GB RAM).
- `com.osaf.jarvis.JarvisCoordinator`: Central state machine (`ReactorState`: `IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `OFFLINE`, `ERROR`), duplicate command guard, and command/AI routing.
- `com.osaf.jarvis.ui.*`:
  - `home`: Responsive HUD dashboard with animated 60-FPS `ArcReactorView`, live battery/date/time, push-to-talk mic, wake-word toggle, confirmation cards, and quick command input.
  - `chat`: Lazy-loaded conversation history with SSE streaming display and bilingual quick prompts.
  - `controls`: Real flashlight toggle (`CameraManager`), media volume slider (`AudioManager`), system shortcuts, CPU ABI & RAM diagnostics, confirmation-gated Call/SMS dialer, and installed app launcher.
  - `memory`: Room-backed Notes search/CRUD, scheduled local Reminders, and JSON Backup Export/Import (SAF).
  - `settings`: HTTPS OpenAI-compatible AI config, Android Keystore AES-256-GCM key storage, connection tester, TTS voice/pitch/speed calibration, wake-word foreground service controls, and privacy toggles.
- `com.osaf.jarvis.voice` & `com.osaf.jarvis.wakeword` & `com.osaf.jarvis.services`:
  - `VoiceEngine`: `SpeechRecognizer` with live audio RMS feedback + `TextToSpeech` (English & Urdu locales).
  - `WakeWordEngine` & `JarvisVoiceService`: Opt-in `AudioRecord` acoustic energy gate + microphone `ForegroundService` with persistent Stop notification.
- `com.osaf.jarvis.commands` & `com.osaf.jarvis.ai`:
  - `CommandInterpreter`: Natural-language intent parser for English, Urdu, and Roman Urdu.
  - `OfflineAiEngine` & `OpenAiClient`: Honest offline capabilities and HTTPS + SSE streaming OpenAI Chat Completions client.

## Compatibility & Build Requirements

- **Minimum SDK**: Android 8.0 (API 26)
- **Target / Compile SDK**: API 36
- **CPU Architectures**: Universal pure-Kotlin/Java APK supporting `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` (no Play Services required for offline features).

## Building & Installing

1. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`
2. Build the release APK (requires release signing env vars `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`):
   ```bash
   gradle :app:assembleRelease
   ```
3. Run unit & Robolectric UI tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
4. Install on an Android 8.0+ device:
   Transfer `app-debug.apk` to the device or run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
