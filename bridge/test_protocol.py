import pytest
from protocol import decode, pack, pack_config

def test_worked_example():
    r = decode(bytes.fromhex("010500e8030000740e2c01"))
    assert (r["seq"], r["time_ms"], r["mv"], r["ma"]) == (5, 1000, 3700, 300)

def test_pack_config():
    assert pack_config(100).hex() == "6400"
    assert pack_config(500).hex() == "f401"
    assert pack_config(5000).hex() == "8813"

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


def test_pack_config_1000ms():
    # 1000 = 0x03E8, little-endian -> E8 03
    assert pack_config(1000) == bytes.fromhex("e803")

def test_pack_config_limits_allowed():
    assert pack_config(100) == bytes.fromhex("6400")
    assert pack_config(5000) == bytes.fromhex("8813")

def test_pack_config_out_of_range_raises():
    for bad in (99, 5001, 0, -1):
        with pytest.raises(ValueError):
            pack_config(bad)