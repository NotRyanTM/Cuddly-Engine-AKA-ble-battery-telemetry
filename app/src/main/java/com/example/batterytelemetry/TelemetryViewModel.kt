package com.example.batterytelemetry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collects readings from any [TelemetrySource] and turns them into one [UiState] for the screen:
 * the latest reading, the recent ones for the chart, and a count of lost messages.
 */
class TelemetryViewModel(
    private val source: TelemetrySource,
    private val windowMs: Long = 120_000,  // how much history the chart keeps (2 minutes)
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            source.connectionState.collect { connection ->
                _state.update { it.copy(connectionState = connection) }
            }
        }
        viewModelScope.launch {
            source.readings.collect { reading ->
                _state.update { addReading(it, reading) }
            }
        }
    }

    /** Asks the board for a new sample interval. The screen only changes if the request was sent. */
    fun setInterval(ms: Int) {
        viewModelScope.launch {
            if (source.setInterval(ms)) {
                _state.update { it.copy(intervalMs = ms) }
            }
        }
    }

    private fun addReading(state: UiState, reading: Reading): UiState {
        val lost = state.latest?.let { lostBetween(it.sequence, reading.sequence) } ?: 0
        // Keep only readings within the window, measured back from the newest one.
        val recent = (state.recent + reading).filter { reading.timeMs - it.timeMs <= windowMs }
        return state.copy(
            latest = reading,
            recent = recent,
            lostMessages = state.lostMessages + lost,
            ratePerSecond = ratePerSecond(recent),
        )
    }

    companion object {
        /**
         * How many messages were skipped between two sequence numbers.
         * The sequence wraps from 65535 back to 0, so that step counts as 1, not a huge gap.
         * A repeated number gives 0 rather than a negative count.
         */
        fun lostBetween(previous: Int, current: Int): Int =
            ((current - previous + 65536) % 65536 - 1).coerceAtLeast(0)

        /**
         * Readings per second over the last [sample] readings, from the board's own timestamps.
         * Null with fewer than 2 readings, or if they all have the same time.
         */
        fun ratePerSecond(readings: List<Reading>, sample: Int = 5): Double? {
            val last = readings.takeLast(sample)
            if (last.size < 2) return null
            val spanMs = last.last().timeMs - last.first().timeMs
            if (spanMs <= 0) return null
            return (last.size - 1) * 1000.0 / spanMs  // 5 readings = 4 gaps
        }
    }
}
