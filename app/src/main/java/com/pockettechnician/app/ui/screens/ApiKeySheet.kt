package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pockettechnician.app.data.ai.AiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySheet(
    configuredProviders: Set<AiProvider>,
    providerKeyMasks: Map<AiProvider, String>,
    focusedProvider: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider, String) -> Unit,
    onClear: (AiProvider) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val providers = if (focusedProvider != null) listOf(focusedProvider) else AiProvider.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (focusedProvider != null) {
                    "${focusedProvider.displayName} API key"
                } else {
                    "API keys"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Keys are stored encrypted on this device and sent only to the selected provider.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            providers.forEachIndexed { index, provider ->
                ProviderKeySection(
                    provider = provider,
                    isConfigured = provider in configuredProviders,
                    maskedKey = providerKeyMasks[provider],
                    onSave = onSave,
                    onClear = onClear,
                )
                if (index < providers.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProviderKeySection(
    provider: AiProvider,
    isConfigured: Boolean,
    maskedKey: String?,
    onSave: (AiProvider, String) -> Unit,
    onClear: (AiProvider) -> Unit,
) {
    var draftKey by rememberSaveable(provider.name) { mutableStateOf("") }
    var showKey by rememberSaveable(provider.name + "_visible") { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isConfigured) maskedKey ?: "Set" else "Not set",
                style = MaterialTheme.typography.bodySmall,
                color = if (isConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        OutlinedTextField(
            value = draftKey,
            onValueChange = { draftKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${provider.displayName} API key") },
            placeholder = { Text("Paste key here") },
            singleLine = true,
            visualTransformation = if (showKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showKey) "Hide API key" else "Show API key",
                    )
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSave(provider, draftKey) },
                enabled = draftKey.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = {
                    draftKey = ""
                    onClear(provider)
                },
                enabled = isConfigured,
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear")
            }
        }
    }
}