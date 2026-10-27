package com.example.batterytelemetry

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Reads 11-byte telemetry messages over TCP from bridge/fake_board.py or the BLE bridge,
 * and sends 2-byte config messages back.
 * Inside the Android emulator, 10.0.2.2 is the laptop's own 127.0.0.1.
 * If the connection drops or never opens, it waits [retryDelayMs] and tries again.
 */
class NetworkTelemetrySource(
    private val host: String = "10.0.2.2",
    private val port: Int = 5050,
    private val retryDelayMs: Long = 2000,
    private val connectTimeoutMs: Int = 3000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TelemetrySource {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // The open socket's output, so setInterval can write to it. Null when not connected.
    @Volatile private var output: OutputStream? = null

    override suspend fun setInterval(intervalMs: Int): Boolean = withContext(dispatcher) {
        val out = output ?: return@withContext false  // not connected
        try {
            out.write(PacketParser.packConfig(intervalMs))
            out.flush()  // send now instead of waiting for more bytes
            true
        } catch (e: IOException) {
            false  // the connection dropped mid-write
        }
    }

    override val readings: Flow<Reading> = flow {
        var firstAttempt = true
        try {
            while (true) {
                _connectionState.value =
                    if (firstAttempt) ConnectionState.SCANNING else ConnectionState.RECONNECTING
                firstAttempt = false
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                        _connectionState.value = ConnectionState.CONNECTED
                        output = socket.getOutputStream()
                        coroutineScope {
                            // A blocked socket read ignores coroutine cancellation. Closing the
                            // socket when the listener goes away makes readFully throw and stop.
                            // UNDISPATCHED enters the try right away; a plain launch could be
                            // cancelled before it started, skipping the finally and the close.
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    awaitCancellation()
                                } finally {
                                    output = null
                                    socket.close()
                                }
                            }
                            val input = DataInputStream(socket.getInputStream())
                            while (true) {
                                // TCP can split or join messages, so read exactly one message's worth.
                                val message = ByteArray(PacketParser.MESSAGE_LENGTH)
                                input.readFully(message)
                                emit(PacketParser.parse(message))
                            }
                        }
                    }
                } catch (e: IOException) {
                    // Refused, timed out or closed by the other side: fall through and retry.
                } catch (e: IllegalArgumentException) {
                    // A message we can't parse means we're out of step with the stream: reconnect.
                }
                _connectionState.value = ConnectionState.RECONNECTING
                delay(retryDelayMs)
            }
        } finally {
            _connectionState.value = ConnectionState.DISCONNECTED  // nobody is listening anymore
        }
    }.flowOn(dispatcher)  // sockets block, so keep them off the main thread
}