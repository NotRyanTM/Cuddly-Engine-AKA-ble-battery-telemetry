package com.example.batterytelemetry

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns one 11-byte telemetry message into a [Reading]. Mirrors bridge/protocol.py. */
object PacketParser {
    const val MESSAGE_LENGTH = 11
    const val SUPPORTED_VERSION = 1
    const val CONFIG_LENGTH = 2
    const val MIN_INTERVAL_MS = 100
    const val MAX_INTERVAL_MS = 5000

    fun parse(bytes: ByteArray): Reading {
        require(bytes.size == MESSAGE_LENGTH) { "expected $MESSAGE_LENGTH bytes, got ${bytes.size}" }

        // Each get call reads the next field in order and moves forward.
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.get().toInt() and 0xFF
        require(version == SUPPORTED_VERSION) {
            "unsupported message version $version, expected $SUPPORTED_VERSION"
        }

        // Kotlin's short and int are signed, so mask the fields that are unsigned in the protocol.
        val sequence = buf.short.toInt() and 0xFFFF
        val timeMs = buf.int.toLong() and 0xFFFFFFFFL
        val millivolts = buf.short.toInt() and 0xFFFF
        val milliamps = buf.short.toInt() // int16: keep the sign

        return Reading(sequence, timeMs, millivolts, milliamps)
    }

    /** Builds the 2-byte config message that sets the board's sample interval. Mirrors pack_config. */
    fun packConfig(intervalMs: Int): ByteArray {
        require(intervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
            "interval must be $MIN_INTERVAL_MS-$MAX_INTERVAL_MS ms, got $intervalMs"
        }
        // uint16, little-endian. Every allowed value fits in a short, so toShort() loses nothing.
        return ByteBuffer.allocate(CONFIG_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(intervalMs.toShort())
            .array()
    }
}
