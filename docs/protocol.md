# BLE Battery Telemetry Protocol (version 1)

This file is the agreement between the ESP32 board, the Python bridge and the
Android app. All three must read and write messages exactly as described here.
If two parts disagree, this file is right and the code is wrong.

## Bluetooth names

| Item | Value | What it does |
|---|---|---|
| Device name | `BatteryTelemetry` | The name the board advertises, so the bridge and app can find it |
| Service UUID | `19fbab80-3f3c-4c26-849f-bd5d6d909e73` | The board's one custom service, holding the two characteristics below |
| Telemetry characteristic | `f43e5ab0-61e2-4a31-8ba7-aa81c8d9102c` | Board → phone. Sends one reading per message using notifications |
| Config characteristic | `05eb3787-17e0-485a-8a51-b3d07c842208` | Phone → board. The phone writes a new sample interval here |

## Telemetry message (board → phone)

11 bytes, little-endian (the lowest byte of each number comes first).

| Offset | Size | Type | Field | Units / meaning |
|---|---|---|---|---|
| 0 | 1 | uint8 | version | Message format version. Always 1 for now |
| 1 | 2 | uint16 | sequence | Counts up by 1 every message; wraps from 65535 to 0. A skipped number means a lost message |
| 3 | 4 | uint32 | time | Milliseconds since the board started |
| 7 | 2 | uint16 | voltage | Millivolts (3700 = 3.700 V) |
| 9 | 2 | int16 | current | Milliamps, signed (negative means current is flowing the other way) |

Python `struct` format: `"<BHIHh"`

## Config message (phone → board)

2 bytes, little-endian.

| Offset | Size | Type | Field | Units / meaning |
|---|---|---|---|---|
| 0 | 2 | uint16 | sample interval | Milliseconds between readings. Allowed range 100 to 5000 |

The board ignores any value outside the allowed range and keeps its current interval.
Default at startup: 1000 ms (one reading per second).

## Worked example

Values: version 1, sequence 5, time 1000 ms, 3700 mV, 300 mA

Bytes (hex): `01 05 00 E8 03 00 00 74 0E 2C 01`

| Bytes | Field | Value |
|---|---|---|
| `01` | version | 1 |
| `05 00` | sequence | 5 |
| `E8 03 00 00` | time | 1000 ms |
| `74 0E` | voltage | 3700 mV |
| `2C 01` | current | 300 mA |

The board, the Python decoder and the Kotlin decoder all test against this example.

## Changes

Any change to this format must bump the version byte and update this file first.