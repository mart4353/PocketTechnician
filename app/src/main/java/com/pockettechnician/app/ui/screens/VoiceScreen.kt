package com.pockettechnician.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.ui.chat.ChatViewModel
import java.util.Locale

/**
 * Voice tab: dictate the problem with on-device [SpeechRecognizer], then submit
 * it into the active conversation via the shared [ChatViewModel] (same path the
 * Chat tab uses) and jump to the Chat tab to see the reply.
 */
@Composable
fun VoiceScreen(onSubmitted: () -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as PocketTechnicianApplication
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(application))

    var prompt by rememberSaveable { mutableStateOf("") }
    var recording by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    // Text already in the field when the current dictation began; live results
    // are appended onto this so manual edits before recording are preserved.
    var baseText by remember { mutableStateOf("") }

    val recognizer = remember {
        if (recognitionAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun appendRecognized(text: String) {
        prompt = (baseText.trimEnd() + " " + text).trim()
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status = "Listening…"
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { appendRecognized(it) }
            }

            override fun onResults(results: Bundle?) {
                results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { appendRecognized(it) }
                baseText = prompt
                recording = false
                status = null
            }

            override fun onError(error: Int) {
                recording = false
                baseText = prompt
                status = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    else -> "Recognition error ($error)."
                }
            }

            override fun onEndOfSpeech() {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            recognizer?.destroy()
        }
    }

    fun startListening() {
        baseText = prompt
        status = "Listening…"
        recording = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer?.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else status = "Microphone permission required to record."
    }

    fun toggleRecording() {
        if (recording) {
            recognizer?.stopListening()
            recording = false
            status = null
            return
        }
        if (!recognitionAvailable || recognizer == null) {
            status = "Speech recognition isn't available on this device."
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ScreenContainer(title = "Voice") {
        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
                baseText = it
            },
            label = { Text("Prompt") },
            placeholder = { Text("Describe the problem, or record it…") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { toggleRecording() },
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
                onClick = {
                    if (recording) {
                        recognizer?.stopListening()
                        recording = false
                    }
                    viewModel.send(prompt)
                    prompt = ""
                    baseText = ""
                    status = null
                    onSubmitted()
                },
                enabled = prompt.isNotBlank() && !recording,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("  Submit")
            }
        }

        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (recording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
