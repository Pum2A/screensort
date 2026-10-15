"""Read PE imports without installing dependency-analysis tooling (build-time only)."""

import json
import struct
import sys
from pathlib import Path


def imports(path: Path) -> set[str]:
    with path.open("rb") as stream:

        def read_at(offset: int, length: int) -> bytes:
            stream.seek(offset)
            return stream.read(length)

        if read_at(0, 2) != b"MZ":
            return set()
        pe = struct.unpack("<I", read_at(0x3C, 4))[0]
        if read_at(pe, 4) != b"PE\0\0":
            return set()
        count = struct.unpack("<H", read_at(pe + 6, 2))[0]
        optional_size = struct.unpack("<H", read_at(pe + 20, 2))[0]
        optional = pe + 24
        magic = struct.unpack("<H", read_at(optional, 2))[0]
        directory = optional + (112 if magic == 0x20B else 96)
        import_rva = struct.unpack("<I", read_at(directory + 8, 4))[0]
        sections = [
            struct.unpack("<IIII", read_at(optional + optional_size + i * 40 + 8, 16))
            for i in range(count)
        ]

        def offset(rva: int) -> int:
            for virtual_size, address, raw_size, raw_pointer in sections:
                if address <= rva < address + max(virtual_size, raw_size):
                    return raw_pointer + rva - address
            raise ValueError(f"Invalid import address in {path.name}")

        if import_rva == 0:
            return set()
        result = set()
        descriptor = offset(import_rva)
        for _ in range(4096):
            row = read_at(descriptor, 20)
            if row == b"\0" * 20:
                return result
            name_rva = struct.unpack_from("<I", row, 12)[0]
            result.add(
                read_at(offset(name_rva), 256)
                .split(b"\0", 1)[0]
                .decode("ascii")
                .lower()
            )
            descriptor += 20
        raise ValueError("Import table too large")


if __name__ == "__main__":
    root = Path(sys.argv[1])
    files = (
        [root]
        if root.is_file()
        else (
            path
            for path in root.rglob("*")
            if path.suffix.lower() in {".dll", ".pyd", ".exe"}
        )
    )
    required = set()
    for file in files:
        required.update(
            name
            for name in imports(file)
            if name.startswith(("vcruntime", "msvcp", "msvcr", "concrt"))
        )
    print(json.dumps(sorted(required)))
