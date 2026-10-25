package com.example.batterytelemetry

import kotlin.random.Random
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTelemetrySourceTest {
    @Test
    fun voltageFallsAndCurrentStaysInRange() = runTest {
        val readings = FakeTelemetrySource(random = Random(1)).readings.take(20).toList()
        // TODO: check each voltage is <= the one before it
        // TODO: check every current is between 280 and 320
    }
}