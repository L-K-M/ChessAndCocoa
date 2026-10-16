#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate cross-check fixtures for the chess module's JVM tests.

Plays seeded random games (plus games from rule-heavy seed positions),
samples positions, and records everything the Kotlin side must reproduce:
legal UCI moves, SAN of every move, check/mate/stalemate flags, material,
en-passant state, absolute pins with their pinners, and attacker maps for
a handful of squares. A separate "games" section records scripted and
searched sequences whose expected Game.outcome() is repetition, the
fifty-move rule, mate, stalemate or material draw.

Outcome semantics follow the Kotlin KDoc, not python-chess Board.outcome:
draws by threefold repetition and the fifty-move rule are automatic, and
checkmate is reported before any draw rule (the last two differ from
python-chess, which needs claim_draw=True and still reports outcomes the
JVM side deliberately does not mirror).

Usage:  python3 tools/chessfixtures/gen_fixtures.py [output]
Default output: chess/src/test/resources/fixtures.json
Requires python-chess 1.11.x.
"""

import json
import random
import sys
from collections import Counter

import chess

SEED = 0xC0C0A
PIECE_VALUES = {
    chess.PAWN: 100,
    chess.KNIGHT: 300,
    chess.BISHOP: 300,
    chess.ROOK: 500,
    chess.QUEEN: 900,
    chess.KING: 0,
}

# Rule-heavy seeds: full castling rights, en-passant-rich files, a hanging
# pin, a near-promotion endgame and the en-passant horizontal-pin classic.
SEED_FENS = [
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "8/8/8/KPp4r/8/8/8/7k w - c6 0 1",
    "8/2P5/8/8/4k3/8/6pp/4K3 w - - 0 1",
    "4k3/8/8/3r4/4B3/8/8/4K3 w - - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
    "4r1k1/8/8/8/8/8/4B3/4K3 w - - 0 1",
]

# Sparse endgames for material and pin coverage, plus a mate and a
# stalemate for the flag fields.
ENDGAME_FENS = [
    "8/8/8/8/8/8/3k4/5K2 w - - 0 1",
    "8/8/8/8/8/8/3k4/5KN1 w - - 0 1",
    "8/8/8/8/8/8/3kb3/6K1 w - - 0 1",
    "8/8/8/8/8/2b5/3k4/6KB w - - 0 1",
    "4k3/8/8/8/8/8/4n3/4K3 w - - 0 1",
    "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
    "k7/8/1Q6/8/8/8/8/K7 b - - 0 1",
]

ATTACK_SAMPLE = ["d4", "e4", "d5", "e5"]


def pinner_map(board, color):
    """Pinned squares of `color` mapped to the enemy slider pinning them."""
    out = {}
    king = board.king(color)
    for sq, piece in sorted(board.piece_map().items()):
        if piece.color != color or not board.is_pinned(color, sq):
            continue
        # board.pin() reports the whole line; walk from the pinned piece
        # away from the king to find the actual slider.
        step_f = sign(chess.square_file(sq) - chess.square_file(king))
        step_r = sign(chess.square_rank(sq) - chess.square_rank(king))
        f, r = chess.square_file(sq) + step_f, chess.square_rank(sq) + step_r
        while 0 <= f <= 7 and 0 <= r <= 7:
            other = board.piece_at(chess.square(f, r))
            if other is not None:
                if other.color != color:
                    out[chess.square_name(sq)] = chess.square_name(chess.square(f, r))
                break
            f += step_f
            r += step_r
    return out


def sign(n):
    return (n > 0) - (n < 0)


def material(board, color):
    return sum(
        PIECE_VALUES[p.piece_type] for p in board.piece_map().values() if p.color == color
    )


def record_position(board, rng):
    squares = set(ATTACK_SAMPLE)
    squares.add(chess.square_name(board.king(chess.WHITE)))
    squares.add(chess.square_name(board.king(chess.BLACK)))
    while len(squares) < 8:
        squares.add(chess.square_name(rng.randrange(64)))
    moves = sorted(m.uci() for m in board.legal_moves)
    return {
        "fen": board.fen(en_passant="fen"),
        "moves": moves,
        "sans": {m: board.san(chess.Move.from_uci(m)) for m in moves},
        "check": board.is_check(),
        "checkmate": board.is_checkmate(),
        "stalemate": board.is_stalemate(),
        "insufficient": board.is_insufficient_material(),
        "ep": chess.square_name(board.ep_square) if board.ep_square is not None else None,
        "ep_legal": board.has_legal_en_passant(),
        "halfmove": board.halfmove_clock,
        "fullmove": board.fullmove_number,
        "material": {"white": material(board, chess.WHITE), "black": material(board, chess.BLACK)},
        "pinned": {
            "white": pinner_map(board, chess.WHITE),
            "black": pinner_map(board, chess.BLACK),
        },
        # Attacker lists are kept in square-index order (a1 -> h8) to match
        # the Kotlin Position.attackers() ordering.
        "attackers": {
            s: {
                "white": [
                    chess.square_name(t)
                    for t in sorted(board.attackers(chess.WHITE, chess.parse_square(s)))
                ],
                "black": [
                    chess.square_name(t)
                    for t in sorted(board.attackers(chess.BLACK, chess.parse_square(s)))
                ],
            }
            for s in sorted(squares)
        },
    }


def spec_outcome(board, key_counts):
    """Outcome under the Kotlin Game.outcome rules (see module docstring)."""
    if board.is_checkmate():
        return {"winner": "black" if board.turn == chess.WHITE else "white",
                "reason": "CHECKMATE"}
    if board.is_stalemate():
        return {"winner": None, "reason": "STALEMATE"}
    if board.is_insufficient_material():
        return {"winner": None, "reason": "INSUFFICIENT_MATERIAL"}
    if board.halfmove_clock >= 100:
        return {"winner": None, "reason": "FIFTY_MOVE_RULE"}
    if key_counts[board._transposition_key()] >= 3:
        return {"winner": None, "reason": "THREEFOLD_REPETITION"}
    return None


def record_game(fen, uci_moves):
    """Replay a move list, reporting the first ply an outcome applies."""
    board = chess.Board(fen)
    key_counts = Counter()
    key_counts[board._transposition_key()] += 1
    first_ply = None
    outcome = None
    for i, uci in enumerate(uci_moves, start=1):
        board.push_uci(uci)
        key_counts[board._transposition_key()] += 1
        if first_ply is None:
            outcome = spec_outcome(board, key_counts)
            if outcome is not None:
                first_ply = i
    return {
        "initial_fen": fen,
        "moves": uci_moves,
        "first_outcome_ply": first_ply,
        "outcome": outcome,
    }


def random_game_positions(rng, fen, max_plies, sample_every):
    board = chess.Board(fen)
    out = []
    for ply in range(max_plies):
        if ply % sample_every == 0:
            out.append(record_position(board, rng))
        moves = list(board.legal_moves)
        if not moves:
            break
        board.push(rng.choice(moves))
    return out


def gen_fifty_move_game(rng):
    """Find a legal 100-halfmove sequence with no third repetition.

    Random walks over reversible moves in a roomy piece set; retries until
    the halfmove clock reaches 100 before any key occurs three times.
    """
    for _ in range(20000):
        board = chess.Board("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1")
        key_counts = Counter([board._transposition_key()])
        uci_moves = []
        while board.halfmove_clock < 100:
            moves = [m for m in board.legal_moves if not board.is_capture(m)]
            if not moves:
                break
            rng.shuffle(moves)
            picked = None
            for m in moves:
                board.push(m)
                if key_counts[board._transposition_key()] < 2:
                    picked = m
                    break
                board.pop()
            if picked is None:
                break
            uci_moves.append(picked.uci())
            key_counts[board._transposition_key()] += 1
            if board.is_checkmate() or board.is_stalemate():
                break
        if board.halfmove_clock >= 100:
            return uci_moves
    raise RuntimeError("no fifty-move sequence found")


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "chess/src/test/resources/fixtures.json"
    rng = random.Random(SEED)
    positions = []

    for i in range(32):
        positions += random_game_positions(rng, chess.STARTING_FEN, 80, 3)
    for fen in SEED_FENS:
        positions += random_game_positions(rng, fen, 60, 3)
        positions.append(record_position(chess.Board(fen), rng))
    for fen in ENDGAME_FENS:
        positions.append(record_position(chess.Board(fen), rng))

    games = [
        # Knight shuffle: the start position recurs on plies 0, 4, 8.
        record_game(chess.STARTING_FEN,
                    ["g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8"]),
        # Fool's mate.
        record_game(chess.STARTING_FEN, ["f2f3", "e7e5", "g2g4", "d8h4"]),
        # Immediate stalemate after the queen move.
        record_game("k7/8/8/1Q6/8/8/8/K7 w - - 0 1", ["b5b6"]),
        # Capturing the last pawn leaves bare kings.
        record_game("8/8/8/8/8/2k5/4p3/4K3 w - - 0 1", ["e1e2"]),
        # Fifty-move rule found by random search.
        record_game("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1", gen_fifty_move_game(rng)),
    ]

    fixtures = {"positions": positions, "games": games}
    with open(out_path, "w") as f:
        json.dump(fixtures, f, separators=(",", ":"))
        f.write("\n")
    print(f"{len(positions)} positions, {len(games)} games -> {out_path}")


if __name__ == "__main__":
    main()
