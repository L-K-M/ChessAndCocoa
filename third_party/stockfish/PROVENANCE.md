# Stockfish provenance

- **Upstream**: https://github.com/official-stockfish/Stockfish
- **Tag**: `sf_19` (Stockfish 19)
- **Source tarball**: `sf_19.tar.gz` from the upstream GitHub release/tag archive
- **Tarball SHA-256**: `519b653d0d1ffb96531d982ccbe5c6a19425e8388e0e3c2f70f34b424ab32d76`
- **Vendored on**: 2026-10-16

All files under `src/`, plus `Copying.txt`, `AUTHORS`, `README.md` and
`scripts/net.sh`, are **unmodified** copies of the tagged upstream release.
Stockfish is licensed GPL-3.0-or-later (see `Copying.txt`); Chess & Cocoa is
licensed the same way, so the in-process linkage is licence-compatible.

## Neural network

Stockfish needs one NNUE evaluation network. Its file name is pinned in
`src/evaluate.h` (`EvalFileDefaultName`, currently `nn-1a298aa575a0.nnue`);
the first 12 hex digits of the file's SHA-256 equal the digits in the name.

The ~100 MB `.nnue` file is **not** committed (`*.nnue` is gitignored).
`scripts/fetch-nnue.sh` fetches it from
`https://tests.stockfishchess.org/api/nn/<name>` (falling back to
`https://github.com/official-stockfish/networks/raw/master/<name>`), verifies
the hash, and stores it in the gitignored `build/nnue/` directory. Both the
host build (`scripts/build-stockfish-host.sh`) and the Android CMake build
consume the verified copy from there; for Android the network is compiled
into `libcocoafish.so` via upstream's INCBIN mechanism, so no runtime file is
needed on the device.
