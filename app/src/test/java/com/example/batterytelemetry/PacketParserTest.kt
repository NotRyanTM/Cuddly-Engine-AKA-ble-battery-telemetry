package com.example.batterytelemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PacketParserTest {
    // Builds a byte array from hex like "010500e8030000740e2c01".
    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun parsesWorkedExample() {
        val r = PacketParser.parse(hex("010500e8030000740e2c01"))
        assertEquals(5, r.sequence)
        assertEquals(1000L, r.timeMs)
        assertEquals(3700, r.millivolts)
        assertEquals(300, r.milliamps)
    }

    @Test
    fun parsesNegativeCurrent() {
        // Same as the worked example, but current is d4 fe = -300 mA
        val r = PacketParser.parse(hex("010500e8030000740ed4fe"))
        assertEquals(-300, r.milliamps)
    }

    @Test
    fun parsesLargestUnsignedValues() {
        // sequence 65535, time 4294967295 ms, 65535 mV: would come out negative without the masks
        val r = PacketParser.parse(hex("01ffffffffffffffff0000"))
        assertEquals(65535, r.sequence)
        assertEquals(4294967295L, r.timeMs)
        assertEquals(65535, r.millivolts)
        assertEquals(0, r.milliamps)
    }

    @Test
    fun rejectsWrongLength() {
        assertThrows(IllegalArgumentException::class.java) { PacketParser.parse(ByteArray(10)) }
        assertThrows(IllegalArgumentException::class.java) { PacketParser.parse(ByteArray(12)) }
    }

    @Test
    fun rejectsWrongVersion() {
        // Same as the worked example, but the first byte (version) is 2 instead of 1
        assertThrows(IllegalArgumentException::class.java) {
            PacketParser.parse(hex("020500e8030000740e2c01"))
        }
    }

    @Test
    fun computesPower() {
        val r = PacketParser.parse(hex("010500e8030000740e2c01"))
        assertEquals(1110, r.milliwatts) // 3700 mV x 300 mA = 1110 mW
    }
}
