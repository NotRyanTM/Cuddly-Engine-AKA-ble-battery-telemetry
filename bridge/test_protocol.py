import pytest
from protocol import decode

def test_worked_example():
    r = decode(bytes.fromhex("010500e8030000740e2c01"))
    assert (r["seq"], r["time_ms"], r["mv"], r["ma"]) == (5, 1000, 3700, 300)

def test_wrong_length_raises():
    with pytest.raises(ValueError):
        decode(bytes(10))