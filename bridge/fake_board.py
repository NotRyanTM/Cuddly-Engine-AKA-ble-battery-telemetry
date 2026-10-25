import random
import time

start = time.monotonic()
seq = 0
mv = 4200

while True:
    time_ms = int((time.monotonic() - start) * 1000)
    ma = 300 + random.randint(-20, 20)
    print(f"seq={seq}  time={time_ms} ms  voltage={mv} mV  current={ma} mA")

    seq += 1
    mv -= 5
    if mv < 3000:
        mv = 4200
    time.sleep(0.5)

    