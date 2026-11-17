package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.ai.ai.AIManager
import com.jarvis.ai.service.WakeWordService
import kotlinx.coroutines.launch
import java.util.Locale

private val Night = Color(0xFF07111F)
private val Panel = Color(0xFF102238)
private val Cyan = Color(0xFF00D9FF)

private data class ChatMessage(val from: String, val text: String)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        setContent {
            JarvisScreen(
                onStart = { startWakeService() },
                onStop = { stopService(Intent(this, WakeWordService::class.java)) },
                onSpeak = { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply") }
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun startWakeService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisScreen(onStart: () -> Unit, onStop: () -> Unit, onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val ai = remember { AIManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SYSTEM ONLINE • READY") }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            ai.recentConversation().forEach { (role, text) ->
                add(ChatMessage(if (role == "assistant") "JARVIS" else "YOU", text))
            }
            if (isEmpty()) add(ChatMessage("JARVIS", "Good day. I am online. Add your OpenRouter API key, then start a conversation."))
        }
    }

    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) prompt = spoken
    }

    fun send() {
        val question = prompt.trim()
        if (question.isBlank() || busy) return
        prompt = ""
        messages.add(ChatMessage("YOU", question))
        busy = true
        status = "THINKING • OPENROUTER"
        scope.launch {
            val answer = runCatching { ai.ask(question) }
                .getOrElse { "I couldn't complete that request. ${it.message ?: "Please check your connection and API key."}" }
            messages.add(ChatMessage("JARVIS", answer))
            onSpeak(answer)
            busy = false
            status = "SYSTEM ONLINE • READY"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Night).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("J.A.R.V.I.S", color = Cyan, fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                Text("PERSONAL AI SYSTEM", color = Color.LightGray, fontSize = 10.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(72.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                    Text("J", color = Cyan, fontSize = 42.sp, fontWeight = FontWeight.Light)
                }
                Text(status, color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("OPENROUTER AI ENGINE", color = Cyan, style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Model: ${ai.model()}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(if (keySaved || ai.hasApiKey()) "KEY SAVED" else "NO KEY", color = if (keySaved || ai.hasApiKey()) Cyan else Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it; keySaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OpenRouter API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("API key encrypted on device", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            ai.clearMemory()
                            messages.clear()
                            messages.add(ChatMessage("JARVIS", "Local conversation memory cleared."))
                        }) { Text("Clear memory", color = Cyan) }
                        Button(
                            enabled = apiKey.isNotBlank(),
                            onClick = {
                                ai.saveApiKey(apiKey)
                                apiKey = ""
                                keySaved = true
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("Save key") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (message.from == "JARVIS") Panel else Color(0xFF16334A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(message.from, color = Cyan, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(3.dp))
                            Text(message.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to JARVIS…") },
                    maxLines = 3
                )
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
                    }
                    runCatching { speechLauncher.launch(intent) }.onFailure {
                        messages.add(ChatMessage("JARVIS", "Speech recognition is not available on this device."))
                    }
                }) { Text("🎙", color = Cyan) }
                IconButton(enabled = !busy && prompt.isNotBlank(), onClick = { send() }) {
                    Text("➤", color = Cyan, fontSize = 24.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wake word listener", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = listening, onCheckedChange = {
                    listening = it
                    if (it) onStart() else onStop()
                })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
        }
    }
}
