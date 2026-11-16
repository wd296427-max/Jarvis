package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.ai.service.WakeWordService

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        setContent { JarvisScreen(onStart = { startWakeService() }, onStop = { stopService(Intent(this, WakeWordService::class.java)) }) }
    }
    private fun startWakeService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
        }
    }
}

@Composable
private fun JarvisScreen(onStart: () -> Unit, onStop: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("SYSTEM STANDBY") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00D9FF), background = Color(0xFF07111F), surface = Color(0xFF102238))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF07111F)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("J.A.R.V.I.S", color = Color(0xFF00D9FF), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Text("PERSONAL AI ASSISTANT", color = Color.LightGray, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(54.dp))
            Box(Modifier.size(190.dp).background(Color(0xFF102238), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).background(Color(0xFF0B1B2D), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (enabled) "LISTENING" else "J", color = Color(0xFF00D9FF), fontSize = if (enabled) 18.sp else 64.sp, fontWeight = FontWeight.Light)
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(if (enabled) "OK JARVIS WAKE WORD ACTIVE" else status, color = Color(0xFF00D9FF), fontSize = 13.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(30.dp))
            Button(onClick = { if (enabled) { onStop(); enabled = false; status = "SYSTEM STANDBY" } else { onStart(); enabled = true; status = "STARTING LISTENER" } }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (enabled) "STOP LISTENING" else "ACTIVATE OK JARVIS", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text("Say ‘OK JARVIS’ to activate the assistant. Keep the foreground notification visible while listening.", color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
