# Changelog

## Unreleased

- Redesign the interface: a warm daylight theme and an evening theme
  that follow the system, bundled Fraunces and Atkinson Hyperlegible Next
  fonts, a framed board whose last move slides into place, a calmer
  coaching panel, numbered move pairs, and "Show me why" replayed on the
  main board with a clearly marked frame.
- Home shows the saved game with its position, and new games are set up
  in one sheet. The review leads with the result and lays out moments and
  plans; settings are grouped.
- Make Gentle weaker: shallower selection, more candidate moves, and more
  frequent bounded concessions, including missed defenses. Player
  assessment stays at full strength.
- Add a rook plan that uses an open file to reach a pawn, with recorded
  intentions, move-impact feedback and hints. Occupying a file alone does
  not count as success; the plan must win material.

## 0.1.0

- First playable app: untimed games against Gentle, Steady and Keen
  opponents, with Stockfish 19 running in-process.
- Plan-aware opponent with three families (weak king square, weak unit,
  passed pawn), a one-reply plan probe, and frozen decision records.
- Two-axis feedback on every player move (quality and plan impact),
  evidence-checked claims, "Show me why", progressive hints, takebacks,
  save and resume, and a post-game review.
- Desktop research harness for the concept's decision experiments.
- Relicensed under GPL-3.0-or-later to ship Stockfish.
