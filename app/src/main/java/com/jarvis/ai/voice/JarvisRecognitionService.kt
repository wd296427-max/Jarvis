package com.jarvis.ai.voice

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Thin local recognition provider used for assistant metadata compatibility. */
class JarvisRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) {
        callback?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }
    override fun onStopListening(callback: Callback?) { callback?.error(SpeechRecognizer.ERROR_CLIENT) }
    override fun onCancel(callback: Callback?) { }
}
