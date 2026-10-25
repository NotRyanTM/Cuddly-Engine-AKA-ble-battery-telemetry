import struct

FORMAT = "<BHIHh"  # version, sequence, time ms, mV, mA
MESSAGE_LENGTH = 11

def decode(data: bytes) -> dict:
    """Turn one 11-byte telemetry message into a dict of values."""
    if len(data) != MESSAGE_LENGTH:
        raise ValueError(f"expected {MESSAGE_LENGTH} bytes, got {len(data)}")

    version, seq, time_ms, mv, ma = struct.unpack(FORMAT, data)

    return {
        "version": version,
        "seq": seq,
        "time_ms": time_ms,
        "mv": mv,
        "ma": ma,
    }