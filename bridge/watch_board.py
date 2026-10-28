import asyncio
import sys

from protocol import decode, pack_config


async def main():
    reader, writer = await asyncio.open_connection("127.0.0.1", 5050)
    print("connected on port 5050 (fake board or BLE bridge)")

    if len(sys.argv) > 1:
        try:
            writer.write(pack_config(int(sys.argv[1])))
            await writer.drain()
            print(f"sent interval {sys.argv[1]} ms")
        except ValueError as err:  # out of range or not a number: don't send it
            print(f"not sent: {err}")

    try:
        while True:
            data = await reader.readexactly(11)
            print(decode(data))
    except asyncio.IncompleteReadError:
        print("the other side closed the connection")
    finally:
        writer.close()


if __name__ == "__main__":
    asyncio.run(main())
