package com.example.batterytelemetry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Runs on the emulator: draws [TelemetryScreen] with a known state and checks what appears. */
class TelemetryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsLatestReadingAndLostCount() {
        val readings = listOf(
            Reading(sequence = 1, timeMs = 0, millivolts = 3800, milliamps = 300),
            Reading(sequence = 3, timeMs = 200, millivolts = 3750, milliamps = 310),
        )
        compose.setContent {
            TelemetryScreen(
                UiState(
                    connectionState = ConnectionState.CONNECTED,
                    latest = readings.last(),
                    recent = readings,
                    lostMessages = 1,
                )
            )
        }

        compose.onNodeWithText("Status: Connected").assertIsDisplayed()
        compose.onNodeWithText("3.75 V").assertIsDisplayed()
        compose.onNodeWithText("310 mA").assertIsDisplayed()
        compose.onNodeWithText("1.16 W").assertIsDisplayed()  // 3750 mV * 310 mA = 1162 mW
        compose.onNodeWithText("Lost messages: 1").assertIsDisplayed()
    }

    @Test
    fun showsWaitingMessageBeforeFirstReading() {
        compose.setContent { TelemetryScreen(UiState()) }

        compose.onNodeWithText("Waiting for readings…").assertIsDisplayed()
        compose.onNodeWithText("Status: Disconnected").assertIsDisplayed()
    }
}
