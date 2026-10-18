# Generated artwork

The ceramic chess set and cocoa-and-knight brand mark were created with
OpenAI's built-in image generator on 2026-10-18 for Chess & Cocoa.
These project assets are distributed with the app under GPL-3.0-or-later.

## Sources and production assets

- `pieces/ceramic/atlas-front.png`: original front-view construction
  reference, RGBA atlas, 1086 × 1448.
- `pieces/ceramic/atlas-perspective.png`: generated elevated three-quarter
  view, RGBA atlas, 1086 × 1448; production source for the dark pieces.
- `pieces/ceramic/atlas.png`: final edit with neutral white porcelain,
  RGBA atlas, 1086 × 1448; production source for the white pieces.
- `icon.png`: original generated brand mark, 1254 × 1254.
- `../app/src/main/res/drawable-nodpi/cocoa_piece_*.webp`: twelve lossless
  RGBA sprites on 320 × 320 transparent canvases.
- `../app/src/main/res/drawable-nodpi/hero_cocoa.webp` and
  `../app/src/main/res/mipmap-*/ic_launcher_foreground.png`: generated
  derivatives of the brand mark.

The final set was edited from the front-view construction reference to add
an elevated three-quarter perspective. The edit prompt requested a subtle,
almost-isometric view; its camera angles describe the requested orientation,
not measured camera properties of the generated image.

The white pieces use neutral or cool porcelain, pearl-grey shadows and
clearer edges to stand apart from the warm board squares. The dark pieces
retain their espresso ceramic finish. The colour edit also changed the dark
pieces, despite the prompt asking to preserve them. Production extraction
therefore selects only the white pieces from `atlas.png` and the dark pieces
from `atlas-perspective.png` to retain their contrast.

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
boards and their promotion chooser, miniature resume boards, small side
selectors and the monochrome launcher icon retain the
[cburnett vectors](pieces/README.md) for readability.
The original vector generator and its check remain in use.

## Chess set construction prompt

This original generation prompt records construction of `atlas-front.png`,
retained as the reference for the perspective edit. The later perspective
and white-material edits produced the production sources described above.
The requested size and grid below describe the prompt; the actual source
sizes and extraction are recorded above.

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

## Chess set perspective edit prompt

This exact edit prompt was applied to `atlas-front.png` to produce
`atlas-perspective.png`.

```text
Use case: precise-object-edit
Asset type: production chess sprite atlas for the Android game Chess & Cocoa.
Input image: edit target is the existing atlas of twelve ivory and espresso ceramic Staunton pieces. Preserve the beautiful ceramic materials, restrained warm light, color identity, piece identities and exact order.
Primary request: Give EVERY chess piece a little perspective, almost isometric. Use a consistent elevated three-quarter ORTHOGRAPHIC camera: looking down about 25 to 30 degrees, turned about 30 degrees to the side. This should be visibly more dimensional than the straight-on source: see the top elliptical surfaces of the turned bases, the top of the pawn sphere, the inside and top surfaces of rook crenellations, the upper surfaces of crowns, and a three-quarter horse head rather than a flat horse profile. Keep it subtle and playable, with upright pieces and easy-to-recognize Staunton silhouettes. All twelve pieces photographed from exactly the SAME camera and lighting.
Layout: exactly twelve isolated individual sprites, ordered in 3 columns and 4 rows on a portrait canvas. Row 1 white king, white queen, white bishop. Row 2 white knight, white rook, white pawn. Row 3 black king, black queen, black bishop. Row 4 black knight, black rook, black pawn. Each sprite must be complete and separated from every other sprite by generous true transparent gutters. Center each piece in its cell; align bases within each row. Ivory and espresso versions should share geometry and comparable scale. Maintain relative heights of king, queen, bishop, knight, rook, pawn. Knights face toward the left and slightly toward the viewer.
Invariants: ivory porcelain white side, very dark espresso ceramic black side, soft studio light from upper left, two broad turned plinth rings, king has cross, queen coronet, bishop slit mitre, rook battlements, pawn round head. No added decorations or ornament.
Background: genuinely transparent alpha throughout, no visible floor, no board, no tile, no cast shadows outside the piece, no scenery, no checkerboard, no labels, no letters, no numbers, no text, no watermark. No piece touches another or canvas edges. Make a polished coordinated game sprite atlas, not a scene or board mockup.
```

## White porcelain edit prompt

This exact edit prompt was applied to `atlas-perspective.png` to produce
`atlas.png`. Only its six white pieces are used in the production set.

```text
Use case: precise-object-edit
Asset type: final transparent chess sprite atlas for Chess & Cocoa.
Input image: edit target is the supplied elevated three-quarter ceramic Staunton atlas. The first six pieces (top two rows) are the white side; the last six (bottom two rows) are the dark side.
Primary request: Change ONLY the MATERIAL COLOR of the six WHITE chess pieces. Remove their warm yellow / golden / beige cast. Make them distinctly neutral, cool PORCELAIN WHITE, bright milk-white surfaces with soft pearl-grey and slate-grey dimensional shadows. The midtones should read white and silver-grey, not cream, tan or pale gold. This is important because these sprites will sit on light cream/tan squares and need a clear silhouette. Use controlled neutral grey edge definition and shaded undersides to separate the white porcelain from a cream board, without a cartoon black outline. Restrained soft glaze highlights; luminous white ceramic rather than metallic silver. No blue-colored pieces, no chalky flat white.
Invariants: Keep EVERY chess piece's identity, shape, geometry, position, relative scale, orientation, elevated almost-isometric three-quarter camera, clean silhouette, material texture, and transparent background unchanged. Keep all six dark espresso pieces visually unchanged; their contrast is already good. Preserve king cross, queen coronet, bishop slit, rook battlements, horse knights facing left toward viewer, spherical pawn heads, and rounded two-ring plinths. Same soft upper-left light direction across the set, but white-piece illumination should be neutral.
Layout: twelve complete isolated individual sprites in 3 columns and 4 rows. Row1 white king, queen, bishop; row2 white knight, rook, pawn; row3 dark king, queen, bishop; row4 dark knight, rook, pawn. Retain generous true transparent gutters. Do not add or remove sprites. Do not introduce floor, cast shadows beyond objects, scenery, labels, text, numbers, checkerboards, logos, or watermarks. Genuine alpha transparency throughout the background.
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
