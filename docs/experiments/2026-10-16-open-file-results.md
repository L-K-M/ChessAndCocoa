# Open-file plan: paired coverage check

These results precede the [Gentle strength reduction](2026-10-16-gentle-calibration.md).

The fourth plan family brings a rook through an open file to win a specific pawn. It uses the existing quality windows, plan limits and one-reply probe. The goal requires the pawn to be gone and material actually won; reaching a file or rank is only preparation.

## Setup

The comparison uses the [three-family baseline](2026-10-16-results.md) at `deca789` and the open-file addition. Both use seeds 1 through 16, at most 100 plies per game, the same difficulty profiles and weakened-Stockfish learner proxy. These are simulated games, not learner evidence. Percentages below are truncated as in the harness reports.

```sh
./gradlew :harness:installDist
harness/build/install/harness/bin/harness --games 16 --workers 4 --plies 100 --difficulty GENTLE --out build/harness/open-file-gentle.md
harness/build/install/harness/bin/harness --games 16 --workers 4 --plies 100 --difficulty STEADY --out build/harness/open-file-steady.md
harness/build/install/harness/bin/harness --games 16 --workers 4 --plies 100 --difficulty KEEN --out build/harness/open-file-keen.md
```

Raw reports: [Gentle](2026-10-16-open-file-gentle.md), [Steady](2026-10-16-open-file-steady.md), [Keen](2026-10-16-open-file-keen.md). Searches use one thread, node limits and a cleared hash. Host Stockfish SHA-256: `083320aafc7a7bc100e017e21132f0f974d097192d54b79c4c30734e6fe42a5e`.

## Results

Each cell shows baseline to four-family result.

| Measure | Gentle | Steady | Keen |
|---|---|---|---|
| E1 plan influence, target at least 30% | 12% to 15% | 19% to 19% | 12% to 13% |
| E1 specificity, target at least 70% | 65% to 74% | 68% to 68% | 73% to 68% |
| E3 median plan events per game, target at least 2 | 1.0 to 1.5 | 2.0 to 2.0 | 1.0 to 1.0 |
| Open-file moves selected | 0 to 3 | 0 to 0 | 0 to 1 |
| E2 policy branches replayed, all matching | 95 to 110 | 105 to 105 | 86 to 89 |

All 304 replayed branches match the simulated policy. These are aggregate results: an early changed move can also create later opportunities for other families. They do not isolate the new family's learning value.

## Decision and limits

Keep the narrow family: it contributes engine-checked moves in ordinary games and modestly improves Gentle's coverage. It remains rare. Steady rejects or outranks every open-file proposal in these games; Keen's aggregate specificity falls below its target. E1 influence still fails for every profile, and only Steady meets E3. This does not justify deeper on-device search or a claim that the coverage problem is solved.

The profiles ran concurrently with four workers each on a shared desktop, alongside a build. Timing is not a controlled comparison with the baseline or evidence of phone performance. Phone latency, battery, heat, Maia-2 replies, deeper resistance tests and learner outcomes remain unmeasured.
