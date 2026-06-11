package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class MockMessage(val fromUser: Boolean, val text: String)

private val mockMessages = listOf(
    MockMessage(true, "My computer has no internet."),
    MockMessage(false, "I'll take a look. Please take a photo of the screen so I can see the current network state."),
    MockMessage(true, "[Photo attached: desktop with network error icon]"),
    MockMessage(false, "I can see the Wi-Fi icon shows \"No internet\". I'd like to open the network settings with Win+I and check the adapter state."),
)

@Composable
fun ChatScreen() {
    var draft by rememberSaveable { mutableStateOf("") }

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
            Text(
                "Chat",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(mockMessages) { message ->
                    MessageBubble(message)
                }
                item {
                    ActionProposalCard()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Describe the problem…") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { draft = "" }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MockMessage) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .align(if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart),
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun ActionProposalCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Proposed action", style = MaterialTheme.typography.labelLarge)
            Text("key_combo: [Win, I] — open Windows Settings")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { /* approve the proposed action */ }) { Text("Approve") }
                OutlinedButton(onClick = { /* reject the proposed action */ }) { Text("Deny") }
            }
        }
    }
}
