// SPDX-License-Identifier: GPL-3.0-or-later
package ch.lkmc.chessandcocoa.chess

/**
 * Internal move engine: a flat 64-slot board indexed like [Square.index]
 * (a1 = 0 .. h8 = 63). Every function here works on a raw [Board] plus the
 * few bits of game state it needs, so legality checks run on scratch copies
 * without allocating positions. [Position] keeps its board private and
 * never mutates it after construction.
 */
internal typealias Board = Array<Piece?>

internal fun newBoard(): Board = arrayOfNulls(64)

internal const val NO_EP = -1

/** +1 towards rank 8 for White, -1 towards rank 1 for Black. */
internal val Side.forward: Int get() = if (this == Side.WHITE) 1 else -1

private val KNIGHT_DIRS = intArrayOf(1, 2, 2, 1, 2, -1, 1, -2, -1, -2, -2, -1, -2, 1, -1, 2)
private val KING_DIRS = intArrayOf(1, 1, 1, 0, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1)
private val DIAG_DIRS = intArrayOf(1, 1, 1, -1, -1, 1, -1, -1)
private val ORTHO_DIRS = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)

// Promotion order for sorting and packed-move encoding: Q, R, B, N, none.
private val PROMO_SLOTS = arrayOf(
    PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, null,
)

/**
 * Packed move: promotion slot (bits 0-2, slot 4 = none, 5 = invalid),
 * to-square (bits 3-8), from-square (bits 9-14). The layout makes plain
 * integer comparison the required deterministic order: from, to, then
 * promotion Q < R < B < N.
 */
internal fun packMove(from: Int, to: Int, promotion: PieceType?): Int {
    val slot = when (promotion) {
        null -> 4
        PieceType.QUEEN -> 0
        PieceType.ROOK -> 1
        PieceType.BISHOP -> 2
        PieceType.KNIGHT -> 3
        else -> 5
    }
    return slot or (to shl 3) or (from shl 9)
}

internal fun moveFrom(m: Int): Int = (m shr 9) and 63
internal fun moveTo(m: Int): Int = (m shr 3) and 63
internal fun movePromotion(m: Int): PieceType? = PROMO_SLOTS.getOrElse(m and 7) { null }
internal fun unpackMove(m: Int): Move = Move(Square(moveFrom(m)), Square(moveTo(m)), movePromotion(m))
internal fun Move.packed(): Int = packMove(from.index, to.index, promotion)

// ------------------------------------------------------------------
// Attack geometry. All of it is geometric: a pinned piece still attacks.
// ------------------------------------------------------------------

private fun rayAttacked(
    b: Board, f: Int, r: Int, df: Int, dr: Int, by: Side, a: PieceType, c: PieceType,
): Boolean {
    var nf = f + df
    var nr = r + dr
    while (nf in 0..7 && nr in 0..7) {
        val p = b[nr * 8 + nf]
        if (p != null) return p.side == by && (p.type == a || p.type == c)
        nf += df
        nr += dr
    }
    return false
}

/** True when a piece of [by] geometrically attacks [target]. */
internal fun isAttacked(b: Board, target: Int, by: Side): Boolean {
    val tf = target and 7
    val tr = target shr 3
    // Pawns: a White pawn attacking target sits one rank below it.
    val pr = tr - by.forward
    if (pr in 0..7) {
        if (tf > 0) {
            val p = b[pr * 8 + tf - 1]
            if (p != null && p.side == by && p.type == PieceType.PAWN) return true
        }
        if (tf < 7) {
            val p = b[pr * 8 + tf + 1]
            if (p != null && p.side == by && p.type == PieceType.PAWN) return true
        }
    }
    for (i in KNIGHT_DIRS.indices step 2) {
        val f = tf + KNIGHT_DIRS[i]
        val r = tr + KNIGHT_DIRS[i + 1]
        if (f in 0..7 && r in 0..7) {
            val p = b[r * 8 + f]
            if (p != null && p.side == by && p.type == PieceType.KNIGHT) return true
        }
    }
    for (i in KING_DIRS.indices step 2) {
        val f = tf + KING_DIRS[i]
        val r = tr + KING_DIRS[i + 1]
        if (f in 0..7 && r in 0..7) {
            val p = b[r * 8 + f]
            if (p != null && p.side == by && p.type == PieceType.KING) return true
        }
    }
    for (i in DIAG_DIRS.indices step 2) {
        if (rayAttacked(b, tf, tr, DIAG_DIRS[i], DIAG_DIRS[i + 1], by, PieceType.BISHOP, PieceType.QUEEN)) {
            return true
        }
    }
    for (i in ORTHO_DIRS.indices step 2) {
        if (rayAttacked(b, tf, tr, ORTHO_DIRS[i], ORTHO_DIRS[i + 1], by, PieceType.ROOK, PieceType.QUEEN)) {
            return true
        }
    }
    return false
}

/**
 * Squares of [by]'s pieces geometrically attacking [target], unsorted.
 * Ray walks stop at the first piece, so this is the attacker's view, not
 * x-ray; pinned pieces are included.
 */
internal fun attackersTo(b: Board, target: Int, by: Side): List<Int> {
    val tf = target and 7
    val tr = target shr 3
    val out = ArrayList<Int>(8)
    val pr = tr - by.forward
    if (pr in 0..7) {
        if (tf > 0) {
            val p = b[pr * 8 + tf - 1]
            if (p != null && p.side == by && p.type == PieceType.PAWN) out += pr * 8 + tf - 1
        }
        if (tf < 7) {
            val p = b[pr * 8 + tf + 1]
            if (p != null && p.side == by && p.type == PieceType.PAWN) out += pr * 8 + tf + 1
        }
    }
    for (i in KNIGHT_DIRS.indices step 2) {
        val f = tf + KNIGHT_DIRS[i]
        val r = tr + KNIGHT_DIRS[i + 1]
        if (f in 0..7 && r in 0..7) {
            val p = b[r * 8 + f]
            if (p != null && p.side == by && p.type == PieceType.KNIGHT) out += r * 8 + f
        }
    }
    for (i in KING_DIRS.indices step 2) {
        val f = tf + KING_DIRS[i]
        val r = tr + KING_DIRS[i + 1]
        if (f in 0..7 && r in 0..7) {
            val p = b[r * 8 + f]
            if (p != null && p.side == by && p.type == PieceType.KING) out += r * 8 + f
        }
    }
    rayAttackers(b, tf, tr, DIAG_DIRS, by, PieceType.BISHOP, out)
    rayAttackers(b, tf, tr, ORTHO_DIRS, by, PieceType.ROOK, out)
    return out
}

private fun rayAttackers(
    b: Board, f: Int, r: Int, dirs: IntArray, by: Side, type: PieceType, out: ArrayList<Int>,
) {
    for (i in dirs.indices step 2) {
        var nf = f + dirs[i]
        var nr = r + dirs[i + 1]
        while (nf in 0..7 && nr in 0..7) {
            val p = b[nr * 8 + nf]
            if (p != null) {
                if (p.side == by && (p.type == type || p.type == PieceType.QUEEN)) out += nr * 8 + nf
                break
            }
            nf += dirs[i]
            nr += dirs[i + 1]
        }
    }
}

/** Geometric attack set of the piece on [from] (empty when none). */
internal fun attackedFrom(b: Board, from: Int): Set<Int> {
    val p = b[from] ?: return emptySet()
    val f = from and 7
    val r = from shr 3
    val out = LinkedHashSet<Int>(16)
    when (p.type) {
        PieceType.PAWN -> {
            val nr = r + p.side.forward
            if (nr in 0..7) {
                if (f > 0) out += nr * 8 + f - 1
                if (f < 7) out += nr * 8 + f + 1
            }
        }
        PieceType.KNIGHT -> addSteps(b, f, r, KNIGHT_DIRS, out)
        PieceType.KING -> addSteps(b, f, r, KING_DIRS, out)
        PieceType.BISHOP -> addRays(b, f, r, DIAG_DIRS, out)
        PieceType.ROOK -> addRays(b, f, r, ORTHO_DIRS, out)
        PieceType.QUEEN -> {
            addRays(b, f, r, DIAG_DIRS, out)
            addRays(b, f, r, ORTHO_DIRS, out)
        }
    }
    return out
}

private fun addSteps(b: Board, f: Int, r: Int, dirs: IntArray, out: MutableSet<Int>) {
    for (i in dirs.indices step 2) {
        val nf = f + dirs[i]
        val nr = r + dirs[i + 1]
        if (nf in 0..7 && nr in 0..7) out += nr * 8 + nf
    }
}

private fun addRays(b: Board, f: Int, r: Int, dirs: IntArray, out: MutableSet<Int>) {
    for (i in dirs.indices step 2) {
        var nf = f + dirs[i]
        var nr = r + dirs[i + 1]
        while (nf in 0..7 && nr in 0..7) {
            out += nr * 8 + nf
            if (b[nr * 8 + nf] != null) break
            nf += dirs[i]
            nr += dirs[i + 1]
        }
    }
}

/**
 * Absolute pins on [side]: maps the pinned piece's square to the pinning
 * enemy slider's square. A pin needs exactly one [side] piece on a ray
 * between the enemy slider and [side]'s king.
 */
internal fun pinnedMap(b: Board, side: Side, kingSq: Int): Map<Int, Int> {
    if (kingSq < 0) return emptyMap()
    val kf = kingSq and 7
    val kr = kingSq shr 3
    val out = HashMap<Int, Int>(2)
    for (i in KING_DIRS.indices step 2) {
        val df = KING_DIRS[i]
        val dr = KING_DIRS[i + 1]
        val diagonal = df != 0 && dr != 0
        var first = -1
        var nf = kf + df
        var nr = kr + dr
        while (nf in 0..7 && nr in 0..7) {
            val sq = nr * 8 + nf
            val p = b[sq]
            if (p != null) {
                if (first < 0) {
                    if (p.side != side) break
                    first = sq
                } else {
                    val slider = if (diagonal) PieceType.BISHOP else PieceType.ROOK
                    if (p.side != side && (p.type == slider || p.type == PieceType.QUEEN)) {
                        out[first] = sq
                    }
                    break
                }
            }
            nf += df
            nr += dr
        }
    }
    return out
}

// ------------------------------------------------------------------
// Move generation and legality.
// ------------------------------------------------------------------

/**
 * Pseudo-legal moves for [side] as packed ints: every move that looks
 * playable before king safety. Castling is emitted on occupancy and piece
 * presence only; its check rules live in [leavesKingSafe].
 */
internal fun pseudoMoves(b: Board, side: Side, castling: CastlingRights, ep: Int): List<Int> {
    val out = ArrayList<Int>(64)
    for (from in 0..63) {
        val p = b[from] ?: continue
        if (p.side != side) continue
        val f = from and 7
        val r = from shr 3
        when (p.type) {
            PieceType.PAWN -> genPawn(b, side, ep, from, f, r, out)
            PieceType.KNIGHT -> genSteps(b, side, from, f, r, KNIGHT_DIRS, out)
            PieceType.BISHOP -> genSliders(b, side, from, f, r, DIAG_DIRS, out)
            PieceType.ROOK -> genSliders(b, side, from, f, r, ORTHO_DIRS, out)
            PieceType.QUEEN -> {
                genSliders(b, side, from, f, r, DIAG_DIRS, out)
                genSliders(b, side, from, f, r, ORTHO_DIRS, out)
            }
            PieceType.KING -> {
                genSteps(b, side, from, f, r, KING_DIRS, out)
                genCastle(b, side, castling, from, out)
            }
        }
    }
    return out
}

private val PAWN_DF = intArrayOf(-1, 1)

private fun genPawn(b: Board, side: Side, ep: Int, from: Int, f: Int, r: Int, out: ArrayList<Int>) {
    val fwd = side.forward
    val startRank = if (side == Side.WHITE) 1 else 6
    val promoRank = if (side == Side.WHITE) 6 else 1
    val one = from + 8 * fwd
    if (one in 0..63 && b[one] == null) {
        if (r == promoRank) {
            out += packMove(from, one, PieceType.QUEEN)
            out += packMove(from, one, PieceType.ROOK)
            out += packMove(from, one, PieceType.BISHOP)
            out += packMove(from, one, PieceType.KNIGHT)
        } else {
            out += packMove(from, one, null)
            val two = from + 16 * fwd
            if (r == startRank && b[two] == null) out += packMove(from, two, null)
        }
    }
    for (df in PAWN_DF) {
        val nf = f + df
        if (nf !in 0..7) continue
        val to = from + 8 * fwd + df
        if (to !in 0..63) continue
        val victim = b[to]
        if (victim != null && victim.side != side && victim.type != PieceType.KING) {
            if (r == promoRank) {
                out += packMove(from, to, PieceType.QUEEN)
                out += packMove(from, to, PieceType.ROOK)
                out += packMove(from, to, PieceType.BISHOP)
                out += packMove(from, to, PieceType.KNIGHT)
            } else {
                out += packMove(from, to, null)
            }
        } else if (to == ep && victim == null) {
            // The victim pawn stands on the mover's rank; demand it so a
            // stray en-passant square in a FEN cannot fabricate captures.
            val capSq = (from and 56) or (to and 7)
            val cap = b[capSq]
            if (cap != null && cap.side != side && cap.type == PieceType.PAWN) {
                out += packMove(from, to, null)
            }
        }
    }
}

private fun genSteps(
    b: Board, side: Side, from: Int, f: Int, r: Int, dirs: IntArray, out: ArrayList<Int>,
) {
    for (i in dirs.indices step 2) {
        val nf = f + dirs[i]
        val nr = r + dirs[i + 1]
        if (nf !in 0..7 || nr !in 0..7) continue
        val to = nr * 8 + nf
        val v = b[to]
        if (v == null || (v.side != side && v.type != PieceType.KING)) {
            out += packMove(from, to, null)
        }
    }
}

private fun genSliders(
    b: Board, side: Side, from: Int, f: Int, r: Int, dirs: IntArray, out: ArrayList<Int>,
) {
    for (i in dirs.indices step 2) {
        var nf = f + dirs[i]
        var nr = r + dirs[i + 1]
        while (nf in 0..7 && nr in 0..7) {
            val to = nr * 8 + nf
            val v = b[to]
            if (v == null) {
                out += packMove(from, to, null)
            } else {
                if (v.side != side && v.type != PieceType.KING) out += packMove(from, to, null)
                break
            }
            nf += dirs[i]
            nr += dirs[i + 1]
        }
    }
}

private fun genCastle(b: Board, side: Side, castling: CastlingRights, from: Int, out: ArrayList<Int>) {
    if (side == Side.WHITE && from == 4) {
        if (castling.whiteKingSide && b[5] == null && b[6] == null && isRook(b[7], side)) {
            out += packMove(4, 6, null)
        }
        if (castling.whiteQueenSide && b[3] == null && b[2] == null && b[1] == null && isRook(b[0], side)) {
            out += packMove(4, 2, null)
        }
    } else if (side == Side.BLACK && from == 60) {
        if (castling.blackKingSide && b[61] == null && b[62] == null && isRook(b[63], side)) {
            out += packMove(60, 62, null)
        }
        if (castling.blackQueenSide && b[59] == null && b[58] == null && b[57] == null && isRook(b[56], side)) {
            out += packMove(60, 58, null)
        }
    }
}

private fun isRook(p: Piece?, side: Side): Boolean =
    p != null && p.side == side && p.type == PieceType.ROOK

/**
 * Applies packed move [m] to [b] in place: en-passant victim removal,
 * castling rook swing and promotion. No legality checking here.
 */
internal fun applyMove(b: Board, m: Int, ep: Int) {
    val from = moveFrom(m)
    val to = moveTo(m)
    val p = b[from] ?: return
    val ff = from and 7
    val tf = to and 7
    b[from] = null
    if (p.type == PieceType.PAWN && to == ep && ff != tf && b[to] == null) {
        // En passant: the captured pawn sits on the mover's rank.
        b[(from and 56) or tf] = null
    }
    if (p.type == PieceType.KING && kotlin.math.abs(tf - ff) == 2) {
        val rookFrom = if (tf > ff) to + 1 else to - 2
        val rookTo = if (tf > ff) to - 1 else to + 1
        b[rookTo] = b[rookFrom]
        b[rookFrom] = null
    }
    b[to] = movePromotion(m)?.let { Piece(p.side, it) } ?: p
}

/**
 * True when [m] (a pseudo-legal move of [side]) leaves [side]'s king safe.
 * [kingSq] is [side]'s king square on [b] before the move.
 *
 * Castling is special: the king's start, transit and landing squares must
 * all be unattacked on the board *without the king*. With the king left in
 * place it could shield its own path; with the rook already swung the rook
 * could shield the landing square. This matches python-chess and FIDE.
 */
internal fun leavesKingSafe(b: Board, side: Side, kingSq: Int, m: Int, ep: Int): Boolean {
    val from = moveFrom(m)
    val to = moveTo(m)
    val p = b[from] ?: return false
    val enemy = side.opposite
    if (p.type == PieceType.KING && kotlin.math.abs((to and 7) - (from and 7)) == 2) {
        val s = b.copyOf()
        s[from] = null
        val mid = (from + to) / 2
        return !isAttacked(s, from, enemy) && !isAttacked(s, mid, enemy) && !isAttacked(s, to, enemy)
    }
    // Apply in place, test, revert: this runs once per candidate move, so
    // it avoids a 64-slot copy in the hottest loop of the engine.
    var capturedSq = -1
    var captured: Piece? = null
    if (p.type == PieceType.PAWN && to == ep && (from and 7) != (to and 7) && b[to] == null) {
        capturedSq = (from and 56) or (to and 7)
        captured = b[capturedSq]
    } else if (b[to] != null) {
        capturedSq = to
        captured = b[to]
    }
    b[from] = null
    if (capturedSq >= 0) b[capturedSq] = null
    b[to] = movePromotion(m)?.let { Piece(p.side, it) } ?: p
    val ksq = if (p.type == PieceType.KING) to else kingSq
    val safe = !isAttacked(b, ksq, enemy)
    b[to] = null
    b[from] = p
    if (capturedSq >= 0) b[capturedSq] = captured
    return safe
}

/** Fast path for mate/stalemate checks: is there at least one legal move? */
internal fun anyLegal(b: Board, side: Side, castling: CastlingRights, ep: Int, kingSq: Int): Boolean {
    val moves = pseudoMoves(b, side, castling, ep)
    for (m in moves) {
        if (leavesKingSafe(b, side, kingSq, m, ep)) return true
    }
    return false
}

/** True when an en-passant capture on [ep] is pseudo-legal and safe. */
internal fun hasLegalEnPassant(b: Board, side: Side, ep: Int, kingSq: Int): Boolean {
    if (ep < 0) return false
    val ef = ep and 7
    val er = ep shr 3
    val pr = er - side.forward
    if (pr !in 0..7) return false
    val capSq = pr * 8 + ef
    val cap = b[capSq]
    if (cap == null || cap.side == side || cap.type != PieceType.PAWN) return false
    for (df in PAWN_DF) {
        val f = ef + df
        if (f !in 0..7) continue
        val from = pr * 8 + f
        val p = b[from]
        if (p != null && p.side == side && p.type == PieceType.PAWN &&
            leavesKingSafe(b, side, kingSq, packMove(from, ep, null), ep)
        ) {
            return true
        }
    }
    return false
}
