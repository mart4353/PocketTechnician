package com.pockettechnician.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.data.chat.ChatMessage
import com.pockettechnician.app.data.chat.ChatRole
import com.pockettechnician.app.data.chat.HidTools
import com.pockettechnician.app.data.chat.ToolCall
import com.pockettechnician.app.data.chat.ToolCallStatus
import com.pockettechnician.app.ui.compressAndResizeJpeg
import com.pockettechnician.app.ui.chat.ChatUiState
import com.pockettechnician.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun ChatScreen() {
    val application = LocalContext.current.applicationContext as PocketTechnicianApplication
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(application))
    val uiState by viewModel.uiState.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Temp file for system camera output
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraFile?.let { file ->
                coroutineScope.launch {
                    val compressed = withContext(Dispatchers.IO) {
                        compressAndResizeJpeg(file.readBytes())
                    }
                    val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
                    viewModel.attachImage(base64)
                }
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            ChatHeader(uiState, onNewConversation = viewModel::startNewConversation)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item { EmptyChatHint(uiState.modelConfigured) }
                }
                items(uiState.messages) { message ->
                    MessageBubble(
                        message = message,
                        hidConnected = uiState.hidConnected,
                        onResolveToolCall = { toolCallId, accepted ->
                            viewModel.resolveToolCall(message.id, toolCallId, accepted)
                        },
                    )
                }
                if (uiState.isSending) {
                    item { ThinkingIndicator() }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // Pending image preview
            uiState.pendingImageBase64?.let { b64 ->
                PendingImagePreview(
                    base64 = b64,
                    onClear = viewModel::clearPendingImage,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        val file = File(
                            context.cacheDir,
                            "camera/photo_${System.currentTimeMillis()}.jpg",
                        ).also {
                            it.parentFile?.mkdirs()
                            cameraFile = it
                        }
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        takePictureLauncher.launch(uri)
                    },
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Attach photo")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe the problem…") },
                    maxLines = 4,
                )
                FilledIconButton(
                    onClick = {
                        viewModel.send(draft)
                        draft = ""
                    },
                    enabled = (draft.isNotBlank() || uiState.pendingImageBase64 != null) && !uiState.isSending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun PendingImagePreview(
    base64: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, base64) {
        value = withContext(Dispatchers.Default) {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    bitmap?.let { bmp ->
        Box(modifier = modifier) {
            Image(
                bitmap = bmp,
                contentDescription = "Attached photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            SmallFloatingActionButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove photo",
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(uiState: ChatUiState, onNewConversation: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiState.conversationTitle ?: "Chat",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            uiState.modelLabel?.let { model ->
                Text(
                    text = model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNewConversation) {
            Icon(Icons.Filled.Add, contentDescription = "New conversation")
        }
    }
}

@Composable
private fun EmptyChatHint(modelConfigured: Boolean) {
    Text(
        text = if (modelConfigured) {
            "Describe the computer problem to get started."
        } else {
            "Add an API key and select a model on the Dashboard tab, then describe the problem here."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    hidConnected: Boolean,
    onResolveToolCall: (toolCallId: String, accepted: Boolean) -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    val bitmap by produceState<ImageBitmap?>(null, message.imageBase64) {
        value = message.imageBase64?.let { b64 ->
            withContext(Dispatchers.Default) {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (message.text.isNotBlank() || bitmap != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (fromUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .align(if (fromUser) Alignment.CenterEnd else Alignment.CenterStart),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = "Attached photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(message.text)
                        }
                    }
                }
            }
        }
        message.toolCalls.forEach { call ->
            ToolCallCard(
                call = call,
                hidConnected = hidConnected,
                onAccept = { onResolveToolCall(call.id, true) },
                onReject = { onResolveToolCall(call.id, false) },
            )
        }
    }
}

@Composable
private fun ToolCallCard(
    call: ToolCall,
    hidConnected: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = toolCallTitle(call.name),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ToolStatusLabel(call.status)
            }
            Text(
                text = toolCallDetail(call),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            call.resultText?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (call.status) {
                ToolCallStatus.PENDING -> {
                    if (!hidConnected) {
                        Text(
                            text = "No computer connected — running it will fail. Connect on the Dashboard tab.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAccept) { Text("Accept") }
                        OutlinedButton(onClick = onReject) { Text("Reject") }
                    }
                }
                ToolCallStatus.RUNNING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Running…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ToolStatusLabel(status: ToolCallStatus) {
    val (label, color) = when (status) {
        ToolCallStatus.PENDING -> "Awaiting approval" to MaterialTheme.colorScheme.onTertiaryContainer
        ToolCallStatus.RUNNING -> "Running" to MaterialTheme.colorScheme.onTertiaryContainer
        ToolCallStatus.EXECUTED -> "Done" to MaterialTheme.colorScheme.primary
        ToolCallStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        ToolCallStatus.REJECTED -> "Rejected" to MaterialTheme.colorScheme.error
    }
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
}

private fun toolCallTitle(name: String): String = when (name) {
    HidTools.TYPE_TEXT -> "Type text"
    HidTools.MOVE_POINTER_TO -> "Move pointer to"
    HidTools.MOUSE_PRESS -> "Mouse click"
    else -> name
}

private fun toolCallDetail(call: ToolCall): String {
    val args = runCatching { JSONObject(call.arguments) }.getOrNull() ?: return call.arguments
    return when (call.name) {
        HidTools.TYPE_TEXT -> "\"${args.optString("text")}\""
        HidTools.MOVE_POINTER_TO -> "x ${args.optInt("x")}, y ${args.optInt("y")}"
        HidTools.MOUSE_PRESS -> "${args.optString("button")} button"
        else -> call.arguments
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            "Thinking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
