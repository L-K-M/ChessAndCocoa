# Generated artwork

The ceramic chess set and cocoa-and-knight brand mark were created with
OpenAI's built-in image generator on 2026-10-18 for Chess & Cocoa.
These project assets are distributed with the app under GPL-3.0-or-later.

## Sources and production assets

- `pieces/ceramic/atlas.png`: original generated RGBA atlas, 1086 × 1448.
- `icon.png`: original generated brand mark, 1254 × 1254.
- `../app/src/main/res/drawable-nodpi/cocoa_piece_*.webp`: twelve lossless
  RGBA sprites on 320 × 320 transparent canvases.
- `../app/src/main/res/drawable-nodpi/hero_cocoa.webp` and
  `../app/src/main/res/mipmap-*/ic_launcher_foreground.png`: generated
  derivatives of the brand mark.

The model did not follow the requested atlas grid exactly. The extraction
script records explicit crop rectangles, preserves alpha, fits each type to
a shared height on both sides, and aligns every base to the same baseline.
It does not recolour or redraw the pieces. Run from the repository root:

```sh
python3 tools/pieces/build_ceramic.py
python3 tools/pieces/build_ceramic.py --check
python3 tools/icons/gen_icons.py
```

The normal board and promotion chooser use the ceramic set. High-contrast
boards and their promotion chooser, miniature resume boards, small side selectors and the monochrome
launcher icon retain the [cburnett vectors](pieces/README.md) for readability.
The original vector generator and its check remain in use.

## Chess set prompt

The requested size and grid below describe the generation prompt; the actual
source size and extraction are recorded above.

```text
Use case: stylized-concept
Asset type: a production chess-game sprite atlas for Chess & Cocoa, a calm Android teaching game.
Primary request: Create one exquisite, consistent Staunton chess piece set as TWELVE isolated transparent sprites in an exact 3-column by 4-row grid, portrait canvas 1536 x 2048 (each invisible cell 512 x 512). No visible grid, no labels.
Row 1: white KING, white QUEEN, white BISHOP.
Row 2: white KNIGHT, white ROOK, white PAWN.
Row 3: black KING, black QUEEN, black BISHOP.
Row 4: black KNIGHT, black ROOK, black PAWN.
Style: beautifully crafted softly glazed ceramic, sculptural tactile game pieces, restrained premium storybook 3D rendering, smooth rounded turned bases, clean iconic silhouettes, subtle dimensional shading. White pieces warm ivory porcelain, dark cocoa fine edge definition. Black pieces very dark espresso ceramic, readable warm highlights and defined silhouette. Same geometry in each side. Warm diffused light from upper left; elegant quiet finish, not shiny plastic.
View: straight-on orthographic elevation, only a very slight view of the elliptical tops of bases, NO isometric perspective. Knights are unmistakable elegant horse profiles facing LEFT. King has a clearly visible cross, queen a five-point coronet, bishop a deep diagonal slit in a pointed mitre, rook a broad crenellated tower, pawn a round ball head. All have broad stable circular plinths with two clean rings.
Composition: Each sprite centered horizontally within its own 512px cell, common baseline at 448px within each cell. Generous true transparent gutter, no part closer than 45 pixels to any cell edge. King height 390px, queen 365px, bishop 335px, knight 325px, rook 290px, pawn 260px. Bases about 240px wide, pawns 205px. Each cell contains exactly one entire chess piece. Consistent scale and light across all 12 pieces. Piece silhouettes must read clearly at 32 to 48px on cream and medium caramel board squares. Minimal fine detail, stronger shapes and clear large forms.
Background: actual alpha transparency, not white, not checkerboard. No floor, no board, no tile, no scenery, no detached cast shadows. No text, numbers, decorations, logos, watermark, gold trim, faces, extra pieces. Transparent areas must be genuinely transparent.
```

## Brand mark prompt

```text
Use case: stylized-concept
Asset type: final square launcher icon and small brand mark for the cozy Android game Chess & Cocoa.
Primary request: a beautifully art-directed, tactile ceramic cocoa cup and an elegant Staunton ivory knight together, sculpted and lit like premium handmade game objects.
Composition: square image, central compact grouping occupies the middle 62% of width and height so it survives circular Android cropping. On the left of the grouping is a warm ivory porcelain mug full of velvety dark hot cocoa, handle on the left; to the right and slightly behind it is a single warm ivory Staunton horse-head knight on a turned base, facing left toward the cup. Two simple delicate curls of cream steam rise from the cocoa. No other pieces. Strong clear silhouette at tiny size. Cup and knight similar scale; mug slightly lower than knight. Common grounded baseline, no plate.
Style: exquisite soft 3D ceramic illustration, warm ivory glaze, espresso cocoa, subtle warm highlights, smooth rounded forms, minimal detail, quiet and welcoming rather than ornate. Soft light from upper left. Restrained premium editorial craft.
Backdrop: rich deep dark cocoa brown, very subtle warm central glow, all four edges uniformly the same deep cocoa brown. Square corners, full bleed opaque background; no drawn rounded-square boundary or outer frame.
Constraints: no text, no lettering, no labels, no border, no watermark, no beans, no flowers, no scenery, no floating chessboard, no metallic gold, no chocolate splashes. Readable and beautiful as an app icon at 44px.
```
