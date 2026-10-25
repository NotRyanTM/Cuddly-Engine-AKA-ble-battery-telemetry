# Cuddly-Engine-AKA-ble-battery-telemetry

[![tests](https://github.com/NotRyanTM/Cuddly-Engine-AKA-ble-battery-telemetry/actions/workflows/tests.yml/badge.svg)](https://github.com/NotRyanTM/Cuddly-Engine-AKA-ble-battery-telemetry/actions/workflows/tests.yml)

An ESP32 sensor board that measures a battery's voltage and current and sends the readings over Bluetooth Low Energy to an Android app written in Kotlin. Until the board arrives, a Python fake board stands in for it over TCP. The message format is in [docs/protocol.md](docs/protocol.md).

## Running the tests

Python (from the `bridge` folder):

```bash
python -m pytest
```

Kotlin unit tests (from the repository root):

```bash
./gradlew testDebugUnitTest
```

GitHub Actions runs both on every push.
