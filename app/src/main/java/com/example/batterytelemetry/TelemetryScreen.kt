package com.example.batterytelemetry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.batterytelemetry.ui.theme.BatteryTelemetryTheme
import java.util.Locale

/**
 * The one place to choose where readings come from:
 * - NetworkTelemetrySource(): bridge/fake_board.py or the BLE bridge on the laptop (10.0.2.2:5050)
 * - FakeTelemetrySource(): made-up readings inside the app, no laptop needed
 */
private fun createTelemetrySource(): TelemetrySource = NetworkTelemetrySource()

/** Connects the screen to a ViewModel fed by [createTelemetrySource]. */
@Composable
fun TelemetryRoute(modifier: Modifier = Modifier) {
    val viewModel: TelemetryViewModel = viewModel(
        factory = viewModelFactory { initializer { TelemetryViewModel(createTelemetrySource()) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    TelemetryScreen(state, modifier, onIntervalSelected = viewModel::setInterval)
}

/** Draws one [UiState]. It holds no logic, so previews and tests can pass in any state. */
@Composable
fun TelemetryScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onIntervalSelected: (Int) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Battery Telemetry", style = MaterialTheme.typography.headlineMedium)
        Text("Status: ${state.connectionState.label()}", style = MaterialTheme.typography.titleMedium)
        IntervalButtons(state.intervalMs, onIntervalSelected)

        val latest = state.latest
        if (latest == null) {
            Text("Waiting for readings…")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigValue("Voltage", formatVolts(latest.millivolts))
                BigValue("Current", "${latest.milliamps} mA")
                BigValue("Power", formatWatts(latest.milliwatts))
            }
            Text("Lost messages: ${state.lostMessages}")
            state.ratePerSecond?.let { Text("Rate: ${formatRate(it)}") }
            Text("Power, last 2 minutes", style = MaterialTheme.typography.titleSmall)
            PowerChart(state.recent, Modifier.fillMaxWidth().height(200.dp))
        }
    }
}

/** Fast / Normal / Slow. The selected one is filled; the others are outlined. */
@Composable
private fun IntervalButtons(selectedMs: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, ms) in listOf("Fast" to 250, "Normal" to 1000, "Slow" to 3000)) {
            if (ms == selectedMs) {
                Button(onClick = { onSelect(ms) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(ms) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun BigValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

/** A line of power over time, scaled to fit between the lowest and highest values shown. */
@Composable
private fun PowerChart(readings: List<Reading>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (readings.size < 2) return@Canvas
        val startTime = readings.first().timeMs
        val timeSpan = (readings.last().timeMs - startTime).coerceAtLeast(1)
        val minPower = readings.minOf { it.milliwatts }
        val powerSpan = (readings.maxOf { it.milliwatts } - minPower).coerceAtLeast(1)

        fun point(r: Reading) = Offset(
            x = size.width * (r.timeMs - startTime) / timeSpan,
            y = size.height * (1f - (r.milliwatts - minPower).toFloat() / powerSpan),
        )

        val path = Path().apply {
            moveTo(point(readings.first()).x, point(readings.first()).y)
            readings.drop(1).forEach { lineTo(point(it).x, point(it).y) }
        }
        drawLine(Color.Gray, Offset(0f, size.height), Offset(size.width, size.height))
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
    }
}

private fun ConnectionState.label() = when (this) {
    ConnectionState.SCANNING -> "Scanning"
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.RECONNECTING -> "Reconnecting"
    ConnectionState.DISCONNECTED -> "Disconnected"
}

internal fun formatVolts(millivolts: Int) = String.format(Locale.US, "%.2f V", millivolts / 1000.0)

internal fun formatWatts(milliwatts: Int) = String.format(Locale.US, "%.2f W", milliwatts / 1000.0)

internal fun formatRate(perSecond: Double) = String.format(Locale.US, "%.1f per second", perSecond)

@Preview(showBackground = true)
@Composable
private fun TelemetryScreenPreview() {
    val readings = (0..30).map { i ->
        Reading(sequence = i, timeMs = i * 200L, millivolts = 4200 - i * 5, milliamps = 300 + (i % 5) * 4)
    }
    BatteryTelemetryTheme {
        Surface {
            TelemetryScreen(
                UiState(
                    connectionState = ConnectionState.CONNECTED,
                    latest = readings.last(),
                    recent = readings,
                    lostMessages = 2,
                )
            )
        }
    }
}
