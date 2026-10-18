#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Extract the generated ceramic atlases into transparent Android sprites.

Usage: python3 tools/pieces/build_ceramic.py [--check]
Requires: Pillow (also used by tools/icons/gen_icons.py).
"""
import argparse
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ATLAS = ROOT / "media-sources/pieces/ceramic/atlas.png"
ESPRESSO_ATLAS = ROOT / "media-sources/pieces/ceramic/atlas-perspective.png"
OUTPUT = ROOT / "app/src/main/res/drawable-nodpi"
SIZE = 320
BASELINE = 300

# The generator placed rows optically, rather than on the requested grid.
# Explicit crops retain the full antialiased silhouette without a neighbour.
# Each tuple is (left, top, right, bottom), in source pixels.
CROPS = {
    "wk": (179, 10, 392, 426),
    "wq": (456, 55, 667, 429),
    "wb": (747, 88, 942, 430),
    "wn": (179, 432, 399, 749),
    "wr": (466, 446, 675, 750),
    "wp": (756, 476, 947, 752),
    "bk": (178, 754, 387, 1144),
    "bq": (451, 788, 658, 1146),
    "bb": (727, 821, 921, 1152),
    "bn": (175, 1145, 386, 1436),
    "br": (449, 1155, 651, 1436),
    "bp": (737, 1186, 919, 1436),
}
HEIGHTS = {"k": 282, "q": 264, "b": 248, "n": 238, "r": 216, "p": 192}


def sprites():
    with Image.open(ATLAS) as porcelain, Image.open(ESPRESSO_ATLAS) as espresso:
        if any(source.size != (1086, 1448) or source.mode != "RGBA" for source in (porcelain, espresso)):
            raise ValueError("Expected the original 1086 x 1448 RGBA atlases")
        for name, bounds in CROPS.items():
            # The white-colour edit also brightened the dark side. Keep the
            # espresso pieces from the perspective pass, before that edit.
            source = porcelain if name[0] == "w" else espresso
            piece = source.crop(bounds)
            height = HEIGHTS[name[1]]
            width = round(piece.width * height / piece.height)
            piece = piece.resize((width, height), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (SIZE, SIZE))
            canvas.alpha_composite(piece, ((SIZE - width) // 2, BASELINE - height))
            output = BytesIO()
            canvas.save(output, "WEBP", lossless=True, exact=True, method=6)
            yield OUTPUT / f"cocoa_piece_{name}.webp", output.getvalue()


def matches_sprite(path, content):
    if not path.exists():
        return False
    with Image.open(path) as actual, Image.open(BytesIO(content)) as expected:
        # Lossless WebP encodings may differ between libwebp versions.
        return actual.size == expected.size and actual.convert("RGBA").tobytes() == expected.convert("RGBA").tobytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="check the committed sprites without writing")
    args = parser.parse_args()
    stale = []
    for path, content in sprites():
        if args.check:
            if not matches_sprite(path, content):
                stale.append(path.name)
        else:
            OUTPUT.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if stale:
        parser.exit(1, "Stale ceramic sprites: " + ", ".join(stale) + "\n")
    print("Ceramic sprites match the atlases." if args.check else "Wrote 12 ceramic sprites.")


if __name__ == "__main__":
    main()
