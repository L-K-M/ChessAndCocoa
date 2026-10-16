# Gentle: strength reduction

Phone feedback reported acceptable performance but excessive strength for a beginner. The old profile searched 20,000 nodes, considered only four top moves, and disabled concessions against any detected threat. Even its deliberate concessions often picked near-equivalent moves.

## Change

| Gentle selection setting | Before | After |
|---|---|---|
| Root node budget | 20,000 | 2,000 |
| Candidates | 4 | 12 |
| Fallback concession chance | 35% | 70% |
| Maximum concession loss, engine estimate | 250 cp | 400 cp |
| Detected threats and check | Best-ranked defense | Concessions permitted among legal replies |

Concessions remain seeded, bounded and recorded separately from plans. They are disclosed in the review. A surviving plan still takes precedence over the fallback; 70% is not the percentage of all moves that are mistakes. Forced single moves, known winning mates and mating-loss scores are protected. Steady, Keen, the analyst and plan-checking budgets are unchanged.

## Paired test

Both runs use seeds 1 through 16, alternating the learner proxy's colour, at most 160 plies and four workers. The proxy remains a 4,000-node, six-candidate Stockfish with score-weighted choices and an 8% random-move chance. Baseline product code is `56ad94d`; only outcome reporting was added before the baseline run.

```sh
./gradlew :harness:installDist
harness/build/install/harness/bin/harness --games 16 --workers 4 --plies 160 --difficulty GENTLE --out build/harness/gentle-strength-after.md
```

Raw reports: [before](2026-10-16-gentle-before.md), [after](2026-10-16-gentle-after.md).

| Measure | Before | After |
|---|---|---|
| Gentle wins / draws / losses | 16 / 0 / 0 | 3 / 0 / 13 |
| Unfinished games | 0 | 0 |
| Median computer decisions per game | 29.5 | 43.5 |
| Median plan feedback events per game | 1.5 | 2.0 |
| Replayed policy branches, all matching | 110 | 89 |
| Plan influence, reported share | 15% | 11% |
| Progress specificity | 74% | 79% |
| Episodes closed at next own decision | 46% | 77% |

Gentle is substantially weaker against this fixed proxy. Plan feedback still occurs, but episodes break sooner and influence remains below target. The larger candidate pool also changes E1 eligibility and its random-acceptable-move control; those percentages are not a fixed-position comparison. Longer games help the per-game feedback count.

Regression tests first demonstrated zero concessions in 100 threatened-position choices and no weaker responses to check. They now verify fallibility, bounded losses, legality, protected mate scores and stronger-profile defenses. An engine-backed test confirms that a player's mate blunder receives the same assessment on Gentle and Keen. Seeded replay and plan-ablation tests cover Gentle too.

This is relative strength validation, not a human Elo estimate or proof of beginner suitability. The new profile needs player feedback. Timing came from a shared desktop alongside a build; the reported phone responsiveness was not a controlled benchmark.
