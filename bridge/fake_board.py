import asyncio
import random
import time

from protocol import pack, decode_config

async def listen_for_config(reader, state):
    """Read 2-byte config messages and update the send interval."""
    while True:
        data = await reader.readexactly(2)
        try:
            state["interval_ms"] = decode_config(data)
            print(f"interval set to {state['interval_ms']} ms")
        except ValueError as err:
            print(f"ignored config: {err}")

async def handle_client(reader, writer):
    print("client connected")
    start = time.monotonic()
    seq = 0
    mv = 4200
    state = {"interval_ms": 1000}  # default from protocol.md
    listener = asyncio.create_task(listen_for_config(reader, state))
    try:
        while True:
            time_ms = int((time.monotonic() - start) * 1000)
            ma = 300 + random.randint(-20, 20)
            message = pack(seq, time_ms, mv, ma)
            writer.write(message)
            await writer.drain()

            if random.random() < 0.02:
                seq = (seq + 1) % 65536  # pretend a message was lost
            seq = (seq + 1) % 65536
            mv -= 5
            if mv < 3000:
                mv = 4200
            await asyncio.sleep(state["interval_ms"] / 1000)
    except ConnectionError:
        print("client disconnected")
    finally:
        listener.cancel()

async def main():
    server = await asyncio.start_server(handle_client, "127.0.0.1", 5050)
    print("listening on port 5050")
    await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())