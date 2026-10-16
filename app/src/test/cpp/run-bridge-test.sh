#!/usr/bin/env bash
# run-bridge-test.sh: host-side smoke test of the JNI streambuf bridge.
#
# Usage: app/src/test/cpp/run-bridge-test.sh
#
# Compiles bridge_test.cpp (which #includes stockfish_jni.cpp with
# COCOAFISH_NO_JNI) plus the vendored Stockfish sources into a host binary
# at build/bridge-test/bridge_test, then runs it: uci handshake, readyok, a
# bounded search to bestmove, and clean EOF after quit.
#
# Requires a fetched network in build/nnue/ (run scripts/fetch-nnue.sh) and
# any working host C++17 compiler (g++, clang++ or a compatible c++).
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
ROOT_DIR="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
SF_DIR="$ROOT_DIR/third_party/stockfish/src"
BUILD_DIR="$ROOT_DIR/build/bridge-test"

pick_cxx() {
    local cand tmp
    tmp="$(mktemp -d)"
    printf 'int main(){return 0;}\n' > "$tmp/probe.cpp"
    for cand in g++ clang++ c++; do
        if command -v "$cand" >/dev/null 2>&1 && \
           "$cand" "$tmp/probe.cpp" -o "$tmp/probe" >/dev/null 2>&1; then
            rm -rf "$tmp"
            printf '%s' "$cand"
            return 0
        fi
    done
    rm -rf "$tmp"
    return 1
}

CXX="$(pick_cxx)" || die "no working C++ compiler found"

# Same ISA profile as build-stockfish-host.sh, minus LTO: the test only
# needs a correct host binary, not a fast one.
case "$(uname -m)" in
    x86_64|amd64)
        if grep -qw avx2 /proc/cpuinfo 2>/dev/null; then
            ARCH_FLAGS="-mavx2 -mbmi -DUSE_AVX2"
        else
            ARCH_FLAGS=""
        fi
        ARCH_FLAGS="$ARCH_FLAGS -msse -msse2 -mssse3 -msse4.1 -msse3 -mpopcnt -DUSE_SSE2 -DUSE_SSSE3 -DUSE_SSE41 -DUSE_POPCNT"
        ;;
    aarch64|arm64)
        ARCH_FLAGS="-DUSE_POPCNT -DUSE_NEON=8"
        ;;
    *) die "unsupported host architecture: $(uname -m)" ;;
esac

NNUE_DIR="$ROOT_DIR/build/nnue"
mkdir -p "$BUILD_DIR"

# bridge_test.cpp pulls in stockfish_jni.cpp itself; compile only the test
# TU plus the Stockfish translation units (Makefile SRCS minus main.cpp).
# shellcheck disable=SC2086  # word-splitting of flag strings is intended
"$CXX" -std=c++17 -fno-exceptions -O2 -DNDEBUG -DIS_64BIT -pthread \
    -Wno-date-time \
    $ARCH_FLAGS \
    -I"$SF_DIR" "-I$NNUE_DIR" \
    "$SCRIPT_DIR/bridge_test.cpp" \
    "$SF_DIR/attacks.cpp" "$SF_DIR/benchmark.cpp" "$SF_DIR/bitboard.cpp" \
    "$SF_DIR/engine.cpp" "$SF_DIR/evaluate.cpp" "$SF_DIR/memory.cpp" \
    "$SF_DIR/misc.cpp" "$SF_DIR/movegen.cpp" "$SF_DIR/movepick.cpp" \
    "$SF_DIR/position.cpp" "$SF_DIR/score.cpp" "$SF_DIR/search.cpp" \
    "$SF_DIR/thread.cpp" "$SF_DIR/timeman.cpp" "$SF_DIR/tt.cpp" \
    "$SF_DIR/tune.cpp" "$SF_DIR/uci.cpp" "$SF_DIR/ucioption.cpp" \
    "$SF_DIR/nnue/nnue_accumulator.cpp" "$SF_DIR/nnue/nnue_misc.cpp" \
    "$SF_DIR/nnue/network.cpp" \
    "$SF_DIR/nnue/features/full_threats.cpp" \
    "$SF_DIR/nnue/features/half_ka_v2_hm.cpp" \
    "$SF_DIR/nnue/features/pp_3wide.cpp" \
    "$SF_DIR/syzygy/tbprobe.cpp" \
    -lpthread \
    -o "$BUILD_DIR/bridge_test"

log "Built $BUILD_DIR/bridge_test"
log "Running bridge smoke test"
"$BUILD_DIR/bridge_test"
