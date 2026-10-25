package com.example.batterytelemetry

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each test runs a tiny TCP server on this machine in place of bridge/fake_board.py. */
class NetworkTelemetrySourceTest {

    // The worked example from docs/protocol.md: sequence 5, 1000 ms, 3700 mV, 300 mA.
    private val workedExample = byteArrayOf(1, 0x05, 0x00, 0xE8.toByte(), 0x03, 0, 0, 0x74, 0x0E, 0x2C, 0x01)

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())  // port 0 = any free port

    @After
    fun tearDown() {
        server.close()
    }

    private fun source() = NetworkTelemetrySource(
        host = "127.0.0.1",
        port = server.localPort,
        retryDelayMs = 50,
    )

    @Test
    fun readsWorkedExampleAndReportsConnected() = runBlocking {
        val testDone = CountDownLatch(1)
        thread {
            server.accept().use {
                it.getOutputStream().write(workedExample)
                testDone.await(5, TimeUnit.SECONDS)  // hold the connection open until the test checks
            }
        }
        val source = source()

        // Check the state at the moment the reading arrives, while the connection is still open.
        val (reading, stateWhenReceived) = withTimeout(5000) {
            source.readings.map { it to source.connectionState.value }.first()
        }
        testDone.countDown()

        assertEquals(Reading(sequence = 5, timeMs = 1000, millivolts = 3700, milliamps = 300), reading)
        assertEquals(ConnectionState.CONNECTED, stateWhenReceived)
    }

    @Test
    fun reportsDisconnectedOnceNobodyIsListening() = runBlocking {
        val testDone = CountDownLatch(1)
        thread {
            server.accept().use {
                it.getOutputStream().write(workedExample)
                testDone.await(5, TimeUnit.SECONDS)  // the server never hangs up on its own
            }
        }
        val source = source()

        withTimeout(5000) {
            source.readings.first()  // take one reading, then stop listening
            source.connectionState.first { it == ConnectionState.DISCONNECTED }
        }
        testDone.countDown()
    }

    @Test
    fun messageSplitAcrossTwoPacketsIsReadAsOne() = runBlocking {
        thread {
            server.accept().use { client ->
                val out = client.getOutputStream()
                out.write(workedExample, 0, 4)
                out.flush()
                Thread.sleep(100)
                out.write(workedExample, 4, 7)
                out.flush()
                Thread.sleep(500)
            }
        }

        val reading = withTimeout(5000) { source().readings.first() }

        assertEquals(5, reading.sequence)
        assertEquals(3700, reading.millivolts)
    }

    @Test
    fun reconnectsAfterServerClosesConnection() = runBlocking {
        val second = workedExample.copyOf().also { it[1] = 0x06 }  // same message, sequence 6
        thread {
            server.accept().use { it.getOutputStream().write(workedExample) }  // send one, then hang up
            server.accept().use { it.getOutputStream().write(second); Thread.sleep(500) }
        }

        val readings = withTimeout(5000) { source().readings.take(2).toList() }

        assertEquals(listOf(5, 6), readings.map { it.sequence })
    }

    @Test
    fun keepsRetryingWhileNothingIsListening() = runBlocking {
        val port = server.localPort
        server.close()  // nothing listens on this port now
        val source = NetworkTelemetrySource(host = "127.0.0.1", port = port, retryDelayMs = 50)

        withTimeout(5000) {
            val job = launch { source.readings.collect {} }
            // Passing this line means it reported RECONNECTING instead of giving up or crashing.
            source.connectionState.first { it == ConnectionState.RECONNECTING }
            job.cancel()
        }
    }
}
