package com.jarvis.ai.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.jarvis.ai.MainActivity
import java.util.Locale

/** Uses Android's speech recognizer; this is not a dedicated low-power keyword model. */
class WakeWordService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var waitingForCommand = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { android.os.Handler(mainLooper).post { listen() } }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { restartSoon() }
        })
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return }
        recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(this)
        listen()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    private fun listen() {
        if (restarting) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 31) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try { recognizer?.startListening(i) } catch (_: Exception) { restartSoon() }
    }
    override fun onResults(results: Bundle?) {
        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
        if (waitingForCommand) {
            waitingForCommand = false
            if (phrase.isNotBlank()) speak("I heard: $phrase. Connect an AI service to process commands.") else listen()
        } else if (phrase.contains("ok jarvis") || phrase.contains("okay jarvis")) {
            waitingForCommand = true
            speak("Yes, I'm listening. What's your command?")
        } else listen()
    }
    private fun speak(message: String) {
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response") else listen()
    }
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) tts?.language = Locale.getDefault() }
    override fun onError(error: Int) { restartSoon() }
    override fun onEndOfSpeech() { }
    override fun onReadyForSpeech(params: Bundle?) { }
    override fun onBeginningOfSpeech() { }
    override fun onRmsChanged(rmsdB: Float) { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onPartialResults(partialResults: Bundle?) {
        val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
        if (!waitingForCommand && (phrase.contains("ok jarvis") || phrase.contains("okay jarvis"))) {
            try { recognizer?.cancel() } catch (_: Exception) { }
            waitingForCommand = true
            speak("Yes, I'm listening. What's your command?")
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) { }
    private fun restartSoon() { if (restarting) return; restarting = true; android.os.Handler(mainLooper).postDelayed({ restarting = false; listen() }, 900) }
    override fun onDestroy() {
        restarting = true
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "JARVIS microphone", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS is listening")
        .setContentText("Say OK JARVIS. Tap Stop to end microphone listening.")
        .setOngoing(true).addAction(0, "Stop", android.app.PendingIntent.getService(this, 2, Intent(this, WakeWordService::class.java).setAction("STOP"), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
        .setContentIntent(android.app.PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)).build()
    companion object { private const val CHANNEL_ID = "jarvis_mic"; private const val NOTIFICATION_ID = 41 }
}
