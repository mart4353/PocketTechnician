package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.data.ai.AiProvider
import com.pockettechnician.app.hid.HidState
import com.pockettechnician.app.ui.dashboard.DashboardViewModel
import kotlin.math.roundToInt

private data class SliderLevel(val label: String, val description: String)

private val automationLevels = listOf(
    SliderLevel("Manual", "Every single action needs a tap"),
    SliderLevel("Step", "One approval per proposed step"),
    SliderLevel("Standard", "Safe actions run automatically; sensitive ones ask"),
    SliderLevel("Automatic", "Everything runs automatically except the safety gates"),
)
private val effortLevels = listOf(
    SliderLevel("low", "Maps to Claude output_config.effort"),
    SliderLevel("medium", "Maps to Claude output_config.effort"),
    SliderLevel("high", "Maps to Claude output_config.effort"),
    SliderLevel("max", "Maps to Claude output_config.effort"),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val application = LocalContext.current.applicationContext as PocketTechnicianApplication
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(application))
    val uiState by viewModel.uiState.collectAsState()
    val hid by application.hidManager.state.collectAsState()

    var automation by rememberSaveable(key = "automationV2") { mutableFloatStateOf(0f) }
    var effort by rememberSaveable { mutableFloatStateOf(2f) }
    var photoResolution by rememberSaveable { mutableIntStateOf(0) }
    var modelMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val selectedRemoteModel = remember(uiState.selectedModel, uiState.availableModels) {
        val selected = uiState.selectedModel
        uiState.availableModels.firstOrNull { model ->
            selected != null &&
                selected.provider == model.provider &&
                selected.modelId == model.id
        }
    }

    ScreenContainer(title = "Dashboard") {
        HidConnectionPanel(
            hid = hid,
            onStart = { application.hidManager.start() },
            onRefresh = { application.hidManager.refreshBondedHosts() },
            onConnect = { application.hidManager.connect(it) },
            onDisconnect = { application.hidManager.disconnect() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("API keys", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isRefreshingModels) {
                        Text(
                            text = "Updating models…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = viewModel::refreshModels,
                        enabled = uiState.configuredProviders.isNotEmpty() && !uiState.isRefreshingModels,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh models")
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    val configured = provider in uiState.configuredProviders
                    val error = uiState.providerErrors[provider]
                    AssistChip(
                        onClick = { viewModel.openApiKeySheet(provider) },
                        label = {
                            Text(
                                if (configured) {
                                    "${provider.displayName}: ${uiState.providerKeyMasks[provider] ?: "Set"}"
                                } else {
                                    "${provider.displayName}: Not set"
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                        enabled = true,
                    )
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.openApiKeySheet() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage API keys")
            }
        }

        if (uiState.statusMessage != null) {
            Text(
                text = uiState.statusMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        ExposedDropdownMenuBox(
            expanded = modelMenuExpanded,
            onExpandedChange = { expanded ->
                if (uiState.availableModels.isNotEmpty()) {
                    modelMenuExpanded = expanded
                }
            },
        ) {
            OutlinedTextField(
                value = selectedRemoteModel?.let { "${it.provider.displayName} — ${it.displayName}" }
                    ?: if (uiState.configuredProviders.isEmpty()) {
                        "Add an API key to load models"
                    } else if (uiState.isRefreshingModels) {
                        "Loading models…"
                    } else {
                        "No compatible models found"
                    },
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                enabled = uiState.availableModels.isNotEmpty(),
            )

            ExposedDropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false },
            ) {
                var lastProvider: AiProvider? = null
                uiState.availableModels.forEach { model ->
                    if (lastProvider != model.provider) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    model.provider.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        lastProvider = model.provider
                    }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName)
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            viewModel.selectModel(model)
                            modelMenuExpanded = false
                        },
                    )
                }
            }
        }

        if (selectedRemoteModel?.provider == AiProvider.ANTHROPIC) {
            SliderCard(
                title = "Effort",
                levels = effortLevels,
                value = effort,
                onValueChange = { effort = it },
            )
        }

        SliderCard(
            title = "Automatic",
            levels = automationLevels,
            value = automation,
            onValueChange = { automation = it },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Photo Resolution", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("MAX", "High", "Medium").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = photoResolution == index,
                        onClick = { photoResolution = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Button(
            onClick = { /* halts the agent loop and all HID output */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Text("  STOP", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = { /* deep-link to the system tethering screen */ },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text("  Share internet (USB tethering)")
        }
    }

    if (uiState.showApiKeySheet) {
        ApiKeySheet(
            configuredProviders = uiState.configuredProviders,
            providerKeyMasks = uiState.providerKeyMasks,
            focusedProvider = uiState.sheetProvider,
            onDismiss = viewModel::dismissApiKeySheet,
            onSave = viewModel::saveApiKey,
            onClear = viewModel::clearApiKey,
        )
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
private fun SliderCard(
    title: String,
    levels: List<SliderLevel>,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val index = value.roundToInt().coerceIn(0, levels.lastIndex)
    val level = levels[index]

    Card {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        level.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = 0f..levels.lastIndex.toFloat(),
                    steps = levels.size - 2,
                )
                Text(
                    level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}