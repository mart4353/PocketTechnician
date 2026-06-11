package com.pockettechnician.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val automationLevels = listOf("Manual", "Step", "Standard", "Autonomous")
private val effortLevels = listOf("low", "medium", "high", "max")

@Composable
fun DashboardScreen() {
    var model by rememberSaveable { mutableStateOf("claude-opus-4-8") }
    var automation by rememberSaveable { mutableFloatStateOf(0f) }
    var effort by rememberSaveable { mutableFloatStateOf(2f) }
    var photoResolution by rememberSaveable { mutableIntStateOf(0) }

    ScreenContainer(title = "Dashboard") {
        // Status row (mock values for now)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text("HID: Not connected") },
                leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
            )
            AssistChip(
                onClick = {},
                label = { Text("API key: Not set") },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            )
        }

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SliderCard(
            title = "Automation",
            levels = automationLevels,
            value = automation,
            onValueChange = { automation = it },
            description = when (automation.roundToInt()) {
                0 -> "Every single action needs a tap"
                1 -> "One approval per proposed step"
                2 -> "Safe actions run automatically; sensitive ones ask"
                else -> "Everything runs automatically except the safety gates"
            },
        )

        SliderCard(
            title = "Effort",
            levels = effortLevels,
            value = effort,
            onValueChange = { effort = it },
            description = "Maps to Claude output_config.effort",
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
}

@Composable
private fun SliderCard(
    title: String,
    levels: List<String>,
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    levels[value.roundToInt().coerceIn(0, levels.lastIndex)],
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..levels.lastIndex.toFloat(),
                steps = levels.size - 2,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
