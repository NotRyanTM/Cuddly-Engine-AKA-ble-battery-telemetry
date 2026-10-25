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

    override val readings: Flow<Reading> = flow {
        var sequence = startSequence
        var timeMs = 0L
        var millivolts = 4200.0

        while (true) {
            // TODO 1: emit a Reading built from the current values
            // TODO 2: move to the next values (sequence, time, voltage)
            // TODO 3: about 1 time in 50, skip an extra sequence number
            delay(intervalMs)
        }
    }

    override val connectionState: Flow<ConnectionState> = flowOf(ConnectionState.CONNECTED)
}