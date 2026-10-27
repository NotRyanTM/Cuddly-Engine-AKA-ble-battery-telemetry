import asyncio
import sys

from protocol import decode, pack_config


async def main():
    reader, writer = await asyncio.open_connection("127.0.0.1", 5050)
    print("connected to fake board")

    if len(sys.argv) > 1:
        writer.write(pack_config(int(sys.argv[1])))
        await writer.drain()
        print(f"sent interval {sys.argv[1]} ms")

    try:
        while True:
            data = await reader.readexactly(11)
            print(decode(data))
    except asyncio.IncompleteReadError:
        print("fake board closed the connection")
    finally:
        writer.close()


if __name__ == "__main__":
    asyncio.run(main())
