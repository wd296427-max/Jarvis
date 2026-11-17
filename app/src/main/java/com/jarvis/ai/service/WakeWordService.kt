package com.jarvis.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.jarvis.ai.MainActivity
import com.jarvis.ai.ai.AIManager
import com.jarvis.ai.ai.AIProvider
import com.jarvis.ai.device.DeviceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * User-started, visible foreground microphone session.
 * This is continuous speech-recognizer cycling, not an always-on DSP keyword detector.
 */
class WakeWordService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var waitingForCommand = false
    private var processing = false
    private var shuttingDown = false
    private lateinit var ai: AIManager
    private lateinit var devices: DeviceController

    override fun onCreate() {
        super.onCreate()
        ai = AIManager(applicationContext)
        devices = DeviceController(applicationContext)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { handler.post { processing = false; statusUpdate("Listening • say OK JARVIS"); scheduleListen(350) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { handler.post { processing = false; scheduleListen(900) } }
        })
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available on this phone.")
            handler.postDelayed({ stopSelf() }, 2500)
            return
        }
        recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
        recognizer?.setRecognitionListener(this)
        scheduleListen(700)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun scheduleListen(delay: Long) {
        if (shuttingDown || processing) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delay)
    }

    private fun listen() {
        if (shuttingDown || processing) return
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 31) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(speechIntent) ?: stopSelf()
        } catch (_: Exception) {
            scheduleListen(1200)
        }
    }

    override fun onResults(results: Bundle?) {
        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        if (phrase.isBlank()) {
            scheduleListen(500)
            return
        }
        val normalized = phrase.lowercase(Locale.ROOT)
        val wake = listOf("okay jarvis", "ok jarvis", "hey jarvis")
            .firstOrNull { normalized.contains(it) }

        if (!waitingForCommand && wake == null) {
            scheduleListen(250)
            return
        }

        val command = if (wake != null) normalized.substringAfter(wake).trim(' ', ',', '.', '!', '?') else phrase
        if (command.isBlank()) {
            waitingForCommand = true
            speak("Yes. What can I do for you?")
            return
        }
        waitingForCommand = false
        processCommand(command)
    }

    private fun processCommand(command: String) {
        processing = true
        statusUpdate("Processing your command")
        val localResult = devices.executeIfSupported(command)
        if (localResult != null) {
            speak(localResult)
            return
        }
        serviceScope.launch {
            val provider = ai.activeProvider()
            val response = runCatching { ai.ask(provider, command) }
                .getOrElse { "I couldn't reach ${provider.name}. ${it.message ?: "Check your internet connection and API key in the JARVIS app."}" }
            speak(response)
        }
    }

    private fun statusUpdate(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(message))
    }

    private fun speak(message: String) {
        if (!ttsReady || tts == null) {
            processing = false
            scheduleListen(1200)
            return
        }
        tts?.speak(message.take(3500), TextToSpeech.QUEUE_FLUSH, null, "jarvis-command")
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.getDefault()
            scheduleListen(500)
        }
    }

    override fun onError(error: Int) {
        scheduleListen(900)
    }

    override fun onEndOfSpeech() { }
    override fun onReadyForSpeech(params: Bundle?) { }
    override fun onBeginningOfSpeech() { }
    override fun onRmsChanged(rmsdB: Float) { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onPartialResults(partialResults: Bundle?) { }
    override fun onEvent(eventType: Int, params: Bundle?) { }

    override fun onDestroy() {
        shuttingDown = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS voice assistant", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(message: String = "Say OK JARVIS, then give a command") =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS voice assistant")
            .setContentText(message)
            .setOngoing(true)
            .addAction(0, "Stop listening", PendingIntent.getService(
                this, 2, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setContentIntent(PendingIntent.getActivity(
                this, 1, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )).build()

    companion object {
        private const val CHANNEL_ID = "jarvis_mic"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "com.jarvis.ai.STOP_LISTENING"
    }
}
