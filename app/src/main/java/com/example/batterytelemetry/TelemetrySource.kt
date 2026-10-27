package com.example.batterytelemetry
import kotlinx.coroutines.flow.Flow

/** Where the app's connection to the board currently stands. */
enum class ConnectionState { SCANNING, CONNECTED, RECONNECTING, DISCONNECTED }

/**
 * Anything that can supply readings: the fake source now,
 * the laptop bridge and real Bluetooth later. The screens only use this.
 */
interface TelemetrySource {
    /** Each new reading from the board, as it arrives. */
    val readings: Flow<Reading>

    /** The current connection state, plus every change to it. */
    val connectionState: Flow<ConnectionState>
    /** Ask the board to send readings every [intervalMs] ms. Returns false if not connected. */
    suspend fun setInterval(intervalMs: Int): Boolean
}