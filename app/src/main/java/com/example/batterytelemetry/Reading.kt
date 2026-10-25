package com.example.batterytelemetry

/** One telemetry reading from the board, decoded from an 11-byte message (see docs/protocol.md). */
data class Reading(
    val sequence: Int,   // 0 to 65535, wraps back to 0
    val timeMs: Long,    // milliseconds since the board started (Long: uint32 can exceed Int)
    val millivolts: Int,
    val milliamps: Int,  // signed: negative means current flows the other way
) {
    val milliwatts: Int
        get() = millivolts * milliamps / 1000
}
