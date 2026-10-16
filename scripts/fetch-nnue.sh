#!/usr/bin/env bash
# fetch-nnue.sh — fetch and verify the Stockfish NNUE evaluation network.
#
# Usage: scripts/fetch-nnue.sh [-h|--help]
#
# Reads the default network name (EvalFileDefaultName) from the vendored
# Stockfish sources at third_party/stockfish and places the verified file in
# build/nnue/<name> under the repository root. Stockfish network names embed
# the first 12 hex digits of the file's SHA-256 (nn-<12 hex>.nnue); the
# downloaded file is checked against that and rejected on mismatch.
#
# A pre-seeded copy can be supplied via the NNUE_CACHE_DIR environment
# variable: when $NNUE_CACHE_DIR/<name> exists and verifies, it is copied
# into place instead of downloading. Otherwise the network is fetched from
# https://tests.stockfishchess.org/api/nn/<name>, falling back to
# https://github.com/official-stockfish/networks/raw/master/<name>.
#
# Idempotent: an existing verified build/nnue/<name> is kept as is. Exits
# non-zero when no source yields a valid file.
#
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    awk 'NR==1 && /^#!/ {next} /^#/ {sub(/^# ?/,""); print; next} {exit}' "$0"
    exit 0
fi

log()  { printf '==> %s\n' "$*"; }
info() { printf ' -- %s\n' "$*"; }
die()  { printf '!! %s\n' "$*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVALUATE_H="$ROOT_DIR/third_party/stockfish/src/evaluate.h"

if command -v sha256sum >/dev/null 2>&1; then
    SHA256="sha256sum"
elif command -v shasum >/dev/null 2>&1; then
    SHA256="shasum -a 256"
else
    die "no SHA-256 tool found (need sha256sum or shasum)"
fi

sha256_of() { $SHA256 "$1" | cut -c 1-64; }

# The net name pins its own checksum prefix: nn-<12 hex of sha256>.nnue
NET_NAME="$(sed -n 's/^#define EvalFileDefaultName "\(nn-[0-9a-f]\{12\}\.nnue\)"$/\1/p' "$EVALUATE_H")"
[ -n "$NET_NAME" ] || die "EvalFileDefaultName not found in $EVALUATE_H"
NET_HEX="$(printf '%s' "$NET_NAME" | sed -n 's/^nn-\([0-9a-f]\{12\}\)\.nnue$/\1/p')"
[ -n "$NET_HEX" ] || die "unexpected net file name: $NET_NAME"

verify_net() {
    # $1 = candidate file; succeeds only if it exists and its sha256 prefix
    # matches the 12 hex digits embedded in the file name.
    [ -f "$1" ] || return 1
    [ "$(sha256_of "$1" | cut -c 1-12)" = "$NET_HEX" ]
}

download() {
    # $1 = url, $2 = output path
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 2 --connect-timeout 30 -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$2" "$1"
    else
        die "neither curl nor wget is installed"
    fi
}

DEST_DIR="$ROOT_DIR/build/nnue"
DEST="$DEST_DIR/$NET_NAME"

if verify_net "$DEST"; then
    log "NNUE $NET_NAME already present and verified ($DEST)"
    exit 0
fi
rm -f "$DEST"  # drop a stale or corrupt copy before replacing it
mkdir -p "$DEST_DIR"

CACHE_DIR="${NNUE_CACHE_DIR:-}"
if [ -n "$CACHE_DIR" ] && verify_net "$CACHE_DIR/$NET_NAME"; then
    log "Using cached $CACHE_DIR/$NET_NAME"
    cp "$CACHE_DIR/$NET_NAME" "$DEST"
    verify_net "$DEST" || die "copied net failed verification"
    log "Verified $NET_NAME -> $DEST"
    exit 0
fi

TMP="$DEST.tmp.$$"
trap 'rm -f "$TMP"' EXIT

for url in \
    "https://tests.stockfishchess.org/api/nn/$NET_NAME" \
    "https://github.com/official-stockfish/networks/raw/master/$NET_NAME"; do
    info "Downloading $url"
    if download "$url" "$TMP" && verify_net "$TMP"; then
        mv "$TMP" "$DEST"
        log "Verified $NET_NAME -> $DEST"
        exit 0
    fi
    rm -f "$TMP"
    info "Source unusable, trying next"
done

die "could not obtain a valid $NET_NAME from any source"
