#!/usr/bin/env bash
# build-stockfish-host.sh — build a Stockfish binary for the host machine.
#
# Usage: scripts/build-stockfish-host.sh [-h|--help]
#
# Copies the vendored sources from third_party/stockfish to
# build/stockfish-host/src (never modifies third_party/), drops the verified
# NNUE network from build/nnue/ next to them so upstream's `net` step
# validates instead of downloading, and runs the upstream Makefile.
# The finished binary lands at build/stockfish-host/stockfish.
#
# ARCH selection follows the Makefile's naming: x86-64-avx2 when the host
# CPU advertises AVX2 (plain x86-64 otherwise), armv8 on arm64 Linux and
# apple-silicon on arm64 macOS.
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
BUILD_DIR="$ROOT_DIR/build/stockfish-host"

detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64)
            # /proc/cpuinfo on Linux, sysctl on macOS; AVX2 picks the fast profile.
            if grep -qw avx2 /proc/cpuinfo 2>/dev/null || \
               { [ "$(uname -s)" = "Darwin" ] && \
                 sysctl -n machdep.cpu.features machdep.cpu.leaf7_features 2>/dev/null | grep -qw AVX2; }; then
                printf 'x86-64-avx2'
            else
                printf 'x86-64'
            fi
            ;;
        aarch64|arm64)
            if [ "$(uname -s)" = "Darwin" ]; then
                printf 'apple-silicon'
            else
                printf 'armv8'
            fi
            ;;
        *) die "unsupported host architecture: $(uname -m)" ;;
    esac
}

jobs() {
    if command -v nproc >/dev/null 2>&1; then
        nproc
    elif [ "$(uname -s)" = "Darwin" ]; then
        sysctl -n hw.ncpu
    else
        printf '4'
    fi
}

pick_comp() {
    # First compiler that compiles AND links a trivial program wins; sets
    # COMP/COMPCXX for the upstream Makefile. clang is tried with a real link
    # because a present-but-broken clang++ (no working linker) must not win.
    local cand tmp
    tmp="$(mktemp -d)"
    printf 'int main(){return 0;}\n' > "$tmp/probe.cpp"
    for cand in g++ clang++ c++; do
        if command -v "$cand" >/dev/null 2>&1 && \
           "$cand" "$tmp/probe.cpp" -o "$tmp/probe" >/dev/null 2>&1; then
            rm -rf "$tmp"
            case "$cand" in
                g++) printf 'gcc' ;;
                *)   printf 'clang' ;;
            esac
            printf ' %s' "$cand"
            return 0
        fi
    done
    rm -rf "$tmp"
    return 1
}

ARCH="$(detect_arch)"
CXX_INFO="$(pick_comp)" || die "no working C++ compiler found (need g++ or clang++)"
COMP="${CXX_INFO%% *}"
COMPCXX="${CXX_INFO#* }"
log "Host build for ARCH=$ARCH ($(uname -sm)) with $COMPCXX (COMP=$COMP)"

"$SCRIPT_DIR/fetch-nnue.sh"

NET_NAME="$(sed -n 's/^#define EvalFileDefaultName "\(nn-[0-9a-f]\{12\}\.nnue\)"$/\1/p' \
    "$ROOT_DIR/third_party/stockfish/src/evaluate.h")"
[ -n "$NET_NAME" ] || die "EvalFileDefaultName not found"

# Fresh out-of-tree copy: upstream's Makefile builds in the source dir, and
# third_party/ must stay pristine.
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/src" "$BUILD_DIR/scripts"
cp -R "$ROOT_DIR/third_party/stockfish/src/." "$BUILD_DIR/src/"
cp "$ROOT_DIR/third_party/stockfish/scripts/net.sh" "$BUILD_DIR/scripts/net.sh"
cp "$ROOT_DIR/build/nnue/$NET_NAME" "$BUILD_DIR/src/$NET_NAME"

info "make -j$(jobs) build ARCH=$ARCH COMP=$COMP COMPCXX=$COMPCXX"
# RTLIB=compiler-rt skips -latomic, which bundled-toolchain shims (e.g. zig
# c++) may not ship; Stockfish needs no libatomic symbols on 64-bit hosts.
make -C "$BUILD_DIR/src" -j"$(jobs)" build ARCH="$ARCH" COMP="$COMP" \
    COMPCXX="$COMPCXX" RTLIB=compiler-rt

cp "$BUILD_DIR/src/stockfish" "$BUILD_DIR/stockfish"
log "Built $BUILD_DIR/stockfish"
