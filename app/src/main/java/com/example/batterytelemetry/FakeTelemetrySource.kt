package com.example.batterytelemetry

import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Pretend board: a battery that slowly drains, with a message lost now and then. */
class FakeTelemetrySource(
    private val intervalMs: Long = 200,
    private val startSequence: Int = 0,
    private val random: Random = Random.Default,
) : TelemetrySource {

    /** The last interval set, so tests can check it. */
    var lastInterval: Int? = null
        private set

    override val readings: Flow<Reading> = flow {
        var sequence = startSequence
        var timeMs = 0L
        var millivolts = 4200.0

        while (true) {
            // 1. Send the current reading to whoever is listening.
            val milliamps = 300 + random.nextInt(-20, 21)
            emit(Reading(sequence, timeMs, millivolts.toInt(), milliamps))

            // 2. Move to the next values.
            sequence = (sequence + 1) % 65536
            timeMs += intervalMs
            millivolts = (millivolts - 0.5).coerceAtLeast(3000.0)

            // 3. About 1 time in 50, skip a number to fake a lost message.
            if (random.nextInt(50) == 0) {
                sequence = (sequence + 1) % 65536
            }
            delay(intervalMs)
        }
    }

    override val connectionState: Flow<ConnectionState> = flowOf(ConnectionState.CONNECTED)

    override suspend fun setInterval(intervalMs: Int): Boolean {
        lastInterval = intervalMs
        return true
    }
}