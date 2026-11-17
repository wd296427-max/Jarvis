# JARVIS AI — Android

A native Android personal assistant inspired by the open-source MARK assistant by FatihMakes.

## Implemented in the current prototype

- Kotlin + Jetpack Compose JARVIS interface.
- Gemini and OpenAI provider selection.
- Gemini `generateContent` and OpenAI Chat Completions API calls.
- API keys stored with AndroidX EncryptedSharedPreferences (Android Keystore-backed master key).
- Android speech-to-text input through the system recognizer.
- Spoken AI replies through Android TextToSpeech.
- Foreground microphone service prototype for "OK JARVIS".
- Android VoiceInteractionService entry points.
- GitHub Actions workflow that builds a debug APK on pushes to `main`.

## Known limitations

- The always-listening wake-word service currently acknowledges "OK JARVIS" but does not yet forward the follow-up spoken command to Gemini/OpenAI. Chat requests work through the chat composer; voice dictation fills the composer.
- Conversation history is currently in-memory and resets when the app process is restarted.
- The app does not yet include the desktop project's full tool/automation system. Android actions must be implemented using Android-supported APIs and user-granted permissions.
- A debug APK is not a Play Store release. A separately managed signing key is required for a distributable signed release.

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main` and can also be started manually.

- Workflow: [Android APK build](.github/workflows/android.yml)
- Expected APK: `app/build/outputs/apk/debug/app-debug.apk`
- Minimum Android: API 26 (Android 8.0)
- Compile / target SDK: 35
- JDK: 17; Gradle 8.9

## Run

Install the generated debug APK on an Android device. Open Settings in the app UI (provider panel), select Gemini or OpenAI, enter the corresponding API key and save it. Cloud AI requests require internet access and a valid API key. Keys are stored encrypted on the device and are not included in the repository.

"Independent phone operation" means the app runs on the phone without a PC. It does not mean cloud AI runs offline.

## Upstream project and attribution

The feature direction takes inspiration from [FatihMakes/Mark-LVII](https://github.com/FatihMakes/Mark-LVII). MARK LVII is a Python desktop application for Windows, macOS and Linux; it is not directly installable as an Android APK. This repository is a separate native Android implementation that adapts compatible concepts and rebuilds phone-specific functionality.

The upstream repository states that it is licensed under **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. Any upstream code copied or adapted must retain attribution and license notices; commercial use is not permitted without separate permission. See the [upstream license](https://github.com/FatihMakes/Mark-LVII/blob/main/LICENSE).

This Android project is an independent work in progress and is not an official FatihMakes release.
