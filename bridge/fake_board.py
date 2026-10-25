import asyncio
import random
import time

from protocol import pack


async def handle_client(reader, writer):
    print("client connected")
    start = time.monotonic()
    seq = 0
    mv = 4200

    try:
        while True:
            time_ms = int((time.monotonic() - start) * 1000)
            ma = 300 + random.randint(-20, 20)
            message = pack(seq, time_ms, mv, ma)
            writer.write(message)
            await writer.drain()

            seq = (seq + 1) % 65536
            mv -= 5
            if mv < 3000:
                mv = 4200
            await asyncio.sleep(0.5)
    except ConnectionError:
        print("client disconnected")


async def main():
    server = await asyncio.start_server(handle_client, "127.0.0.1", 5050)
    print("listening on port 5050")
    await server.serve_forever()


asyncio.run(main())