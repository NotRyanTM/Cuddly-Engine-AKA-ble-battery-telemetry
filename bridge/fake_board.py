import random
import time

from protocol import pack

start = time.monotonic()
seq = 0
mv = 4200

while True:
    time_ms = int((time.monotonic() - start) * 1000)
    ma = 300 + random.randint(-20, 20)
    message = pack(seq, time_ms, mv, ma)
    print(message.hex(), len(message))
    seq = (seq + 1) % 65536
    mv -= 5
    if mv < 3000:
        mv = 4200
    time.sleep(0.5)