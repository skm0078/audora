# File: scripts/fixtures/chm_fixture.py
# Purpose: Builds a small CHM (ITSF/ITSP container, LZX-compressed section) for the CHM reader's tests; used by
#          make_ebook_fixtures.py. Frames use all three LZX block kinds (verbatim, aligned offset, uncompressed) and
#          the content crosses LZX resets, so the decoder is tested where real help files exercise it.

import struct

FRAME = 0x8000
WINDOW_BITS = 16
POSITION_SLOTS = 32
MAIN_ELEMENTS = 256 + POSITION_SLOTS * 8
LENGTH_ELEMENTS = 249
RESET_FRAMES = 2  # resetInterval 0x10000 / (windowSize 0x10000 / 2) * windowsPerReset 1
MIN_MATCH, MAX_MATCH = 2, 257

EXTRA_BITS = []
_j = 0
for _i in range(0, 52, 2):
    EXTRA_BITS += [_j, _j]
    if _i != 0 and _j < 17:
        _j += 1
POSITION_BASE = [0]
for _i in range(51):
    POSITION_BASE.append(POSITION_BASE[-1] + (1 << EXTRA_BITS[_i]))


class Bits:
    """LZX's bit order: bits fill 16-bit words from the top, and each word is stored little-endian."""

    def __init__(self):
        self.out = bytearray()
        self.acc = 0
        self.count = 0

    def put(self, value, n):
        for k in range(n - 1, -1, -1):
            self.acc = (self.acc << 1) | ((value >> k) & 1)
            self.count += 1
            if self.count == 16:
                self.out += struct.pack("<H", self.acc)
                self.acc, self.count = 0, 0

    def align(self, extra_when_aligned=False):
        if self.count:
            self.put(0, 16 - self.count)
        elif extra_when_aligned:
            self.put(0, 16)


def canonical(lengths):
    codes, code = [0] * len(lengths), 0
    for length in range(1, 17):
        for sym, l in enumerate(lengths):
            if l == length:
                codes[sym] = code
                code += 1
        code <<= 1
    return codes


PRETREE = [4] * 12 + [5] * 8  # complete: 12/16 + 8/32
PRETREE_CODES = canonical(PRETREE)
MAIN = [9] * MAIN_ELEMENTS  # complete: 512 symbols of 9 bits
LENGTH = [7] * 7 + [8] * (LENGTH_ELEMENTS - 7)  # complete: 7/128 + 242/256
ALIGNED = [3] * 8


def write_lengths(bits, lengths, previous):
    for l in PRETREE:
        bits.put(l, 4)
    for new, old in zip(lengths, previous):
        z = (old - new) % 17
        bits.put(PRETREE_CODES[z], PRETREE[z])


def find_matches(data, start, end, group_start):
    """Greedy LZ77 over data[start:end]; references reach back no further than [group_start] (the last reset)."""
    table, out, i = {}, [], start
    for k in range(group_start, start - 2):
        table.setdefault(data[k:k + 3], []).append(k)
    while i < end:
        best_len, best_dist = 0, 0
        key = data[i:i + 3]
        for cand in reversed(table.get(key, [])[-32:]):
            dist = i - cand
            if dist >= (1 << WINDOW_BITS) - 3:
                break
            length = 0
            while i + length < end and length < MAX_MATCH and data[cand + length] == data[i + length]:
                length += 1
            if length > best_len:
                best_len, best_dist = length, dist
        if best_len >= 3:
            out.append(("match", best_len, best_dist))
            for k in range(i, i + best_len):
                table.setdefault(data[k:k + 3], []).append(k)
            i += best_len
        else:
            out.append(("literal", data[i]))
            table.setdefault(key, []).append(i)
            i += 1
    return out


def slot_of(formatted):
    s = 0
    while s + 1 < len(POSITION_BASE) and POSITION_BASE[s + 1] <= formatted:
        s += 1
    return s


def encode_frame(data, start, end, group_start, kind, previous, first_in_group):
    bits = Bits()
    if first_in_group:
        bits.put(0, 1)  # no Intel E8 translation
    bits.put(kind, 3)
    bits.put(end - start, 24)
    if kind == 3:  # uncompressed
        bits.align(extra_when_aligned=True)
        raw = bytes(bits.out) + struct.pack("<LLL", 1, 1, 1) + data[start:end]
        if (end - start) & 1:
            raw += b"\x00"
        return raw, previous
    if kind == 2:
        for l in ALIGNED:
            bits.put(l, 3)
    write_lengths(bits, MAIN[:256], previous[0][:256])
    write_lengths(bits, MAIN[256:], previous[0][256:])
    write_lengths(bits, LENGTH, previous[1])
    main_codes, length_codes, aligned_codes = canonical(MAIN), canonical(LENGTH), canonical(ALIGNED)
    for token in find_matches(data, start, end, group_start):
        if token[0] == "literal":
            bits.put(main_codes[token[1]], 9)
            continue
        _, length, dist = token
        formatted = dist + 2
        slot = slot_of(formatted)
        footer = formatted - POSITION_BASE[slot]
        header = min(length - MIN_MATCH, 7)
        symbol = 256 + (slot << 3) + header
        bits.put(main_codes[symbol], 9)
        if header == 7:
            ls = length - MIN_MATCH - 7
            bits.put(length_codes[ls], LENGTH[ls])
        extra = EXTRA_BITS[slot]
        if kind == 2 and extra >= 3:
            if extra > 3:
                bits.put(footer >> 3, extra - 3)
            bits.put(aligned_codes[footer & 7], 3)
        elif extra > 0:
            bits.put(footer, extra)
    bits.align()
    return bytes(bits.out), (MAIN, LENGTH)


def lzx(data):
    frames, offsets, out = (len(data) + FRAME - 1) // FRAME, [], bytearray()
    previous = None
    kinds = [1, 2, 3]
    for f in range(frames):
        start, end = f * FRAME, min(len(data), (f + 1) * FRAME)
        first = f % RESET_FRAMES == 0
        group_start = (f // RESET_FRAMES) * RESET_FRAMES * FRAME
        if first:
            previous = ([0] * MAIN_ELEMENTS, [0] * LENGTH_ELEMENTS)
        offsets.append(len(out))
        frame, previous = encode_frame(data, start, end, group_start, kinds[f % 3], previous, first)
        out += frame
    return bytes(out), offsets


def encint(value):
    groups = [value & 0x7F]
    value >>= 7
    while value:
        groups.append(0x80 | (value & 0x7F))
        value >>= 7
    return bytes(reversed(groups))


HHC = (
    '<HTML><BODY><OBJECT type="text/site properties"><param name="Window Styles" value="0x800025"></OBJECT>\r\n'
    '<UL><LI><OBJECT type="text/sitemap"><param name="Name" value="Welcome"><param name="Local" value="index.htm">'
    '</OBJECT>\r\n<UL><LI><OBJECT type="text/sitemap"><param name="Name" value="Second page">'
    '<param name="Local" value="sub/chapter2.htm#top"></OBJECT>\r\n'
    '<LI><OBJECT type="text/sitemap"><param name="Name" value="Welcome again"><param name="Local" value="index.htm">'
    '</OBJECT></UL></UL></BODY></HTML>'
)

INDEX = (
    "<html><head><title>Welcome</title></head><body><h1>Getting started</h1>"
    "<p>Press the green button to begin.</p>"
    + "".join(f"<p>Step {i}: the help file explains step {i} of the long procedure in plain words.</p>"
              for i in range(900))
    + "</body></html>"
)

CHAPTER2 = (
    '<html><head><meta http-equiv="Content-Type" content="text/html; charset=windows-1252"><title>Two</title>'
    "</head><body><h2>Second page</h2><p>Café “quoted” text.</p></body></html>"
).encode("cp1252")

ORPHAN = b"<html><body><p>A page no contents entry names.</p></body></html>"


def picture(size):
    """Bytes that do not compress (a stand-in for an image), from a fixed generator."""
    x, out = 12345, bytearray()
    for _ in range(size):
        x = (1103515245 * x + 12345) & 0x7FFFFFFF
        out.append((x >> 16) & 0xFF)
    return bytes(out)


def guid(text):
    """A GUID's bytes as Windows stores them: the first three groups little-endian, the rest as written."""
    a, b, c, d, e = text.split("-")
    return struct.pack("<LHH", int(a, 16), int(b, 16), int(c, 16)) + bytes.fromhex(d + e)


ITSF_GUIDS = guid("7C01FD10-7BAA-11D0-9E0C-00A0C922E6EC") + guid("7C01FD11-7BAA-11D0-9E0C-00A0C922E6EC")
ITSP_GUID = guid("5D02926A-212E-11D0-9DF9-00A0C922E6EC")


def build():
    compressed_files = [
        ("/toc.hhc", HHC.encode("ascii")),
        ("/index.htm", INDEX.encode("utf-8")),
        ("/images/photo.gif", picture(70000)),
        ("/sub/chapter2.htm", CHAPTER2),
        ("/zz-orphan.htm", ORPHAN),
    ]
    stream, places = bytearray(), {}
    for name, data in compressed_files:
        places[name] = (len(stream), len(data))
        stream += data
    # hhc pads the compressed stream to whole reset intervals; the reset table keeps the true length.
    interval = RESET_FRAMES * FRAME
    content, offsets = lzx(bytes(stream) + bytes(-len(stream) % interval))

    system = struct.pack("<L", 3)
    for code, text in ((0, "toc.hhc"), (2, "index.htm"), (3, "Tiny Help")):
        body = text.encode("ascii") + b"\x00"
        system += struct.pack("<HH", code, len(body)) + body
    control = struct.pack("<L", 6) + b"LZXC" + struct.pack("<LLLLL", 2, 2, 2, 1, 0)
    reset = struct.pack("<LLLLQQQ", 2, len(offsets), 8, 0x28, len(stream), len(content), FRAME)
    reset += b"".join(struct.pack("<Q", o) for o in offsets)
    transform = "::DataSpace/Storage/MSCompressed/Transform/{7FC28940-9D31-11D0-9B27-00A0C91E9C7C}/InstanceData/"
    plain_files = [
        ("/#SYSTEM", system),
        ("::DataSpace/Storage/MSCompressed/Content", content),
        ("::DataSpace/Storage/MSCompressed/ControlData", control),
        (transform + "ResetTable", reset),
    ]
    section0, entries = bytearray(), []
    for name, data in plain_files:
        entries.append((name, 0, len(section0), len(data)))
        section0 += data
    for name, data in compressed_files:
        entries.append((name, 1, *places[name]))
    entries.sort(key=lambda e: e[0].lower())

    block_len = 0x1000
    body = b"".join(encint(len(n.encode())) + n.encode() + encint(s) + encint(o) + encint(l) for n, s, o, l in entries)
    assert 0x14 + len(body) <= block_len
    free = block_len - 0x14 - len(body)
    # The free space ends with the entry count; the quickref offsets before it are optional for readers.
    pmgl = b"PMGL" + struct.pack("<LLll", free, 0, -1, -1) + body + b"\x00" * (free - 2)
    pmgl += struct.pack("<H", len(entries))

    # Version, header length, chunk size, density, depth, root index chunk (none), first and last listing chunk,
    # chunk count, language, then the GUID and the header length again (Russotto's notes, libmspack's chmd.c).
    itsp = b"ITSP" + struct.pack("<LLLLLLlLLlLL", 1, 0x54, 0x0A, block_len, 2, 1, -1, 0, 0, -1, 1, 0x409)
    itsp += ITSP_GUID + struct.pack("<Llll", 0x54, -1, -1, -1)
    assert len(itsp) == 0x54

    header_len = 0x60
    directory = itsp + pmgl
    dir_offset = header_len + 0x18
    data_offset = dir_offset + len(directory)
    # Header section 0, as hhc writes it: its own marker and the whole file's length.
    section_table = struct.pack("<LLQQ", 0x01FE, 0, data_offset + len(section0), 0)
    itsf = b"ITSF" + struct.pack("<LLLLL", 3, header_len, 1, 0, 0x409) + ITSF_GUIDS
    itsf += struct.pack("<QQQQQ", header_len, len(section_table), dir_offset, len(directory), data_offset)
    assert len(itsf) == header_len
    return itsf + section_table + directory + bytes(section0)
