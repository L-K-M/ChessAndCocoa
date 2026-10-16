# Chess & Cocoa concept review (document v0.2)

Independent review of `chess_and_cocoa_concept.md` version 0.2, dated October 16, 2026. Section numbers refer to version 0.2. Version 0.3 of the concept applies the owner decisions in Section G and the amendments in Section E. Version 0.4 applies the later switch to GPL-3.0-or-later and Stockfish (Section G). Sections A–F describe the repository as reviewed.

**Assumption:** one experienced developer or a small team (2–3). The document does not say.
**Method:** I read the whole document, and Section 18 was not executed. Chess claims were checked with python-chess 1.11.2 (rules) and Stockfish 19 (official build, Threads=1, node-limited) on a shared desktop Xeon W-2145. Primary sources were fetched 2026-10-16. Independent helper reviews (Devin swe-2-max, GLM-5.3, DeepSeek V4.1 Flash via Venice) were cross-checked; one helper chess claim was wrong and is excluded. The verification scripts were run locally and were not committed, because they import python-chess (GPL-3.0+) and the repository was then under the Unlicense.

## A. Executive verdict

**Prototype before committing, and simplify the decision-time planner.** Confidence: medium-high on architecture; low on learning value, where no evidence exists either way.

- **Product idea: keep it.** An opponent that says what it was really trying to do, and how your move changed that, is distinctive and honest. Nothing found refutes it.
- **Architecture: change it.** Do not start with on-device multi-turn adversarial validation (§8.2–8.4 at decision time). Start with plan-aware selection among engine-checked moves, persistent goals and dependency predicates (alternative B), plus a **one-reply probe**. That probe is the document's own §8.2 rollout at horizon 1. Use deeper rollouts offline to audit templates; raise the on-device horizon per family only when measurements justify it.
- **Why:** faithful intention claims (R1) need only real influence on selection, which B gives cheaply. Deep rollouts add advance-viability claims at one to two orders of magnitude more search. They also rest on a policy-identity problem the spec doesn't solve, and on a usefulness check that is circular.
- **The largest unknowns are not engineering.** Do the plan families fire often enough in beginner-level games? Does faithful plan talk teach better than equally good post-hoc coaching?

**Retain:**
- R2 immutable records with §10.5's no-retroactive-traps rule.
- The R3 two-axis assessment, including the "allows plan + favorable" row (§10.3).
- R6 and §8.1's `searchmoves` caveat.
- Certainty classes and abstention (§12.1, R8).
- The R9 analyst/difficulty split.
- R11 revision-tagged results.
- The R13–R15 behavioral cozy requirements and their scope fences (§2.3, §4.4).
- The §16.5 three-arm learning comparison.
- The honest scoping in §15.1.

## B. Prioritized findings

No demonstrated blocker to the product idea. There are three **conditional blockers**:
1. E3 measures low coverage → the distinctive feature is a demo.
2. A plan family fails Finding 1's independent value check → that family must not ship.
3. Proprietary closed distribution with bundled Stockfish → licensing (risk 12).

### 1. Plan usefulness is validated by the same heuristic that proposes and scores it

- **Severity / refs:** important risk; conditional blocker per family. §7.1, §7.2 "Success", §8.4, §17 row 3; R12.
- **Proposes:**
  - The template supplies applicability, goal predicate, progress features, challenges and explanation vocabulary.
  - Mitigation: §8.4 "Chess quality" and "Cost of persistence"; §17 "named benefits, opponent challenges, and independent quality checks".
- **Gap:**
  - Cost of persistence shows a plan is *cheap*, not that its goal *matters*. A near-optimal engine makes persistence cost ≥ 0 almost always, so a zero-cost, zero-benefit plan passes.
  - §7.2's "analysis supports that role against tested responses" has no operational definition.
  - Template-authored challenges keep even the resistance test inside the circle; only engine replies are independent.
- **Failure (schematic):** a knight-outpost template counts every acceptable knight move as progress, and rollouts show no persistence cost. The opponent then tells the learner "my knight is heading to d5 to support my attack" about a square with no measurable value. The claim is faithful but teaches a false strategic lesson.
- **Why it matters:** R1 guarantees the program's reason is real, not that it is good chess. Learners model the plans they are shown.
- **Smallest correction:** before a family ships, require three checks the template does not control:
  - **Specificity:** plan-chosen moves score higher on the family's progress features than the other acceptable moves. If not, the plan is just a label.
  - **Goal value:** an engine contrast between continuations where the goal is achieved and matched ones where it is frustrated, with a family-specific margin.
  - **Expert review:** blind expert rating of sampled episodes.

  Prefer families whose success has an external oracle: material won, forced mate, tablebase result.
- **Evidence / confidence:** wording circularity demonstrated; the failure mode is plausible. High.

### 2. The rollout policy cannot match the live policy as written, and analysis is nondeterministic

- **Severity / refs:** important risk; foundation for R1/R6 testing. §8.2 vs §9.1, §9.3, §11.3, §16.1.
- **Demonstrated contradiction:**
  - §8.2 says: "The simulated policy must match the policy intended for actual play."
  - §9.1's live selector ranks by "resistance to tested challenges", which is `testPlan`'s own output (a regress), and also by "instructional relevance" and "variety". §9.3 puts difficulty into move selection.
  - `testPlan(gameState, plan, responsePolicy, budget)` has no difficulty input. `selectComputerMove(...)` has no learner context or history, yet must compute relevance and variety.
- **Failure:** rollouts validate a full-strength plan follower while a beginner-profile opponent deviates in play. Assessments then overstate live execution exactly in the profiles beginners use.
- **Measured nondeterminism (desktop):**
  - Position: Closed Ruy Lopez after 11.d4 Qc7, MultiPV 3, four runs.
  - Threads=8 with 0.5 s per search: 4 different results. The third candidate varied between Qe2, Re2 and Bd3; Nbd2's score ranged 35–55 cp.
  - Threads=1 with 1M nodes and a cleared hash: 4 identical results.
  - So a 20–30 cp acceptability window changes membership from run to run. Version pinning (§16.1) does not fix this.
- **Smallest correction:**
  - Define one deterministic plan policy per template, π_P(s): generate the template's legal actions → run a `searchmoves`-restricted search under a fixed node budget → pick the best move inside the quality window.
  - Use π_P both in rollouts and in live play while the plan is active. The selector only chooses *which* plan, obligation or fallback to follow.
  - Difficulty is either inside π_P with a seeded random source, so rollouts reproduce it, or recorded as an interruption.
  - Record and ablation paths use Threads=1, node limits and an explicit hash policy. For throughput, run several single-thread engine instances in parallel.
  - This removes the move-level mismatch but not the trajectory-level one: live play may re-plan. Claims must stay conditional: "if I keep following this plan, the replies I tested don't stop it."
- **Evidence / confidence:** contradiction demonstrated in the wording; nondeterminism measured on desktop. High.

### 3. First-person intention claims can be false while every logging rule is met

- **Severity / refs:** important risk. §16.3 "Plan influence", §9.1, §9.3, §11.1 `DecisionRecord`; R1, R2, R8, R15.
- **Proposes:** an ablation test, plus "Attaching labels after selection fails". Mitigation: R2 freezing; §9.3 "the coach must not falsely call it best".
- **Gaps:**
  - **Weak acceptance test.** §16.3 passes if removing the plan changes "candidate preference **or the recorded deciding criteria**". A labeler that records different criteria while picking the same move passes.
  - **No per-decision attribution.** Under §9.1, a plan orders the acceptable set and may introduce candidates. With one acceptable move (forced replies, punishing blunders) it is inert. A plan can stay `active` for many moves without deciding one.
  - **Difficulty moves have no honest record.** The beginner profile must choose weak moves on purpose. §11.1 has "selected plan revision or fallback reason", with no slot for "chosen to be weaker". §9.3 guards the coach's voice, not the opponent's "I".
  - **Teaching-driven choices.** "Instructional relevance" (§9.1) can drive a choice that is then narrated as a chess intention.
- **Failure:** "I played this to pressure your pawn" appears on a move the selector would play without the plan, or on one picked by the difficulty filter.
- **Smallest correction:**
  - Add `planRole` = introduced-candidate | decisive | tiebreak | concurrent | none. Compute it by re-running candidate generation *and* ranking with the plan removed, reusing cached analyses (no new searches).
  - Add `selectionBasis` = plan | immediate obligation | difficulty concession | teaching preference | fallback.
  - Wording follows the role. Only introduced-candidate, decisive or tiebreak may say "I played this to…". Concurrent says "this also fits my plan". A difficulty concession carries no plan claim and is disclosed in review.
  - Measure influence at corpus level on natural positions, not only on controlled fixtures.
  - Prefer weakness mechanisms that keep the opponent's speech true: fewer candidate plans or shallower threat checking, so misses are genuine and can be narrated as "I did not see your threat".
  - Add an acceptance test that every narrated claim stays supported at the lowest difficulty.
- **Evidence / confidence:** weakness in the acceptance wording demonstrated; contract gap demonstrated. High.

### 4. Axis B ("effect on my plan") has no defined baseline and no goal-level rule

- **Severity / refs:** important risk. §10.2 vs §10.4, §4.3, §13.2; R3, R5.
- **Proposes:**
  - §10.2 compares the move with "the plan revision that existed before the response".
  - §10.4 warns "Do not confuse the natural passage of a turn with a causal effect of the chosen move" and asks for legal alternatives.
  - Existing hedges: "Prevents" is qualified "in the supported analysis"; §13.2 correctly scopes 3...g6 as "the immediate threat is interrupted".
- **Gap:**
  - Nothing ties a §10.2 label to §10.4's same-parent contrast.
  - Nothing fixes whether impact is judged at route level or goal level. The same events get opposite labels depending on how a template author bound the plan.
  - A before/after dependency diff also credits the human with killing a plan that was already dead, or with effects that come only from the turn passing.
- **Verified on the §13.2 fixture** (1.e4 e5 2.Bc4 Nc6 3.Qh5):

  | Claim | Check |
  |---|---|
  | 3...g6 blocks h5–f7 and attacks the queen | rules |
  | 4.Qf3 renews the threat: after 4.Qf3 a6, 5.Qxf7# is mate | rules |
  | 4.Qf3 is Stockfish's top reply: −22 cp (White's view) vs 4.Qd1 −35 | engine, 3M nodes |
  | So 3...g6 forces adaptation; it does not prevent the plan | follows from the above |
  | 8 of 28 legal replies leave no mate in one | rules |
  | Those 8 range from 3...g6 (+30 for Black) to 3...Qg5?? and 3...Qh4??, which lose to 4.Qxf7+ Kd8 5.Qxf8# | engine (2M nodes); mate confirmed by rules |
  | 3...Ke7 ≈ −5.6 pawns | engine |

  "Stopped the recorded threat" spans the best move to mate in two.
- **Smallest correction:**
  - Compute impact at goal level; route changes are revisions within the goal.
  - Define impact as a contrast with reference moves from the same parent: an engine-best move that doesn't address the plan, and internally a null move as a diagnostic, never displayed (§6.3).
  - "Prevents" requires continuation evidence that π_P finds no renewal within the probed horizon. Otherwise the label is "delays" or "changes a dependency".
  - Add a fixture where the plan is already dead before the human moves; the expected output is "no supported material effect".
  - §4.3 clarification: when Axis A finds a material or decisive loss, the primary point must state it.
- **Evidence / confidence:** wording gap demonstrated; positions rules- and engine-verified. High.

### 5. The distinctive feature may rarely fire for the stated audience, and default disclosure may do the learner's work

- **Severity / refs:** important risk; conditional product blocker. §1.1, §4.2, §4.3, §7.3, §9.3, §15.1–15.2, §17 rows 4, 7, 9.
- **Proposes:**
  - Beginners and improving players; 1–2 strategic families demonstrated in curated positions.
  - Mitigation: fallback (§2.3, R8); no promise of coverage in arbitrary positions (§15.1); "measure coverage" (§17); coverage metrics (§16.6).
- **Gaps:**
  - **Tactics dominate.** Beginner games are mostly decided by one- and two-move tactics (expert judgment, unverified). §9.2 overrides then pre-empt plans, so most feedback is tactical, which alternative A already delivers.
  - **The opponent must err.** Stockfish's built-in limiter stops at `UCI_Elo` 1320, calibrated at 120 s + 1 s and anchored to CCRL 40/4; Skill Level 0 is about 1347 on that scale (Stockfish 19 docs, verified). Engine Elo is not human rating, but this confirms difficulty must come from the app's own selector (§9.3). Each concession weakens the "my plan" story (Finding 3).
  - **Default disclosure.** Guided play is the default (§4.2) and announces intentions before the reply. That performs threat detection, the skill beginners most need, for them, and hands the human free defensive information. "During play" does not say whether a plan is announced when adopted or only after it resolves, and that choice is load-bearing. §4.3 tracks "help", but nothing says these unrequested disclosures count as help.
  - **No hope-chess plans.** Strong-response testing (§8.2–8.3) excludes plans that work only against weak replies; §10.5 bans only retroactive traps, so pre-recorded ones remain possible. Those plans and traps are common at this level and instructive (owner decision F3).
  - **Passive pedagogy.** No mechanic exercises the learner's own planning.
- **Smallest correction:**
  - Measure coverage per game before deepening the planner (Experiment 3).
  - Choose families by measured frequency in target-level games and by oracle availability, not by how easy they are to curate.
  - Count every disclosure as assistance.
  - Default to progressive disclosure: announce that a plan episode exists and report completed effects live, but keep targets, routes and threats sealed until resolved or requested. The R2 record is shown at reveal, so its integrity is visible.
- **Evidence / confidence:** empirical unknown; Stockfish limiter verified. Medium.

### Other important risks

| # | Issue | Refs | Existing safeguard | Remaining gap → smallest step | Status, confidence |
|---|---|---|---|---|---|
| 6 | Search cost of deep rollouts | §8.2, §10.4, §14.2, §4.4 | Total budget; degrade coverage | Count one MultiPV search per opponent node and one π_P search per computer node. A deterministic π_P with R sampled replies and horizon H then needs Σ_{k<H} Rᵏ + Σ_{1≤k≤H} Rᵏ searches per plan: 52 for R=3, H=3; 4 for H=1. If the policy keeps 2 actions per turn, that rises to about 170–300, depending on whether the actions share a search. Multiply by 2–4 plans, then add §10.4 persistence, adaptation and alternatives: 10²–10³ searches per move. Desktop measurement: 50k nodes takes 0.10 s (depth 15) and 200k takes 0.33 s (depth 17) on one thread, so 10–100 s per move single-thread. Phones unmeasured. The §10.4 battery lands in the "remaining budget" that §14.2 spends last, so it is the first work cut. A one-reply probe needs about 4 searches per plan, about 12–16 per move in total. Add a kill criterion and a degradation order for §10.4 that keeps one shared configuration. | Arithmetic + desktop measurement; phones unknown. High |
| 7 | Horizon shorter than goal | §7.2, §8.2 step 5, §12.1 | Horizon recorded; certainty classes | Pawn-pressure and outpost episodes outlast "a few computer turns" (§14.2). Rule: if the goal boundary isn't reached, the claim is heuristic or suppressed. Test the rule. | Plausible. Medium-high |
| 8 | Quality gate may exclude preparation | §7.2, §9.1 | Plan-introduced candidates are evaluated explicitly | Slow preparation moves may fall outside the window; widening it lets mistakes in. Data point: 3.Qh5 is about 41 cp below 3.Nf3 (−24 vs +17, 2M nodes), so the f7 plan is selectable only with a window of about 40 cp or more. Measure where plan moves fall. | Plausible; one measured datum. Medium |
| 9 | Challenge generator blind spots | §8.3 | Strong-reply mixture; refutations recorded | No coverage metric. Measure recall of refutations found by deeper unrestricted search on held-out positions. | Plausible. Medium |
| 10 | Computer's own weak plans taught faithfully | §4.2, §9.3 | §4.2: the coach may say a stronger move existed than the opponent chose; §9.3 bars calling it best | Move-level only: the coach never critiques the computer's *plan*. In review, flag plan moves with high persistence cost. | Plausible. Medium |
| 11 | Claims link evidence without proving it | §12.1, §16.4 | Traceability required | A test can pass on mere existence of a link. Re-derive each claim type from its cited evidence. Audit "only/forced/cannot/wins" against the certainty class. Store evidence values, not just §7.5 "analysis references", because re-runs can differ (Finding 2). | Plausible. High |
| 12 | Licensing inputs incomplete | §14.4 | Release gate; qualified review | Add: python-chess is GPL-3.0+ (verified on PyPI), which matters if harness code is ported or shipped; this repo's LICENSE is the Unlicense; Stockfish nets are trained on Leela data under ODbL (Stockfish README). Human-move models: Maia-1 GPL-3.0, Maia-2 repo MIT, Maia-3 AGPL-3.0 (repo licenses; weight terms not verified). The distribution model determines the engine and integration boundary. No legal conclusion offered. | Facts verified. High |
| 13 | Android engine integration | §14.1 | "Choose and validate" | Verified: apps targeting API 29+ cannot `execve()` files in the app home directory. A subprocess engine needs the community workaround (ship it as `lib*.so` and run it from the native-lib directory); Briar uses this for Tor, Android doesn't document it, and it relies on the installer extracting it. In-process JNI is the documented path; integration choice and licensing analysis are coupled. Google Play requires 16 KB page support for apps targeting API 35+; updates without it are blocked from Feb 1, 2027 (NDK r28+ aligns by default). The official SF19 Android arm64 binary is about 101 MB uncompressed (measured). Routine engineering, but decide early. | Verified. High |
| 14 | Plan-episode identity across branches | §14.3 | Plan state restored on the selected branch | Fork semantics are undefined: does "anticipated" survive a takeback? Specify, and test that histories never mix. | Gap. Medium |
| 15 | Authoring cost hidden | §2.2, §13.1, §15.1, §16.2 | Reviewed fixtures required | Coverage is editorial work: chess-expert review, fixture authoring. Budget it as a deliverable. | Empirical. Medium |
| 16 | No difficulty acceptance case | §9.3, §16 | "Do not claim a target Elo without measurement" (§9.3) | Add a case: a reference beginner-strength opponent scores within a target band against each profile, with the analyst at full strength. | Gap. Medium |

### Optional improvements

- **Plan myopia as an honest difficulty lever.** Weak profiles pursue the plan with reduced threat checking, and review can say "I kept pushing my plan and missed your bishop."
- **Learner-side pattern facts without mind-reading.** "You've brought three pieces toward my king" engages the learner's planning while respecting §2.3.
- **Axis A consistency.** Analyze the parent position once, with `searchmoves` = top-k ∪ {played move}, under one budget. It can run, bounded, during the human's thinking time.
- **Narrate lifecycle transitions.** §4.3 suppresses repeated statements about an unchanged goal, which hides the continuity R7 is meant to teach. Narrate adopted, delayed, revised and abandoned, and add a small persistent plan indicator.
- **Bind claim types to speakers.** Plan intent belongs to the opponent ("I"); quality and impact belong to the coach. Decide between two personas and one styled channel, and test whether learners understand which is speaking.
- **Name.** One web search found no conflicting "Chess & Cocoa" product. Results collide with Apple's Cocoa framework, which affects discoverability, not legal clearance. §14.4's clearance gate stands.

## C. Architecture recommendation

| | A: engine + grounded post-hoc templates | B: plan-aware selection, persistent goals, dependency predicates | C: document §8, decision-time multi-turn validation |
|---|---|---|---|
| Teaching potential | Tactics, consequences, effect descriptions | A + a real plan narrative across moves; "your move removed what my plan relied on"; review episodes | B + advance claims ("in the lines I tested you'd have lost the pawn") |
| Decision faithfulness | None for intention: coach voice only | High, with per-decision attribution | Same for intention; adds tested viability within a sampled horizon |
| Strategic coverage | Broad but descriptive | Per template | Per template; templates cost more because the policy must play coherent chess inside rollouts |
| Burden (1 dev / small team) | Low | Medium | High; `testPlan` is a research problem |
| Mobile, searches per move | 1–3 | 2–6 (about 12–16 with the probe) | 10²–10³ (risk 6) |
| Honest claim | "This move does X; here is the line." | "Among sound moves I chose this to pursue G. G relies on D; your move removed D." | "…and against the replies I tested within N moves, G held / failed." |
| What it gives up | **The distinctive concept**: with no prior intention, the plan-impact claims of §10.2 would need fabricated intent, which §10.5 bans | Pure B can't tell delays from prevents, or reject plans that are dead on arrival | Cost, the identity problem, circular value |

**Recommend B plus a one-reply probe.** With π_P from Finding 2, A ⊂ B ⊂ C differ only in horizon H and sampled replies R. B is horizon 0, the probe is horizon 1, and C is horizon ≥2.

- **At decision time, per candidate plan (at most 2):** one MultiPV search at the opponent node after π_P's move (top 2 + 1 template challenge), then π_P after each reply. This rejects plans that are dead on arrival.
- **At assessment time:** π_P for one turn after the played move and after 1–2 reference moves. This makes goal-level "prevents" versus "delays" claims possible, scoped "for now".
- **Offline:** H = 2–3 tree rollouts in the desktop harness to audit dependency lists, challenge generators and abandonment rules.

| Capability | Status under the recommendation |
|---|---|
| R1, R2, R3, R7, R8, R9, two-axis assessment, review episodes | Preserved |
| R4 conditional actions | Weakened: tested one reply deep only |
| R6 | Weakened: met by scoping claims, plus offline audits |
| §16.3 "false-plan rejection" | Weakened: applies at the probed horizon on device |
| Multi-move advance-viability claims; challenge-aware selection beyond one reply | Deferred |

This is a descope of §8, not a neutral option.

**Raise a family's on-device horizon when** Experiment 2 shows both of these:
- More than 25% of horizon-1 announced plans are refuted within two computer turns against best defence (proposed target).
- H=3 roughly halves that rate at a cost Experiment 4 accepts, without dropping Experiment 3's coverage below its floor.

## D. Decision-making experiments

Numbers are proposed targets, not established facts. Use the deterministic engine profile (Threads=1, node limits, explicit hash policy) throughout.

**E1. Plan influence and specificity (engineering)**
- **Hypothesis:** the plan term changes moves, and the changed moves are specific to the plan.
- **Setup:** one family (pawn pressure). 50 curated positions plus 200 uncurated positions where the template applies, taken from club/master PGNs.
- **Control:** the selector with the plan removed, at both candidate generation and ranking; a random acceptable move.
- **Observe:**
  - `planRole` rates in positions with ≥2 acceptable moves.
  - Progress-feature gain of the plan move over other acceptable moves.
  - The goal-value contrast (Finding 1).
- **Decision rule:**
  - Proceed if the plan introduces or decides the move in ≥30% of those positions, and plan moves beat random acceptable moves on progress in ≥70% of those cases.
  - Otherwise the template is only labeling: redesign it before any UI work.

**E2. Continuation under resistance (is a deeper horizon needed?)**
- **Hypothesis:** horizon-1 plans survive realistic resistance well enough that deeper validation adds little.
- **Setup:** from E1 positions, play B with the probe for 2–3 computer turns against three kinds of reply:
  - engine-best;
  - template challenges;
  - human-proxy replies from Maia-2 in low-Elo buckets (a proxy, not humans).
- **Control:** offline H=3 rollouts with the same π_P.
- **Observe:**
  - Dead-on-arrival rate.
  - Refutation within two turns.
  - Share of plan claims that later need "my idea no longer works".
  - Recall of refutations found by deep unrestricted search.
  - Live and simulated π_P moves are identical (must be 100%).
- **Decision rule:**
  - Any π_P mismatch must be fixed before further work.
  - Raise the horizon per the rule in C.

**E3. Coverage in ordinary games**
- **Hypothesis:** plan-related teaching events happen often enough in games at the target level.
- **Setup:**
  - 200 full games: the beginner profile against Maia-2 low-Elo buckets, and against a weakened Stockfish as a second proxy.
  - Then real games from about 10–20 consenting pilot learners.
- **Observe, per game:**
  - Moves where a template applies.
  - Moves where the plan introduces or decides the choice.
  - Axis-B events that are not "Unknown".
  - Tactical events and abstentions.
- **Decision rule:** if the median is below 2 plan events per game, switch to more frequent families or reposition the product as a tactics coach with occasional plan episodes. Do not build a deeper horizon.

**E4. End-to-end mobile cost**
- **Hypothesis:** B with the probe responds promptly on a minimum-class device; deeper horizons do not without crippling depth.
- **Setup:**
  - SF19 arm64, 16 KB-aligned, packaged per risk 13.
  - Three devices (low, mid, high).
  - Scripted 40-move games through the full pipeline at H = 0, 1, 2, 3.
  - Measure single versus parallel single-thread engines, including memory.
- **Control:** a plain engine move at the same node budget.
- **Observe:**
  - p50/p95 latency.
  - Battery use per 30 minutes.
  - Onset of thermal throttling.
  - Tactical misses of shallow checks against a deep reference.
- **Decision rule:**
  - The default configuration must reach p95 ≤ 2 s and ≤ 5% battery per 30 minutes on the minimum device.
  - A horizon ships on device only if it passes both this and E2.

**E5. Learner pilot (learner evidence; small; not proof of efficacy)**
- **Hypothesis:** faithful plan-aware feedback beats equally good post-hoc coaching on transfer.
- **Arms:**
  1. No commentary.
  2. A, coach voice.
  3. B with the probe.
  4. Optional: a "sticky" post-hoc plan narrative (persistent but unfaithful labels), to test whether faithfulness itself matters to learning or only to integrity. Debrief participants.
- **Controls:**
  - Same opponent profile and compute budget.
  - Explanation sets matched for count, length and quality (blind expert rating before the pilot).
  - Identical hint availability, with hints logged.
  - Fixed disclosure setting.
- **Size:** about 10–15 learners per arm, 3–4 sessions.
- **Disclosure timing:** within arm 3, vary timing (announced at adoption / revealed after resolution / on request) and measure unaided threat identification afterwards.
- **Outcomes:** one instrument per goal. Pre-commit to independent learning as the decisive endpoint.
  - Enjoyment: return rate and session satisfaction.
  - Comfort: perceived-pressure rating, plus willingness to use hints.
  - Assisted performance: in-app accuracy, flagged by help shown (§4.3 collects this).
  - Independent learning: hint-free, narration-free pre/post and delayed tests on novel positions (identify the threat; state the opponent's plan and a reply).
- **Decision rule:**
  - The pilot only sizes effects and surfaces problems; it does not prove efficacy.
  - If B with the probe does not beat A on transfer, keep it only for integrity and only if cheap. Never fund deeper horizons on learning grounds.

## E. Targeted document amendments

1. **§16.3 "Plan influence":** replace the outcome with: "For each decision, rerun candidate generation and ranking with the plan removed, using the same cached analyses. Record `planRole` (introduced-candidate | decisive | tiebreak | concurrent | none). Across positions with at least two acceptable moves, including natural positions, the plan must change the chosen move at a measured rate, and the changed moves must outscore other acceptable moves on the plan's progress features. A change only in recorded criteria does not count."
2. **§8.2:** replace "The simulated policy must match the policy intended for actual play" with: "Each template defines one deterministic plan policy: generate the template's legal actions, search them with root-restricted analysis under a fixed node budget, and pick the best within the quality window. Rollouts and live play while the plan is active use this same function. The move selector chooses only which plan, obligation, or fallback to follow. Claims from rollouts are conditional on continued plan-following."
3. **§11.1 / §11.3:**
   - Add `planRole`, `selectionBasis` and an engine-profile digest (threads, node limit, hash policy) to `DecisionRecord`.
   - Store evidence values, not just references.
   - Add learner context and history to `selectComputerMove`, and difficulty to `testPlan`.
4. **§10.2:** add: "Impact labels are computed at goal level and against reference moves from the same parent, not by before/after comparison. 'Prevents' requires continuation evidence that the plan finds no renewal within the tested horizon; otherwise use 'delays' or 'changes a dependency'."
5. **§4.3:** add: "If Axis A finds a material or decisive loss, the primary point states it. Unrequested intention disclosures count as assistance."
6. **§7.2 and §8.4:** replace the §7.2 success line with: "The knight reaches the square and an independent check supports its value: against tested responses, engine evaluation with the goal achieved exceeds a matched alternative by a family-specific margin." Add a §8.4 row, "Goal value (independent of template)". Add blind expert episode review to §16.2.
7. **§7.3:** add: "Select initial families by measured frequency in target-level games and by availability of an external success oracle."
8. **§4.2:** specify disclosure timing (at adoption, after resolution, or on request), and recommend progressive disclosure as the default (owner decision).
9. **§14.1 / §14.2 / §14.4:**
   - §14.2: the search-count formula, a kill criterion, a degradation order for §10.4, and the deterministic profile.
   - §14.1: the Android `execve` and 16 KB constraints.
   - §14.4: python-chess GPL-3.0+, the repo's Unlicense, the ODbL note, and the Maia licenses.
10. **Opening hypothesis paragraph:** compare against "evidence-grounded post-hoc explanations of equal quality", not "isolated move labels or evaluation numbers". This aligns it with §16.5.
11. **§1.2 / §15.1:** state the claim scope: "Plans influence selection among sound moves. Many tactical moments carry no plan content."

## F. Remaining owner decisions

1. **Learner band.** True beginners (tactics first, plans secondary) or improving players around 1000–1600 (plans central)? This drives families, difficulty mechanism and coverage expectations.
2. **Default disclosure.** Announce intentions before the human replies, or disclose progressively and seal details until afterward?
3. **Hope-chess plans.** May the opponent pursue level-tailored plans and traps (which needs a human-response model and pre-recorded trap intent), or only plans that hold against strong replies?
4. **Distribution model.** GPL-compatible open source, or proprietary? This sets the engine choice and the in-process versus subprocess boundary.
5. **Device class and latency.** What minimum device and per-move latency are acceptable? This decides whether any on-device horizon above 1 is possible.

## G. Owner decisions (October 16, 2026) and their consequences

| Decision | Consequence |
|---|---|
| Beginners first; support improving players later | Start with plan families that have outside success checks: **win a weak unit** (material won) and **attack a weak king square** (mate or material won). Move the knight maneuver to the improving-player band. Both bands share one architecture with per-band profiles for repertoire, difficulty and explanation depth. Weaken the opponent by narrowing what it notices, so its statements stay true. |
| Never announce intentions before the player moves; reveal them as the player chooses, with an assessment and the move's impact | Resolves the disclosure gap in Finding 5. The plan-impact explanation (Finding 4) becomes the main teaching moment, so the one-reply probe at assessment time is essential. Reveal only the part of the plan the move affected; otherwise the first reveal spoils the rest of the episode. |
| Plans must hold against strong replies (the simpler option) | No human-response model and no recorded traps on the device; decision F3 is closed. Maia-2 remains an offline test opponent. |
| Most liberal license, preferably public domain | Stockfish (GPLv3) is excluded. The engine must be permissive (table below). A permissive engine can be linked in-process with the NDK, which avoids the separate-executable workaround of risk 13. Rules core: chesslib (Apache-2.0) or an in-house public-domain core. Keep python-chess in the desktop harness only. The shipped app still carries third-party notices (AndroidX is Apache-2.0; the engine is MIT); "public domain" applies to the app's own code. |
| Phones from the last two years; under 5 s per move most of the time, under 10 s in extreme cases | This budget is looser than E4's original rule of p95 ≤ 2 s. Proposed replacement for E4's decision rule: p90 ≤ 5 s and maximum ≤ 10 s on a mid-range and a flagship phone released in the last two years, over a 30-minute session. Support arm64 only. Desktop timing (Stockfish, one thread, about 0.63 M nodes/s) suggests the one-reply probe fits easily and deeper horizons might too. The chosen engine's speed on phones is unmeasured, so measure before relying on it. |

### Revision: GPL-3.0-or-later and Stockfish (October 16, 2026)

The owner relicensed the app under GPL-3.0-or-later to use Stockfish; decision F4 is closed. This supersedes the license row above and the engine recommendation below; the permissive engines remain fallbacks.

- Stockfish is GPL-3.0-or-later (source headers). Every distribution must include the license and the full source, or a pointer to it (README).
- Linking it in-process through the NDK is then the plain path, so risk 13's separate-executable workaround stays unnecessary.
- This review's fixtures, determinism checks and desktop timing used Stockfish 19, so they now describe the shipped engine.
- Its networks are trained on Leela data under the ODbL (README). ODbL section 4.3 requires a notice for publicly used produced works; a derivative database would also fall under section 4.4 share-alike. Show Stockfish's acknowledgment in the app, and have the qualified review decide which applies to the networks.
- The official SF19 Android arm64 binary is about 101 MB uncompressed (measured). Measure the packaged app size early.
- python-chess becomes license-compatible, so the reason for not committing the verification scripts no longer applies. It stays in the desktop harness, because the app has no Python runtime.

### Permissive engine fallbacks

| Engine | Code license | Network provenance | `MultiPV` / `searchmoves` / `go nodes` | Strength | Verification |
|---|---|---|---|---|---|
| Caissa (C++) | MIT | Own self-play games (README). Network files are in a separate repository that declares **no license**. | yes / yes / yes | README reports CCRL 40/15 3628 | License, README, UCI parser and the network repository checked directly |
| Arasan (C++) | MIT, explicitly covering the network directory | Recent networks trained on "6B+ lc0 + selfplay games" (`doc/CHANGES`); Leela data is under the ODbL | yes / yes / no (use depth limits) | Helper-reported CCRL 40/15 about 3546 | License, changelog, UCI parser and Android CMake notes checked directly |
| Patricia (C++) | MIT | Trained on the author's Willow engine data | yes / no / yes (helper-reported) | README: about 3500 CCRL Blitz | License and README checked; UCI details helper-reported |
| Altair, Renegade (C++) | MIT (helper-reported) | Self-generated data (helper-reported) | Missing `searchmoves` (helper-reported) | Helper-reported 3400–3600 class | Not verified |

Excluded or weaker options, all from the helper survey:
- **Lc0-trained networks:** Starzix (WTFPL, archived) and akimbo (MIT).
- **Managed runtimes:** Leorik, Lynx and Sapling need .NET.
- **Weaker:** handcrafted-evaluation engines below about 3000.

**Original recommendation (superseded):** Caissa is the primary candidate; it is the only one verified with all three analysis features. Before committing to it, ask its author to put a license on the network files (MIT or CC0). Arasan is the fallback, after a qualified review of the ODbL question for networks trained partly on Leela data. No legal conclusion is offered here.

### Owner actions

1. Obtain qualified licensing review before release, including the ODbL question for Stockfish's networks.
2. Choose two or three test phones from the last two years, at least one mid-range, for Experiment 4.
