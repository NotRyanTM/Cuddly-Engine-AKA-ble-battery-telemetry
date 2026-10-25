package com.example.batterytelemetry

import kotlin.random.Random
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTelemetrySourceTest {
    @Test
    fun voltageFallsAndCurrentStaysInRange() = runTest {
        val readings = FakeTelemetrySource(random = Random(1)).readings.take(20).toList()

        for ((earlier, later) in readings.zipWithNext()) {
            assertTrue(later.millivolts <= earlier.millivolts)
        }
        for (reading in readings) {
            assertTrue(reading.milliamps in 280..320)
        }
    }

    @Test
    fun sequenceWrapsAfter65535() = runTest {
        val readings = FakeTelemetrySource(startSequence = 65534, random = Random(1))
            .readings.take(3).toList()

        assertEquals(65534, readings[0].sequence)
        for ((earlier, later) in readings.zipWithNext()) {
            val step = (later.sequence - earlier.sequence + 65536) % 65536
            assertTrue(step == 1 || step == 2)
        }
        assertTrue(readings[2].sequence in 0..2)
    }
}