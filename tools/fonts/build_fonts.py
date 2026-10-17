#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the app's static fonts from the variable sources in media-sources/fonts.

Static instances render the same everywhere (including Robolectric screenshot
tests) and, subset to Latin, keep the APK small. Characters outside the subset
fall back to the system font.

    Fraunces         headings   opsz 28, SOFT 50, WONK 0; weights 500 and 600
    Atkinson Next    body text  weights 400, 500 and 600

Usage: python3 tools/fonts/build_fonts.py [--check]
Requires: fontTools (pip install fonttools).
"""
import io
import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = ROOT / "media-sources" / "fonts"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "font"

FRAUNCES = SOURCES / "Fraunces[SOFT,WONK,opsz,wght].ttf"
ATKINSON = SOURCES / "AtkinsonHyperlegibleNext[wght].ttf"

# Optical size tuned for 22-36 sp headings; half-soft corners, no leaning glyphs.
HEADING_AXES = {"opsz": 28, "SOFT": 50, "WONK": 0}

INSTANCES = {
    "fraunces_medium.ttf": (FRAUNCES, {**HEADING_AXES, "wght": 500}),
    "fraunces_semibold.ttf": (FRAUNCES, {**HEADING_AXES, "wght": 600}),
    "atkinson_regular.ttf": (ATKINSON, {"wght": 400}),
    "atkinson_medium.ttf": (ATKINSON, {"wght": 500}),
    "atkinson_semibold.ttf": (ATKINSON, {"wght": 600}),
}

# Basic Latin through Latin Extended-A, general punctuation, currency, arrows,
# the minus sign and geometric shapes.
UNICODES = "U+0020-017F,U+2000-206F,U+20A0-20CF,U+2190-21FF,U+2212,U+25A0-25FF"


def build(source, axes):
    font = instancer.instantiateVariableFont(TTFont(source), axes, static=True)
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=subset.parse_unicodes(UNICODES))
    subsetter.subset(font)

    # A fixed timestamp keeps the output byte-for-byte reproducible.
    font.recalcTimestamp = False
    font["head"].modified = font["head"].created
    buffer = io.BytesIO()
    font.save(buffer, reorderTables=True)
    return buffer.getvalue()


def main():
    check = "--check" in sys.argv[1:]
    stale = []
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, (source, axes) in INSTANCES.items():
        data = build(source, axes)
        target = OUTPUT / name
        if check:
            if not target.exists() or target.read_bytes() != data:
                stale.append(name)
            continue
        target.write_bytes(data)
        print(f"wrote {target.relative_to(ROOT)} ({len(data) // 1024} KB)")

    if stale:
        print("stale fonts, run tools/fonts/build_fonts.py: " + ", ".join(stale), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
