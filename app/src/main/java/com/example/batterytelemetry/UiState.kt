package com.example.batterytelemetry

/** Everything the screen needs to draw, in one snapshot. */
data class UiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val latest: Reading? = null,              // null until the first reading arrives
    val recent: List<Reading> = emptyList(),  // readings inside the chart window, oldest first
    val lostMessages: Int = 0,                // gaps found in the sequence numbers
)
