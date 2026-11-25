"""Writes the app's settings file (a DataStore preferences protobuf) so the emulator opens on a playlist."""
import sys

def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out

def field(number, payload):
    return varint(number << 3 | 2) + varint(len(payload)) + payload

def entry(key, value):
    return field(1, field(1, key.encode()) + field(2, value))

def text(value):
    return field(5, value.encode())

def text_set(values):
    return field(6, b"".join(field(1, v.encode()) for v in values))

url, language, out = sys.argv[1], sys.argv[2], sys.argv[3]
data = (entry("playlist_url", text(url))
        + entry("saved_playlists", text_set([f"Test\t{url}"]))
        + entry("interface_language", text(language)))
open(out, "wb").write(data)
