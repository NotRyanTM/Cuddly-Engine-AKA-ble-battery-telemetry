package com.example.batterytelemetry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryViewModelTest {

    /** A source the test controls exactly: it emits only what each test sends. */
    private class TestTelemetrySource : TelemetrySource {
        val sentReadings = MutableSharedFlow<Reading>()
        val sentConnection = MutableStateFlow(ConnectionState.SCANNING)
        override val readings: Flow<Reading> = sentReadings
        override val connectionState: Flow<ConnectionState> = sentConnection
    }

    // viewModelScope runs on the Main dispatcher, which plain unit tests don't have.
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun reading(sequence: Int, timeMs: Long = sequence * 200L) =
        Reading(sequence, timeMs, millivolts = 4000, milliamps = 300)

    /** Creates the ViewModel and lets it start collecting before the test sends anything. */
    private fun TestScope.startViewModel(source: TestTelemetrySource, windowMs: Long = 120_000): TelemetryViewModel {
        val viewModel = TelemetryViewModel(source, windowMs)
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun TestScope.send(source: TestTelemetrySource, vararg readings: Reading) {
        for (r in readings) source.sentReadings.emit(r)
        advanceUntilIdle()
    }

    @Test
    fun startsEmptyThenShowsLatestReading() = runTest(dispatcher) {
        val source = TestTelemetrySource()
        val viewModel = startViewModel(source)
        assertNull(viewModel.state.value.latest)

        send(source, reading(1), reading(2))

        assertEquals(reading(2), viewModel.state.value.latest)
    }

    @Test
    fun gapInSequenceCountsAsLostMessage() = runTest(dispatcher) {
        val source = TestTelemetrySource()
        val viewModel = startViewModel(source)

        send(source, reading(1), reading(2), reading(4))  // 3 is missing

        assertEquals(1, viewModel.state.value.lostMessages)
    }

    @Test
    fun wrapFrom65535To0IsNotALoss() = runTest(dispatcher) {
        val source = TestTelemetrySource()
        val viewModel = startViewModel(source)

        send(source, reading(65534, timeMs = 0), reading(65535, timeMs = 200), reading(0, timeMs = 400))

        assertEquals(0, viewModel.state.value.lostMessages)
    }

    @Test
    fun readingsOlderThanWindowDropOut() = runTest(dispatcher) {
        val source = TestTelemetrySource()
        val viewModel = startViewModel(source, windowMs = 1000)

        send(source, reading(1, timeMs = 0), reading(2, timeMs = 900), reading(3, timeMs = 1500))

        // 1500 - 0 is past the 1000 ms window, so only the last two remain.
        assertEquals(listOf(2, 3), viewModel.state.value.recent.map { it.sequence })
    }

    @Test
    fun connectionStatePassesThrough() = runTest(dispatcher) {
        val source = TestTelemetrySource()
        val viewModel = startViewModel(source)
        assertEquals(ConnectionState.SCANNING, viewModel.state.value.connectionState)

        source.sentConnection.value = ConnectionState.CONNECTED
        advanceUntilIdle()

        assertEquals(ConnectionState.CONNECTED, viewModel.state.value.connectionState)
    }

    @Test
    fun lostBetweenHandlesNormalWrapAndRepeat() {
        assertEquals(0, TelemetryViewModel.lostBetween(5, 6))
        assertEquals(2, TelemetryViewModel.lostBetween(5, 8))
        assertEquals(0, TelemetryViewModel.lostBetween(65535, 0))
        assertEquals(1, TelemetryViewModel.lostBetween(65535, 1))
        assertEquals(0, TelemetryViewModel.lostBetween(7, 7))
    }
}
