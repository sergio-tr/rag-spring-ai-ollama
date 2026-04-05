#!/usr/bin/env python3
"""
Fix legacy Keras v3 (.keras) vocabulary assets encoded in latin-1.

TensorFlow/Keras expects TextVectorization vocab files to be UTF-8. 
"""

from __future__ import annotations

import io
import sys
import zipfile
from pathlib import Path


VOCAB_PATHS = (
    "assets/layers/text_vectorization/vocabulary.txt",
    "assets/layers/text_vectorization/_lookup_layer/vocabulary.txt",
)


def is_utf8(data: bytes) -> bool:
    try:
        data.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def latin1_to_utf8(data: bytes) -> bytes:
    return data.decode("latin-1").encode("utf-8")


def rewrite_zip(path: Path, replacements: dict[str, bytes]) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    with zipfile.ZipFile(path, "r") as zin, zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            name = info.filename
            buf = replacements.get(name)
            if buf is None:
                buf = zin.read(name)
            zi = zipfile.ZipInfo(filename=name, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zi.internal_attr = info.internal_attr
            zi.flag_bits = info.flag_bits
            zi.create_system = info.create_system
            zout.writestr(zi, buf)
    tmp.replace(path)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {argv[0]} /path/to/model.keras", file=sys.stderr)
        return 2
    path = Path(argv[1])
    if not path.exists():
        print(f"skip: {path} not found", file=sys.stderr)
        return 0

    with zipfile.ZipFile(path, "r") as z:
        missing = [p for p in VOCAB_PATHS if p not in z.namelist()]
        if missing:
            print("skip: no TextVectorization vocab assets found", file=sys.stderr)
            return 0

        replacements: dict[str, bytes] = {}
        changed = False
        for vp in VOCAB_PATHS:
            raw = z.read(vp)
            if is_utf8(raw):
                continue
            fixed = latin1_to_utf8(raw)
            if not is_utf8(fixed):
                print(f"error: {vp} could not be converted to utf-8", file=sys.stderr)
                return 1
            replacements[vp] = fixed
            changed = True

    if not changed:
        print("ok: vocab already utf-8", file=sys.stderr)
        return 0

    rewrite_zip(path, replacements)
    print("fixed: rewrote vocab assets to utf-8", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

