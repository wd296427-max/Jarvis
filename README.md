# JARVIS AI — Android

A native Android personal assistant project inspired by the open-source MARK assistant by FatihMakes.

## Current implementation

This repository already contains an initial Android-native foundation:

- Kotlin + Jetpack Compose launcher screen.
- Microphone and notification permission requests.
- Foreground microphone service with Android speech recognition and text-to-speech.
- Android VoiceInteractionService entry points.
- GitHub Actions workflow that builds a debug APK.

**Important:** The current wake-word service is a prototype. It detects "OK JARVIS" and acknowledges the user, but it does not yet send commands to an AI model. Gemini/OpenAI chat, persistent memory, Android action tools, and secure API-key storage are still to be implemented and tested.

## Android build

The GitHub Actions workflow builds the debug APK on pushes to `main` and on manual dispatch.

- Workflow: [Android APK build](.github/workflows/android.yml)
- APK output: `app/build/outputs/apk/debug/app-debug.apk`
- Minimum Android version: API 26 (Android 8.0)
- Compile / target SDK: 35
- JDK: 17

## Planned milestones

1. Add a chat screen and provider abstraction for Gemini and OpenAI.
2. Add encrypted API-key storage and provider selection / fallback.
3. Connect speech recognition and text-to-speech to real AI conversations.
4. Add conversation history and local memory.
5. Add Android-native actions (open apps, alarms, notifications, settings shortcuts) with explicit user confirmation where needed.
6. Add reliable wake-word behavior, battery controls and background-service safeguards.
7. Run device tests, fix crashes, and publish a signed release APK.

The app will run on the phone without a PC. Cloud AI requests require internet access and the user's own API key. Fully offline AI is a separate future capability and is not implied by "independent phone operation".

## Upstream project and attribution

The design and feature roadmap take inspiration from [FatihMakes/Mark-LVII](https://github.com/FatihMakes/Mark-LVII). The upstream project is a Python desktop application for Windows, macOS and Linux; it is not directly installable as an Android APK. This repository is a separate native Android implementation and will reuse/adapt compatible ideas rather than package the desktop UI.

The upstream repository states that it is licensed under **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. Any upstream code copied or adapted must preserve attribution and license notices; commercial use is not permitted without separate permission. See the [upstream license](https://github.com/FatihMakes/Mark-LVII/blob/main/LICENSE).

This Android project is an independent work in progress and is not an official FatihMakes release.
