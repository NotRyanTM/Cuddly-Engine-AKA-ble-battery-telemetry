import pytest
from protocol import decode, pack
def test_worked_example():
    r = decode(bytes.fromhex("010500e8030000740e2c01"))
    assert (r["seq"], r["time_ms"], r["mv"], r["ma"]) == (5, 1000, 3700, 300)

def test_wrong_length_raises():
    with pytest.raises(ValueError):
        decode(bytes(10))

def test_wrong_version_raises():
    # Same as the worked example, but the first byte (version) is 2 instead of 1
    with pytest.raises(ValueError):
        decode(bytes.fromhex("020500e8030000740e2c01"))

def test_pack_matches_worked_example():
    assert pack(5, 1000, 3700, 300).hex() == "010500e8030000740e2c01"

def test_round_trip():
    r = decode(pack(42, 123456, 3850, -15))
    assert (r["seq"], r["time_ms"], r["mv"], r["ma"]) == (42, 123456, 3850, -15)