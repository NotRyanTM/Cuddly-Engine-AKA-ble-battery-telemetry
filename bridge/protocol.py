import struct

FORMAT = "<BHIHh"  # version, sequence, time ms, mV, mA
MESSAGE_LENGTH = 11

CONFIG_FORMAT = "<H"      # one unsigned 2-byte number, little-endian
MIN_INTERVAL_MS = 100
MAX_INTERVAL_MS = 5000
SUPPORTED_VERSION = 1

def decode(data: bytes) -> dict:
    """Turn one 11-byte telemetry message into a dict of values."""
    if len(data) != MESSAGE_LENGTH:
        raise ValueError(f"expected {MESSAGE_LENGTH} bytes, got {len(data)}")

    version, seq, time_ms, mv, ma = struct.unpack(FORMAT, data)
    if version != SUPPORTED_VERSION:
        raise ValueError(f"unsupported message version {version}, expected {SUPPORTED_VERSION}")

    return {
        "version": version,
        "seq": seq,
        "time_ms": time_ms,
        "mv": mv,
        "ma": ma,
    }

def pack(seq: int, time_ms: int, mv: int, ma: int) -> bytes:
    """Build one 11-byte telemetry message: the reverse of decode."""
    return struct.pack(FORMAT, SUPPORTED_VERSION, seq, time_ms, mv, ma)


def pack_config(interval_ms: int) -> bytes:
    """Build the 2-byte config message that sets the board's sample interval."""
    if not (MIN_INTERVAL_MS <= interval_ms <= MAX_INTERVAL_MS):
        raise ValueError(f"interval must be {MIN_INTERVAL_MS}-{MAX_INTERVAL_MS} ms, got {interval_ms}")
    return struct.pack(CONFIG_FORMAT, interval_ms)