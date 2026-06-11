package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VoiceScreen() {
    var prompt by rememberSaveable { mutableStateOf("") }
    var recording by rememberSaveable { mutableStateOf(false) }

    ScreenContainer(title = "Voice") {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            placeholder = { Text("Describe the problem, or record it…") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    // SpeechRecognizer integration comes later; toggle mock state
                    if (!recording) prompt = ""
                    recording = !recording
                },
                colors = if (recording) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
                Text(if (recording) "  Stop" else "  Record")
            }

            Button(
                onClick = { /* send the prompt to the agent loop */ },
                enabled = prompt.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("  Submit")
            }
        }

        if (recording) {
            Text(
                "Listening… (mock)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
