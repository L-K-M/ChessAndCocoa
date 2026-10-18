#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Extract the generated ceramic atlas into transparent Android sprites.

Usage: python3 tools/pieces/build_ceramic.py [--check]
Requires: Pillow (also used by tools/icons/gen_icons.py).
"""
import argparse
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ATLAS = ROOT / "media-sources/pieces/ceramic/atlas.png"
OUTPUT = ROOT / "app/src/main/res/drawable-nodpi"
SIZE = 320
BASELINE = 300

# The generator placed rows optically, rather than on the requested grid.
# Explicit crops retain the full antialiased silhouette without a neighbour.
# Each tuple is (left, top, right, bottom), in source pixels.
CROPS = {
    "wk": (180, 12, 383, 423),
    "wq": (446, 60, 641, 424),
    "wb": (705, 80, 889, 426),
    "wn": (178, 442, 385, 755),
    "wr": (447, 478, 648, 756),
    "wp": (713, 491, 897, 757),
    "bk": (180, 759, 383, 1153),
    "bq": (443, 801, 641, 1155),
    "bb": (704, 821, 890, 1159),
    "bn": (180, 1164, 384, 1440),
    "br": (448, 1194, 648, 1440),
    "bp": (716, 1202, 896, 1442),
}
HEIGHTS = {"k": 282, "q": 264, "b": 248, "n": 238, "r": 216, "p": 192}


def sprites():
    with Image.open(ATLAS) as source:
        if source.size != (1086, 1448) or source.mode != "RGBA":
            raise ValueError("Expected the original 1086 x 1448 RGBA atlas")
        for name, bounds in CROPS.items():
            piece = source.crop(bounds)
            height = HEIGHTS[name[1]]
            width = round(piece.width * height / piece.height)
            piece = piece.resize((width, height), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (SIZE, SIZE))
            canvas.alpha_composite(piece, ((SIZE - width) // 2, BASELINE - height))
            output = BytesIO()
            canvas.save(output, "WEBP", lossless=True, exact=True, method=6)
            yield OUTPUT / f"cocoa_piece_{name}.webp", output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="check the committed sprites without writing")
    args = parser.parse_args()
    stale = []
    for path, content in sprites():
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                stale.append(path.name)
        else:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if stale:
        parser.exit(1, "Stale ceramic sprites: " + ", ".join(stale) + "\n")
    print("Ceramic sprites match the atlas." if args.check else "Wrote 12 ceramic sprites.")


if __name__ == "__main__":
    main()
