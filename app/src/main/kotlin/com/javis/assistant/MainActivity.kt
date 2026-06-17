package com.javis.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.javis.assistant.ui.navigation.JavisNavGraph
import com.javis.assistant.ui.theme.JavisTheme
import com.javis.assistant.voice.JavisVoiceService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleVoiceTriggerIntent(intent)

        setContent {
            JavisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JavisNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceTriggerIntent(intent)
    }

    private fun handleVoiceTriggerIntent(intent: Intent?) {
        if (intent?.action == "com.javis.assistant.ACTION_VOICE_TRIGGER") {
            val serviceIntent = Intent(this, JavisVoiceService::class.java).apply {
                action = JavisVoiceService.ACTION_START_LISTENING
            }
            startService(serviceIntent)
        }
    }
}
