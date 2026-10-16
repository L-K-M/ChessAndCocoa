# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds libcocoafish: the unmodified Stockfish 19 sources vendored in
# third_party/stockfish (everything from the Makefile's SRCS except
# main.cpp; the universal/ dispatch sources are not used) plus
# stockfish_jni.cpp, a bridge that pipes the engine's UCI loop to Kotlin.
#
# Flags mirror `make build COMP=ndk` in the vendored Makefile:
#   arm64-v8a -> ARCH=armv8-dotprod        (every arm64 phone in scope has
#                                           NEON + dot product extensions)
#   x86_64    -> ARCH=x86-64-sse41-popcnt  (emulator only; SSE4.1 + POPCNT is
#                                           the practical Android x86-64 floor)
# Both get the optimize=yes/debug=no core: -O3 -funroll-loops -DNDEBUG
# -fno-exceptions -std=c++17 -DIS_64BIT plus clang's raised inline
# threshold. -fvisibility / exceptions settings follow upstream.
#
# The ~100 MB NNUE network is embedded by upstream's INCBIN in
# nnue/network.cpp: .incbin resolves the file name through the assembler's
# include path, so NNUE_DIR (the directory written by scripts/fetch-nnue.sh)
# is passed to it via -Wa,-I. No runtime network file is needed.

cmake_minimum_required(VERSION 3.22.1)
project(cocoafish CXX)

# One static STL is enough; the app has no other native libraries.
set(ANDROID_STL c++_static)

get_filename_component(SF_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/stockfish/src" ABSOLUTE)

if(NOT DEFINED NNUE_DIR)
    get_filename_component(NNUE_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/../../../../build/nnue" ABSOLUTE)
endif()

# The network file name is pinned by EvalFileDefaultName; fail the configure
# step with a clear message when fetch-nnue.sh has not run yet.
file(READ "${SF_DIR}/evaluate.h" EVALUATE_H)
if(NOT EVALUATE_H MATCHES "EvalFileDefaultName \"(nn-[0-9a-z]+\.nnue)\"")
    message(FATAL_ERROR "EvalFileDefaultName not found in ${SF_DIR}/evaluate.h")
endif()
set(NNUE_FILE "${NNUE_DIR}/${CMAKE_MATCH_1}")
if(NOT EXISTS "${NNUE_FILE}")
    message(FATAL_ERROR
        "NNUE network not found: ${NNUE_FILE}\n"
        "Run scripts/fetch-nnue.sh (or any Gradle task that triggers the "
        "native build) to download and verify it.")
endif()
message(STATUS "Embedding NNUE network ${NNUE_FILE}")

set(SF_SOURCES
    ${SF_DIR}/attacks.cpp
    ${SF_DIR}/benchmark.cpp
    ${SF_DIR}/bitboard.cpp
    ${SF_DIR}/engine.cpp
    ${SF_DIR}/evaluate.cpp
    ${SF_DIR}/memory.cpp
    ${SF_DIR}/misc.cpp
    ${SF_DIR}/movegen.cpp
    ${SF_DIR}/movepick.cpp
    ${SF_DIR}/position.cpp
    ${SF_DIR}/score.cpp
    ${SF_DIR}/search.cpp
    ${SF_DIR}/thread.cpp
    ${SF_DIR}/timeman.cpp
    ${SF_DIR}/tt.cpp
    ${SF_DIR}/tune.cpp
    ${SF_DIR}/uci.cpp
    ${SF_DIR}/ucioption.cpp
    ${SF_DIR}/nnue/nnue_accumulator.cpp
    ${SF_DIR}/nnue/nnue_misc.cpp
    ${SF_DIR}/nnue/network.cpp
    ${SF_DIR}/nnue/features/full_threats.cpp
    ${SF_DIR}/nnue/features/half_ka_v2_hm.cpp
    ${SF_DIR}/nnue/features/pp_3wide.cpp
    ${SF_DIR}/syzygy/tbprobe.cpp
)

add_library(cocoafish SHARED ${SF_SOURCES} stockfish_jni.cpp)

set_target_properties(cocoafish PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF)

target_include_directories(cocoafish PRIVATE ${SF_DIR})

# Upstream's optimize=yes/debug=no core flags (see src/Makefile, COMP=ndk).
target_compile_options(cocoafish PRIVATE
    -O3 -funroll-loops -fno-exceptions
    # SHELL: keeps the second -Xclang from being deduplicated away.
    "SHELL:-Xclang -mllvm -Xclang -inline-threshold=500"
    # Makes .incbin find the network in NNUE_DIR.
    "-Wa,-I,${NNUE_DIR}")

target_compile_definitions(cocoafish PRIVATE NDEBUG IS_64BIT)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # ARCH=armv8-dotprod
    target_compile_options(cocoafish PRIVATE -march=armv8.2-a+dotprod)
    target_compile_definitions(cocoafish PRIVATE
        USE_POPCNT USE_NEON=8 USE_NEON_DOTPROD ARCH=armv8-dotprod)
elseif(ANDROID_ABI STREQUAL "x86_64")
    # ARCH=x86-64-sse41-popcnt
    target_compile_options(cocoafish PRIVATE
        -msse -msse2 -mssse3 -msse4.1 -msse3 -mpopcnt)
    target_compile_definitions(cocoafish PRIVATE
        USE_SSE2 USE_SSSE3 USE_SSE41 USE_POPCNT ARCH=x86-64-sse41-popcnt)
else()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()

# Google Play requires 16 KB page-size support for native libraries; make it
# explicit instead of relying on the NDK default.
target_link_options(cocoafish PRIVATE -Wl,-z,max-page-size=16384)
