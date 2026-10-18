# Gentle testing two plans per decision

A player reported that the opponent "never has a longer plan": with **With my move** on, almost every Gentle move read "I have no longer plan to explain for this move". This run checks whether letting Gentle test two plans per decision, as Steady does, would change that.

## Setup

Both runs use the code at `8316412` (current main), seeds 1 through 16, at most 160 plies per game and the weakened-Stockfish learner proxy. The variant changes only `Difficulty.GENTLE.maxPlans` from 1 to 2. These are simulated games, not learner evidence.

```sh
./gradlew :harness:installDist
harness/build/install/harness/bin/harness --games 16 --difficulty GENTLE --plies 160 --workers 6
```

## Results

| Per game, mean | One plan (current) | Two plans |
|---|---|---|
| Opponent decisions | 46.1 | 52.3 |
| Decisions where a template applied | 18.3 | 21.6 |
| Plan moves (any role) | 2.3 | 3.3 |
| Plan moves per decision | 5.0% | 6.3% |
| Plan introduced, decided or broke a tie | 1.8 | 2.7 |
| Plan events shown, median | 2.0 | 2.5 |

| Rule (review E1) | One plan | Two plans |
|---|---|---|
| Plan introduced or decided the move (at least 30%) | 11% | 12% |
| Plan move beats a random acceptable move on progress (at least 70%) | 79% | 63% |

Against the proxy the opponent won 3 and lost 13 with one plan; with two it won 2, lost 12, and 2 games reached the ply limit.

## Reading the results

- A second plan adds about one plan move per game and lowers plan specificity below the 70% rule. It does not change what the player sees on most moves, so the profile stays at one plan.
- Plans are rare because most candidate plans are too costly or refuted by a strong reply, and in about 60% of decisions no template applies at all. More frequent plans need further checkable families, not more tests per move.
- What changed instead is the wording. In about a third of decisions a plan was tested and turned down; the opponent now names it and the reason. Otherwise it says that none of its plan ideas fit the position.
