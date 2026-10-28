"""Bridge between the ESP32 (Bluetooth) and the Android app (TCP port 5050).

Board --BLE notification--> bridge --TCP--> app
App   --TCP 2-byte config--> bridge --BLE write--> board
"""
import asyncio

from bleak import BleakClient, BleakScanner

from protocol import decode, decode_config

DEVICE_NAME = "BatteryTelemetry"                          # from docs/protocol.md
TELEMETRY_UUID = "f43e5ab0-61e2-4a31-8ba7-aa81c8d9102c"
CONFIG_UUID = "05eb3787-17e0-485a-8a51-b3d07c842208"
PORT = 5050

# What's connected right now: the app's TCP writer and the board's Bleak client.
state = {"app": None, "board": None}


def on_reading(sender, data: bytearray):
    """Bleak calls this for every notification from the board."""
    try:
        print(decode(bytes(data)))
    except ValueError as err:
        print(f"bad message from board: {err}")  # still forward it; the app rejects it the same way
    app = state["app"]
    if app is not None and not app.is_closing():
        app.write(bytes(data))  # raw 11 bytes, exactly as the board sent them


async def handle_app(reader, writer):
    """One app connection: read 2-byte config messages and pass them to the board."""
    print("app connected")
    state["app"] = writer
    try:
        while True:
            data = await reader.readexactly(2)
            try:
                interval = decode_config(data)  # reject bad values before they reach the board
            except ValueError as err:
                print(f"ignored config: {err}")
                continue
            board = state["board"]
            if board is None or not board.is_connected:
                print("board not connected, config dropped")
                continue
            try:
                await board.write_gatt_char(CONFIG_UUID, data, response=True)
                print(f"sent interval {interval} ms to the board")
            except Exception as err:  # the board dropped mid-write; keep serving the app
                print(f"config write failed: {err}")
    except (asyncio.IncompleteReadError, ConnectionError):
        print("app disconnected")
    finally:
        state["app"] = None
        writer.close()


async def run_board():
    """Find the board, subscribe to its readings, and reconnect if it drops."""
    while True:
        print(f"scanning for {DEVICE_NAME}...")
        try:
            device = await BleakScanner.find_device_by_name(DEVICE_NAME, timeout=10)
        except Exception as err:  # e.g. Bluetooth is switched off
            print(f"scan failed: {err}")
            await asyncio.sleep(2)
            continue
        if device is None:
            continue
        disconnected = asyncio.Event()
        try:
            async with BleakClient(device, disconnected_callback=lambda c: disconnected.set()) as client:
                state["board"] = client
                await client.start_notify(TELEMETRY_UUID, on_reading)
                print("board connected")
                await disconnected.wait()  # stay here until the board drops
        except Exception as err:
            print(f"connection failed: {err}")
        state["board"] = None
        print("board disconnected, retrying in 2 s")
        await asyncio.sleep(2)


async def main():
    server = await asyncio.start_server(handle_app, "127.0.0.1", PORT)
    print(f"waiting for the app on port {PORT}")
    async with server:
        await asyncio.gather(server.serve_forever(), run_board())


if __name__ == "__main__":
    asyncio.run(main())