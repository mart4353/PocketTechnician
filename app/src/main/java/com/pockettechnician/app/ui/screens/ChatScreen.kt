package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.hid.HidState
import com.pockettechnician.app.ui.chat.ChatItem
import com.pockettechnician.app.ui.chat.ChatViewModel
import com.pockettechnician.app.ui.chat.ProposalStatus

@Composable
fun ChatScreen() {
    val application = LocalContext.current.applicationContext as PocketTechnicianApplication
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(application))
    val items by viewModel.items.collectAsState()
    val hid by application.hidManager.state.collectAsState()

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
                "Chat — HID demo",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            HidConnectionPanel(
                hid = hid,
                onStart = { application.hidManager.start() },
                onRefresh = { application.hidManager.refreshBondedHosts() },
                onConnect = { application.hidManager.connect(it) },
                onDisconnect = { application.hidManager.disconnect() },
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Said -> MessageBubble(item)
                        is ChatItem.Proposal -> ActionProposalCard(
                            proposal = item,
                            hidConnected = hid.connected,
                            onApprove = { viewModel.approve(item.key) },
                            onDeny = { viewModel.deny(item.key) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::reset) {
                    Text("Restart demo")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HidConnectionPanel(
    hid: HidState,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hid.connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hid.connected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                )
                Text(
                    text = "  HID: ${hid.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!hid.registered) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start HID service")
                }
            } else if (hid.connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            } else {
                Text(
                    "Pick the computer to control:",
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hid.bondedHosts.forEach { host ->
                        AssistChip(
                            onClick = { onConnect(host.address) },
                            label = { Text(host.name) },
                            leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
                        )
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh paired devices")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatItem.Said) {
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
private fun ActionProposalCard(
    proposal: ChatItem.Proposal,
    hidConnected: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Proposed action", style = MaterialTheme.typography.labelLarge)
                StatusLabel(proposal.status)
            }
            Text(
                text = "${proposal.label}: ${proposal.detail}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )

            when (proposal.status) {
                ProposalStatus.Pending -> {
                    if (!hidConnected) {
                        Text(
                            "Connect a host above to enable this action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApprove, enabled = hidConnected) { Text("Approve") }
                        OutlinedButton(onClick = onDeny) { Text("Deny") }
                    }
                }
                ProposalStatus.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Sending over HID…")
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StatusLabel(status: ProposalStatus) {
    val (text, color) = when (status) {
        ProposalStatus.Pending -> "Awaiting approval" to MaterialTheme.colorScheme.onSurfaceVariant
        ProposalStatus.Running -> "Running" to MaterialTheme.colorScheme.primary
        ProposalStatus.Done -> "Done ✓" to MaterialTheme.colorScheme.primary
        ProposalStatus.Failed -> "Failed" to MaterialTheme.colorScheme.error
        ProposalStatus.Denied -> "Denied" to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}
