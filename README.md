# JARVIS AI — Android

A native Android assistant inspired by the feature direction of FatihMakes' open-source MARK project. This is a separate Android implementation, not an official MARK release.

## Implemented features

- Kotlin + Jetpack Compose JARVIS interface.
- Gemini and OpenAI chat providers; the active provider is remembered.
- API keys encrypted locally with AndroidX Security Crypto.
- Android speech-to-text input and spoken replies.
- User-started foreground voice service: say "OK JARVIS", "Okay Jarvis", or "Hey Jarvis", then give a command. The service captures the next utterance, runs an allow-listed phone action or sends it to the selected AI, speaks the result, and resumes listening.
- Persistent local conversation history in a private SQLite database. Recent turns are included as context for subsequent Gemini/OpenAI requests and restored in the chat screen.
- Clear-memory control in the app.
- Android action commands currently supported:
  - Open Settings, Wi-Fi, Bluetooth, Display, Sound, Battery, Notifications and Do Not Disturb settings.
  - Launch installed apps by name (for example, "open app YouTube").
  - Open Camera and Clock.
  - Set an alarm (for example, "set an alarm for 7:30 AM").
  - Turn the flashlight on/off (camera permission required).
  - Increase/decrease/mute/unmute media volume.
  - Open the dialer with a number filled in (user confirms the call).
  - Open the messaging app with a draft (user reviews and sends).
- GitHub Actions workflow for building a debug APK.

## Voice service limitations and privacy

The wake-word implementation cycles Android's SpeechRecognizer in a visible foreground microphone service. It is **not** a dedicated low-power hotword DSP and may consume battery or be interrupted by Android, device vendor policies, unavailable offline speech packs, or microphone restrictions. The user must start it from the app and can stop it from the persistent notification. It does not secretly record or listen before the user enables the service.

The service is designed to resume listening after each response, but real-world behavior must be tested on the target phone. A hardware-level always-on wake word, guaranteed background operation, and offline speech recognition are not claimed.

Conversation history is stored locally in the app's private SQLite database. API keys are encrypted locally. When using Gemini or OpenAI, prompts and relevant recent conversation turns are sent to the selected provider over the internet. Clear Memory deletes the local conversation history.

## Android controls: important boundaries

Android intentionally restricts direct access to sensitive settings. JARVIS opens the appropriate system settings panel for Wi-Fi, Bluetooth, display, battery, notifications and Do Not Disturb instead of silently changing protected settings. Calls and SMS are opened as drafts for user review. No root access, accessibility-service surveillance, or bypass of Android security controls is included.

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main` and can be run manually.

- Workflow: [Android APK build](.github/workflows/android.yml)
- Expected APK: `app/build/outputs/apk/debug/app-debug.apk`
- Minimum Android: API 26 (Android 8.0)
- Compile / target SDK: 35
- JDK: 17; Gradle 8.9

## Run

Install the debug APK on an Android device. Select Gemini or OpenAI, enter the matching API key and save it. Start the voice listener switch and grant microphone/notification permissions. Grant camera permission if you want flashlight control. Cloud AI requests require internet access and a valid provider API key.

"Independent phone operation" means the app runs on the phone without a PC. Gemini and OpenAI are cloud services, so AI conversations require internet access; this build is not an offline LLM.

## Upstream attribution and license

The feature direction takes inspiration from [FatihMakes/Mark-LVII](https://github.com/FatihMakes/Mark-LVII). MARK LVII is a Python desktop application for Windows, macOS and Linux; it is not directly installable as an Android APK. This repository is a separate native Android implementation.

The upstream repository states that it is licensed under **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. Any upstream code copied or adapted must retain attribution and license notices; commercial use is not permitted without separate permission. See the [upstream license](https://github.com/FatihMakes/Mark-LVII/blob/main/LICENSE).

This Android project is an independent work in progress and is not an official FatihMakes release.
