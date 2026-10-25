import asyncio

from protocol import decode


async def main():
    reader, writer = await asyncio.open_connection("127.0.0.1", 5050)
    print("connected to fake board")

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
