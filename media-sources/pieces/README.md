# Piece artwork

The standard board uses the generated ceramic set documented in
[ARTWORK.md](../ARTWORK.md). The vectors below remain in use for
high-contrast boards, miniature boards, small side selectors and the
monochrome launcher icon.

`*.svg` are the "cburnett" chess pieces by
[Colin M.L. Burnett](https://en.wikipedia.org/wiki/User:Cburnett), licensed
GPL-2.0-or-later (used here under GPL-3.0), as distributed in
[lichess-org/lila](https://github.com/lichess-org/lila/tree/master/public/piece/cburnett)
(`COPYING.md` there lists the set and its license). Fetched 2026-10-16,
unmodified.

`app/src/main/res/drawable/piece_*.xml` are generated from them:

```sh
python3 tools/pieces/svg2vector.py
```
