# Piece artwork

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
