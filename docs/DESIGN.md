# Chess & Cocoa: implementation design

This document maps the concept brief ([`chess_and_cocoa_concept.md`](../chess_and_cocoa_concept.md), v0.4) onto the code. The brief stays the source of the requirements; this file records how they are met, what was measured, and what is still open.

## 1. Modules and layers

Each layer talks only to the one directly below it. The UI never runs chess rules or engine searches itself.

```text
app        ui/ (Compose screens)
             |  intents, StateFlow<SessionState>
           session/ (GameSession: revisions, cancellation, persistence)
             |                         \
coach      game/Coach (facade)        data/ (GameStore, PreferencesStore)
             |
           decide/  assess/  plan/  evidence/  teach/  render/  facts/  analysis/
             |                                                   |
chess      rules core (Position, Game)            engine  UCI client (UciEngine)
                                                             |
app        nativeengine/ (JNI) ----------------------> libcocoafish.so (Stockfish 19)
```

| Module | Kind | Responsibility |
|---|---|---|
| `:chess` | Kotlin/JVM | Legal moves, FEN, SAN, outcomes, static exchange, absolute pins, piece identity across a game. The only rules authority (concept Section 14.3). |
| `:engine` | Kotlin/JVM | UCI protocol, score kinds with perspective, bounds, MultiPV, `searchmoves`, cancellation. Engine-agnostic: speaks FEN and UCI strings. |
| `:coach` | Kotlin/JVM | Everything the concept calls the teaching opponent: plans, move selection, assessment, evidence, teaching policy, templates. Pure Kotlin, testable on the desktop. |
| `:app` | Android | Compose UI, session controller, persistence, the JNI transport to the in-process engine. |
| `:harness` | Kotlin/JVM | Desktop research harness for the decision experiments (Section 16.7). Never shipped. |

Stockfish 19 is vendored unmodified under [`third_party/stockfish/`](../third_party/stockfish/PROVENANCE.md) and compiled with the NDK into `libcocoafish.so`, with its network embedded. One engine runs per process: the JNI bridge redirects the process-wide `std::cin` and `std::cout` to pipes, so the app keeps a single engine for its lifetime.

## 2. The decision pipeline

A computer move ([`MoveSelector`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/decide/MoveSelector.kt)):

1. Revalidate open plan episodes: goal reached, target lost, dependency broken, stalled.
2. Root search with the difficulty's own node budget (the opponent's view, not the analyst's).
3. Obligations: forced move, check, mate threat, material threat. Check and forced moves suspend plans.
4. Up to `maxPlans` plans: the open episode first, then new bindings. For each, the plan policy proposes a move and the one-reply probe tests it.
5. Choose the surviving plan move (continuity first), or the baseline: best move, tactic, or a seeded difficulty concession.
6. Freeze a `DecisionRecord` with candidates, plan snapshot, probe branches, `planRole`, `selectionBasis` and the plan-free counterfactual move.

The plan policy ([`PlanPolicy`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/plan/PlanPolicy.kt)) is one deterministic function: the template's legal actions, searched with root-restricted analysis at a fixed node budget, kept only within the quality window of the unrestricted best. Live play and the probe call it with identical inputs, and searches are single-threaded, node-limited and start from a cleared hash, so the same position gives the same move (policy identity).

The probe ([`PlanProbe`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/plan/PlanProbe.kt)) tests the three strongest replies plus one template challenge, then applies the policy once after each. A plan is refuted when a strong reply leaves it without an acceptable continuation.

### Plan families

| Family | Target | Steps | Success (checked outside the template) |
|---|---|---|---|
| Attack a weak king square | A square in front of the enemy king that only the king defends (f7 before castling) | Add attackers, create a mate threat, strike | Checkmate, or material won by the strike |
| Win a weak unit | An enemy piece or pawn attacked at least as often as it is defended, or pinned | Add attackers, remove or attack defenders, pin, strike | The unit is gone and material was won |
| Promote a passed pawn | An own passed pawn on its fourth rank or beyond | Advance, control the stop square, remove a blocker, promote | A promoted piece that keeps its material |

The third family was added after experiment E3 found the first two too rare (Section 4). A plan applies only where it has at least one statically safe next step.

## 3. Assessing the player's move

[`MoveAssessor`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/assess/MoveAssessor.kt) runs two independent axes (R3):

- **Quality**: the analyst's root search of the player's decision (started while the player thinks), the played move from the same search or a restricted search at the same budget, and mate-aware categories. A restricted search that rates the move above the best one lowers confidence instead of hiding the conflict.
- **Plan impact**: [`PlanImpactAssessor`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/assess/PlanImpactAssessor.kt) compares the played move with a reference move from the same parent position: the best move that does not address the plan. A move that ignores the plan gets `ALLOWS`; otherwise the policy's result after both moves decides `PREVENTS`, `DELAYS`, `FORCES_ADAPTATION`, `REDUCES_BENEFIT` or `ENABLES_OR_ACCELERATES`. A plan already failing after the reference yields "no supported material effect".

On the concept's fixture (Section 13.2) this gives the required results: `3...Nf6` allows `Qxf7#` with the bishop on c4 named as support; `3...g6` delays the plan, not prevents it; `3...Qh4` stops the mate in one but is neither "prevents" nor a good move ([`ScholarFixtureTest`](../coach/src/test/kotlin/ch/lkmc/chessandcocoa/coach/ScholarFixtureTest.kt)).

## 4. Evidence, teaching and wording

Claims ([`Claims.kt`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/evidence/Claims.kt)) are typed, carry a certainty class and a disclosure rule, and hold their own continuation from an exact game state. [`ClaimVerifier`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/evidence/ClaimVerifier.kt) replays every continuation and re-checks rule-verified claims before anything is shown; a failing claim is dropped. Board marks and "Show me why" use the same claim objects, and every mark pairs colour with a shape.

[`TeachingPolicy`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/teach/Teaching.kt) picks one primary point per event: game end, then serious consequences, then plan impact (introduced by the recorded intention it refers to), then good tactics. Commentary level only filters what is said. Intentions are revealed only after the player's move touched them, or only in the review. [`Renderer`](../coach/src/main/kotlin/ch/lkmc/chessandcocoa/coach/render/Renderer.kt) holds the English templates; a plan is called the reason for a move only when its recorded role allows it.

## 5. Measured results

The harness plays the real coach pipeline against a weakened-Stockfish learner proxy (not a model of people). Full reports: [`docs/experiments/`](experiments/2026-10-16-results.md). In short:

- Policy identity held in every re-run probe branch (E2).
- Plan influence and plan events per game stay below the review's thresholds for some profiles; see the report for the numbers and the decision-rule status.
- Phone latency is unmeasured: the numbers are desktop numbers.

## 6. Requirement traceability

| Req. | Where | Test |
|---|---|---|
| R1 plans influence moves | `MoveSelector` chooses plan-policy moves; `planRole` and the plan-free counterfactual are recorded | `DecisionTest.ablationMatchesTheRecordedCounterfactual`, harness E1 |
| R2 intention recorded before the reply | `DecisionRecord` frozen at decision time; plan revisions are append-only | `CoachGameTest` (earlier records never change) |
| R3 quality independent of plan effect | Separate `QualityAssessment` and `PlanImpactAssessment` | `ScholarFixtureTest` (`Qh4` stops the threat, still a blunder) |
| R4 plans as goals with dependencies | `PlanTemplate`, `PlanInstance`, `DependencyState`, lifecycle statuses | `PlanTemplateTest` |
| R5 claims backed by positions or analysis | Typed claims with continuations; `ClaimVerifier` | `ScholarFixtureTest.everyDisplayedClaimReplaysFromItsOwnState`, `CoachGameTest` |
| R6 policy tests versus first-move tests | `PlanProbe` applies the plan policy after each reply | `DecisionTest.liveAndSimulatedPolicyMovesAgree`, harness E2 |
| R7 continuity and narrated transitions | `Planner.revalidate`, transitions in records, `PlanTransitionClaim`, review episodes | `PlanTemplateTest.revalidationClosesAnEpisodeWhoseKingMovedAway` |
| R8 abstain without support | Claims without evidence are not built or are dropped by the verifier | `CoachGameTest` |
| R9 analyst independent of difficulty | `AnalystBudgets` versus `Difficulty.selectionNodes` | `GameSessionTest.experienceSettingsNeverChangeTheOpponent` |
| R10 learning through play | Untimed games, nonmodal cards, hints and "Show me why" on demand | Screenshot tests |
| R11 results tied to a revision | `GameRecord.revision`; the session drops stale results and cancelled jobs | `GameSessionTest.aTakebackDiscardsTheOpponentsStaleAnswer` |
| R12 test correctness and faithfulness | Perft and python-chess fixtures, fixture tests, decision invariants, harness | All suites |
| R13 cozy behaviour | No clock, no nagging, explicit takeback, resume, review | `GameSessionTest`, screenshots |
| R14 readable and accessible | Every square is a labelled button with state text; marks use shapes; no sound or motion needed | Screenshot tests use the accessibility labels to play |
| R15 warmth separate from judgment | Wording only renders claims; tone never changes a claim | `ScholarFixtureTest` exact wording check |

## 7. Open items

- **Phone latency, battery and heat** (E4) need real devices. Budgets live in `AnalystBudgets` and `Difficulty`.
- **Learner pilot** (E5) and Maia-2 replies (E2, E3) were not run.
- **Licensing review**: qualified review of the ODbL question for Stockfish's network before public release (concept Section 14.4).
- **Naming clearance** for "Chess & Cocoa" before public release.
- **Translations**: UI strings are resources; the coach's templates are English only.
- **Difficulty calibration**: the three profiles are hypotheses, not measured Elo.
