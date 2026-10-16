# Chess & Cocoa - a cozy chess teaching app

## Android product concept and technical design brief

**Document version:** 0.2  
**Date:** October 16, 2026  
**Status:** Concept specification, not an implementation plan or a validated research result  
**Intended reader:** A software architect or implementation-planning LLM with no access to the preceding conversation  
**Revision focus:** Selected product name and cozy experience requirements; the plan-aware reasoning architecture is retained

> Build **Chess & Cocoa**, a cozy Android chess game that teaches through play. Make the experience calm, welcoming, and unhurried. Its computer opponent chooses explicit, testable plans, explains the purpose of its moves, and explains how the human's responses affect those plans. An established chess engine checks move quality; it does not substitute for the application's strategic planner. Warm presentation must never come at the expense of accurate feedback.

This document defines the product, the essential reasoning architecture, proposed component contracts, and the evidence needed to trust the result. The next implementation planner should turn it into concrete technology choices, experiments, milestones, and engineering tasks. Recommendations and unresolved questions are distinguished from mandatory conceptual requirements.

The central product hypothesis is that explaining the interaction between both players' plans will help people learn chess more effectively than isolated move labels or evaluation numbers. That educational benefit remains to be tested.

---

## 1. Vision and intended experience

### 1.1 The problem

The intended user wants to learn while playing a real game, rather than complete a detached tutorial before being allowed to play. A numerical evaluation or a label such as "mistake" does not, by itself, tell that learner what changed or what to think about next.

The app should answer questions in the context of the current game:

- What was the computer trying to achieve with its last move?
- What did my move prevent, permit, or make urgent?
- Was my move effective overall, and why?
- What should I pay attention to in a similar position?

The primary audience assumption is beginners and improving recreational players. The initial implementation may assume basic familiarity with moving pieces, but should explain unfamiliar terms in context. Support for complete beginners can add legal-move guidance and brief, event-triggered rules explanations without making a tutorial a prerequisite.

### 1.2 The distinctive capability

The computer is a **goal-directed opponent with an inspectable decision record**, not simply a strong engine followed by a prose generator.

A conventional explanatory wrapper has this shape:

```text
Choose a move with an engine
    -> Find a plausible description afterward
```

The proposed player has this shape:

```text
Identify candidate goals
    -> Test conditional plans
    -> Choose a move partly because it serves a selected plan
    -> Record the decision and its evidence
    -> Explain that decision
    -> Observe how the human changes the plan's prospects
```

The selected plan must have a real role in choosing the move. Merely attaching a plan label to the engine's preferred move is not sufficient.

### 1.3 Illustrative feedback

The following messages describe the desired style; they are not claims about a supplied chess position:

> "I'm bringing another attacker toward your isolated pawn. My aim is to make it harder for you to defend."

> "Your exchange removes one of the attackers I needed. I have to find another way to create pressure."

> "You stopped my pawn advance, but your rook is now undefended. The defensive idea has a tactical cost."

> "You allowed my plan, but created a more urgent threat against my king. I cannot safely continue."

> "That was not my original plan, but your last move has given me a new opportunity."

The important content is the relationship between a decision, a goal, a response, and a consequence. The app should not invent a strategic story for every move.

### 1.4 Selected name and product positioning

| Element | Decision |
|---|---|
| Product name | **Chess & Cocoa** |
| Descriptor | **a cozy chess teaching app** |
| Full title | **Chess & Cocoa - a cozy chess teaching app** |
| Suggested supporting copy | "Play a game. Understand the moves." |

The product name and descriptor are selected by the owner. Use them consistently in product documentation and player-facing identity; the supporting copy is only a recommendation. Use the short name where the full title would crowd an interface. Store metadata, a domain, and an Android application ID remain separate implementation and release decisions.

The intended feeling is a quiet game with a patient, knowledgeable companion: room to think, clear explanations when useful, and freedom to explore without embarrassment. "Cocoa" communicates warmth and approachability. It does not require a beverage mechanic, a cafe simulation, a child-oriented character, or a decorative scene around the board.

**Cozy is a requirement for the interaction, not just a visual theme.** It should be evident in pacing, feedback, control over assistance, and the ability to pause and return. It does not mean an opponent that always loses, an analyst that ignores errors, or a coach that calls every move good.

The selected name is not a statement of trademark clearance or domain/handle availability. Keep naming clearance as a pre-release task rather than treating selection as verification.

---

## 2. Requirements, assumptions, and boundaries

### 2.1 Non-negotiable conceptual requirements

| ID | Requirement |
|---|---|
| R1 | Plans influence the computer's actual move selection; they are not only retrospective annotations. |
| R2 | Record the selected plan and decision evidence before the human's response is known. Never rewrite the earlier record to imply foreknowledge. |
| R3 | Evaluate overall move quality independently of the move's effect on the computer's plan. |
| R4 | Represent plans as goals with conditional actions, dependencies, and abandonment criteria, not fixed principal variations. |
| R5 | Support material chess claims with position-specific facts, legal continuations, or clearly qualified analysis. |
| R6 | Distinguish testing a plan-following policy from evaluating its first move with unrestricted engine continuation. |
| R7 | Retain plans across moves when justified, and explain significant revisions, interruptions, completions, or abandonment. |
| R8 | Abstain from unsupported explanations. A limited, factual explanation is preferable to an invented intention. |
| R9 | Keep the analyst's configuration independent of opponent difficulty. Weak play must not imply weak checking. |
| R10 | Deliver learning through playable games, selective feedback, and interactive evidence rather than compulsory lectures. |
| R11 | Tie analysis and explanations to an exact game revision and history. Discard stale results after moves, takebacks, or branch changes. |
| R12 | Test both chess correctness and explanatory faithfulness; do not equate fluent prose or playing strength with teaching quality. |
| R13 | Make the cozy identity concrete through untimed default play, selective non-scolding feedback, optional assistance, and reliable pause/resume. Do not require streaks, urgency, or punishment for mistakes. |
| R14 | Keep the board and explanations readable and accessible. Decorative motion, sound, and haptics must be optional; neither color nor a sensory effect may be the sole carrier of essential information. |
| R15 | Keep warmth separate from chess judgment. Friendly wording must not alter evidence, conceal an important consequence, fabricate praise, or silently change opponent difficulty. |

### 2.2 Recommended starting assumptions

Target Android phones first, standard chess only, and single-player games against the application. Prefer an offline-capable core, deterministic explanation templates, and a small hand-authored plan repertoire. Use an established engine as the analyst rather than writing a new tactical search engine.

Kotlin with Jetpack Compose is a proposed application stack, not an irreversible requirement. A separate desktop research harness may be useful for testing planner ideas. It must not accidentally make a Python runtime or a remote service mandatory for the Android product.

The product name and cozy positioning in Section 1.4 are fixed. Exact artwork, palette values, typography, and optional sensory features are not. The app's business model, distribution license, minimum Android version, and supported device range have not been decided. Resolve those explicitly during implementation planning.

### 2.3 Non-goals for the first release

Do not make the first release depend on unrestricted strategy invention, training a new chess neural network, a grandmaster-strength custom engine, multiplayer, opening-book mastery, a comprehensive curriculum, or an open-ended conversational assistant. The cozy identity must not expand the MVP into a cafe simulator, mascot relationship system, collectible-drink economy, or elaborate animated environment.

Do not promise to identify a human player's actual intention. The system can describe a move's consequences and possible purposes, but recognizing a defensive effect does not prove the player anticipated it.

Do not require a long-term plan in every position. Forced responses, immediate tactics, unsupported positions, and resource-limited analysis all need honest fallback behavior.

---

## 3. Essential distinctions

### 3.1 Board fact, tactical consequence, threat, and plan

A **board fact** is a directly checkable statement about the current state: a square is occupied, a piece attacks a square, or a particular move is legal.

A **tactical consequence** concerns what follows after moves are played: a fork wins material, a defender can be removed, or a capture is answered by checkmate. Detecting an attack pattern alone does not establish its consequence.

A **threat** is a harmful continuation available under specified circumstances. A legal example showing what happens after one inadequate response establishes that example, not that every response loses.

A **plan** is a goal pursued through a conditional policy: how the computer intends to choose actions as the position changes. It includes why the goal is useful, what it depends on, how the opponent may resist, and when to stop pursuing it.

A **plan episode** is one concrete attempt to pursue a plan in a game. A reusable template such as "pressure a weak pawn" can instantiate many different episodes with different targets and pieces.

### 3.2 Intention versus justification

For the application's own opponent, intention means a recorded goal that influenced its decision. It is an application-level construct, not a claim about consciousness or the internal meaning of a neural evaluation.

An engine may support the move without supporting the proposed reason for it. A rook move can be acceptable because of a defensive resource even when the planner proposed it as preparation for an attack. The system must validate the proposed purpose separately.

For moves played by a human or an external engine, purposes remain hypotheses unless explicitly supplied. Prefer "Your move prevents..." to "You correctly spotted...".

### 3.3 Immediate action versus long-term objective

"Move the knight to this square" is an action or subgoal. "Establish a useful knight that attacks this target and cannot easily be displaced" is a strategic objective.

Long-term does not mean a promised sequence of many moves. Start with modest multi-turn goals and bounded evidence. A plan can involve preparation, temporary lack of visible progress, alternative routes, and eventual abandonment.

---

## 4. Player experience and feedback timing

### 4.1 Normal game loop

The player starts a game and makes legal moves on a conventional chessboard. After a meaningful event, the app shows a short explanation connected to the relevant pieces and squares.

A message can expand into a small evidence view: highlighted dependencies, a playable continuation, or a comparison with another move. Exploration must not modify the live game unless the player explicitly chooses a takeback or a new branch.

The computer's subsequent explanations should refer back to an ongoing plan when useful. At the end of a game, a short review should reconstruct a few important plan episodes rather than enumerate every numerical evaluation change.

### 4.2 Guidance settings

Use a teaching-oriented default, with explicit control over how much future information is revealed.

| Setting | Intended behavior |
|---|---|
| Guided play | Explain selected computer intentions and major response effects during play. Hints may reveal future threats. |
| Reduced guidance | Explain completed effects and answer requests, but avoid automatically revealing the next tactic. |
| Review | Reveal stored intentions, tested alternatives, and plan transitions without spoiler restrictions. |

These are recommended behaviors, not necessarily three separate screens or independently implemented modes. A single disclosure setting can support the first version.

The opponent and the coach have different presentation roles even when they share analysis. The coach may explain that a stronger response existed than the difficulty-controlled opponent chose. It must not imply that a weak reply proves the human's move was objectively sound.

### 4.3 Attention and tone

Default to one primary teaching point per event. Prioritize immediate consequences and consequential plan changes over minor positional observations. Repeated statements about an unchanged goal should be suppressed.

Use warm, plain-spoken, concrete language: patient rather than clinical, but never patronizing or falsely reassuring. Explain terminology on demand, with examples on the board. Do not automatically interrupt every move with a quiz, praise routine actions, or punish exploration with scolding. State serious consequences plainly; a quiet explanation of checkmate is more useful than either an alarm or a euphemism.

Takebacks, optional hints, and "show me why" interactions should be supported without corrupting the original decision history. Track whether help was shown before treating a move as evidence of independent understanding.

### 4.4 Cozy interaction defaults

The following interaction design implements R13. Exact control labels and layout are recommendations; the untimed default, learner control, and non-scolding feedback are requirements.

| Moment | Desired behavior |
|---|---|
| Starting or returning | Make "Play" and "Continue" easy to find. Do not gate a first game behind a lesson, quiz, or lengthy setup. |
| Thinking | Default to no chess clock. Do not pressure the player to move with idle prompts or time-based penalties. The computer can respond promptly; do not add artificial waiting to appear relaxed. |
| Receiving feedback | Show one brief, nonmodal teaching point when useful. Offer "Show me why" and a way to reduce commentary. Do not force acknowledgement after each move. |
| Making a mistake | Explain the consequence, then allow continued play, evidence exploration, or an explicit takeback. Never undo the move automatically or replace honest feedback with consolation. |
| Asking for help | Make hints voluntary and reveal information progressively. Separate guidance level from opponent strength; requesting help must not silently weaken the opponent. |
| Finishing or leaving | Offer a short review of meaningful moments. Support leaving and resuming without streak-loss messages, guilt, or a demand to finish a lesson. |

Keep centipawn numbers and engine jargon out of the default feedback surface; offer numerical detail as an optional expansion. This changes presentation, not what the independent analyst checks. If a game ends in a loss or an analysis identifies a decisive threat, say so clearly.

### 4.5 Visual and sensory direction

Favor a restrained, warm visual direction: cocoa and cream tones with muted accents are a starting suggestion, not a fixed palette. The board remains the dominant element, with familiar, distinguishable chess pieces, readable notation, and a small explanation area. A mug or steam motif can appear in branding; it must not obscure squares or replace tactical symbols. Avoid a busy cafe backdrop, excessive ornament, and red flashing error states.

Readability takes precedence over atmosphere. Validate board contrast, piece recognition, text scaling, focus order, and accessible descriptions of squares, moves, and teaching events. Pair color highlights with shapes, labels, or equivalent descriptions. Do not require the player to distinguish subtle brown shades to understand a threat. The implementation plan must specify an accessible interaction approach, not just an attractive palette.

Default to no background music or decorative ambience. Sound and haptics, if included, are optional and separately controllable. Respect reduced-motion preferences, avoid looping decorative animation, and ensure that disabling all sensory embellishments leaves the complete game and explanations usable. Elaborate themes, narration, and ambience are not MVP dependencies.

### 4.6 Voice and example copy

The opponent may say "I" when referring to its recorded plan. The coach should explain effects without boasting, pretending to read the human's mind, or manufacturing a friendship narrative. Warmth should come from patience and useful explanations, not constant praise, distracting decoration, patronizing language, or repeated cocoa puns.

The following are illustrative wording examples; each concrete chess claim still requires evidence from the actual position.

| Avoid | Prefer |
|---|---|
| "Blunder! You missed an obvious threat." | "That move leaves your bishop undefended. I can capture it next." |
| "You fell right into my trap!" | "Moving that defender lets me carry out the attack I was preparing." Only use this when the earlier plan record supports it. |
| "Brilliant! You understood my plan." | "Your move takes away the square my knight was heading for." |
| "Oops, a tiny setback!" when the game is lost | "That allows checkmate. You can replay the position to explore another response." |

Use ordinary chess vocabulary inside explanations. The identity should make chess easier to approach, not rename checks, captures, or mistakes after drinks. Familiar controls such as "Hint," "Show me why," and "Continue" are preferable to themed labels that need interpretation.

---

## 5. Recommended system architecture

```text
                     Android game interface
                              |
                       Game session controller
                              |
                     Authoritative game state
                         /               \
                Feature analysis      Engine adapter
                         \               /
                      Strategic planner
                              |
                     Plan-aware move selector
                              |
                       Decision / plan log
                              |
         Human response -> Independent move assessment
                              |
                      Plan-impact assessment
                              |
                   Evidence and claim assembly
                              |
                         Teaching policy
                              |
                Templates and board visualizations
                              |
                 Optional constrained language layer
```

The arrows describe responsibilities, not a requirement to implement separate services.

| Component | Responsibility |
|---|---|
| Game core | Legal moves, outcomes, state transitions, notation, history, and branches. |
| Feature analyzer | Position-specific relations and candidate tactical or positional motifs. |
| Engine adapter | Bounded analysis, candidate evaluation, cancellation, and normalized results. |
| Strategic planner | Generate, bind, test, retain, revise, and retire explicit plan instances. |
| Move selector | Choose the actual computer move using quality constraints and plan-related criteria. |
| Move assessor | Judge human moves against strong alternatives, independently of the computer's chosen plan. |
| Plan-impact assessor | Identify which dependencies, routes, benefits, or priorities changed after the human's move. |
| Evidence assembler | Build supported claims and associate them with positions, pieces, moves, and limitations. |
| Teaching policy | Select what to explain, when to reveal it, and how much detail to show. |
| Renderer | Produce template text and interactive board evidence from the same structured claims. |
| Persistence | Preserve games, decision records, plan episodes, settings, and optional learning observations. |

Stockfish is a suitable analyst candidate: its UCI interface exposes evaluations and continuations, `MultiPV` returns multiple candidate lines, and `searchmoves` restricts root candidates. The application's planner must supply the goal representation and multi-turn control. [S1]

---

## 6. Semantic position analysis

### 6.1 Start with facts that can be checked

The initial feature layer should identify piece locations, attacked squares, legal captures, checks, absolute pins, changed lines of attack, and candidate targets. Add a small set of tactical recognizers and positional features needed by the supported plan families.

Distinguish geometrical attacks from legal captures and effective defensive resources. For example, the `python-chess` attack APIs count pinned pieces as attackers, while its pin helpers identify absolute pins. Those APIs are useful prototype building blocks, but their outputs must not be treated as complete tactical explanations. [S4]

A pinned defender may be unable to make a particular capture. Conversely, simply counting attackers and defenders does not establish whether an exchange is favorable. Treat static exchange estimates and structural heuristics as filters, then verify consequential claims with legal analysis.

### 6.2 Candidate tactical coverage

A practical initial set is immediate mate threats, undefended-piece losses, forks, and absolute-pin consequences. Store the participating pieces and causal relationships, not only motif names.

Lichess's puzzle tagger contains rule-based detectors for motifs including forks, hanging pieces, discovered attacks, and pins. It operates on puzzle continuations and emits tags, so use it as a reference or evaluate reuse explicitly rather than assuming it is a general position-explanation API. [S6]

### 6.3 Facts are not automatically teaching points

A feature such as an isolated pawn is a candidate consideration, not proof of a decisive weakness. An open file is not automatically useful. A move that develops a piece is not necessarily good.

Each proposed explanation must connect the feature to this position: what action becomes available, what response changes, or what benefit the supported plan can obtain.

For immediate threats, generate legal response examples. Do not create a fictitious "opponent passes" position and present the resulting tactic as proof. A diagnostic that changes the side to move may help candidate discovery, but every displayed example must belong to a legal continuation from the real game.

---

## 7. Plan representation and lifecycle

### 7.1 Plan template

A template describes a reusable strategic idea. It should define:

| Field | Purpose |
|---|---|
| Applicability rules | Identify positions where the idea is worth considering. |
| Target binding | Select concrete pieces, squares, files, or pawns. |
| Goal predicate | Describe the desired outcome and why it is useful. |
| Dependencies | Record assumptions on which the plan relies. |
| Subgoals and action generator | Propose legal next actions in different states. |
| Progress features | Measure relevant preparation and goal achievement. |
| Constraints | Identify unacceptable costs and tactical failures. |
| Opponent challenges | Suggest responses that specifically test the plan. |
| Completion and abandonment rules | Decide when the episode is finished or no longer justified. |
| Explanation vocabulary | Define claims and terms that the template can support. |

The goal predicate must be specific enough to test. Avoid labels such as "improve the position" unless the implementation can say what improvement means in the instantiated case.

### 7.2 Example: establishing a knight on a useful square

**Goal:** Establish a selected knight on a target square where it contributes to a named tactical or positional objective.

**Potential subgoals:** Clear a route, improve support, remove a relevant defender, and complete the maneuver.

**Dependencies:** A viable route exists; the maneuver is tactically affordable; support remains available; the target still has value; opposing counterplay does not become more urgent.

**Success:** The knight reaches a useful role and analysis supports that role against tested responses. Merely visiting the square is not sufficient.

**Revision:** Choose another route or target while recording what changed.

**Abandonment:** The target loses value, the route becomes unacceptable, or a different priority dominates.

Progress need not increase on every move. A preparatory exchange or a necessary defensive action can temporarily delay the goal. Nevertheless, repeatedly labeling unrelated moves as preparation is not acceptable.

### 7.3 Recommended initial strategic families

Implement one or two narrow families well: pressure against a concrete pawn target, and a knight maneuver toward a useful square. Select curated positions where the intended dependencies can be demonstrated and tested.

A later extension can address open-file use, passed-pawn creation, pawn breaks, or specialized endgame goals. Those are not automatically covered by the initial templates.

### 7.4 Lifecycle

Use explicit statuses such as `proposed`, `active`, `suspended`, `completed`, `abandoned`, and `superseded`.

A plan becomes active only when a decision actually uses it. After each move, reassess its dependencies and expected value. Suspension means an interruption may be temporary; abandonment means this episode is no longer being pursued. Resuming a suspended plan requires revalidation.

Retain a justified plan rather than switching because of tiny, unstable score differences. Apply continuity only within acceptable chess quality; it must not cause stubborn commitment to a refuted idea.

For the MVP, one primary active plan is enough. Secondary tactical obligations can be recorded separately. A general hierarchy of competing plans is an extension, not a prerequisite.

### 7.5 Immutable intention history

Before revealing the computer's move and accepting the human's response, record the selected goal, bound targets, important dependencies, considered challenges, chosen action, and analysis references.

Later updates create new plan revisions or events. They must not edit the original intention record. Do not claim that a human move was anticipated unless it, or a clearly recorded response class, was considered beforehand. Even then, distinguish recognizing a response class from predicting the exact move.

---

## 8. Validating plans rather than attractive first moves

### 8.1 Two different questions

The analyst asks:

> How well does this candidate move stand up when subsequent play is unrestricted?

The plan validator asks:

> Can our policy continue pursuing its stated goal against relevant resistance, at an acceptable cost?

Both checks matter. A first move can be sound even though the proposed plan fails. If the only good continuation abandons the goal, mark that limitation rather than crediting the original plan with the unrelated good result.

Stockfish's `searchmoves` only restricts the initial candidate moves of that search. It does not impose a goal or policy on later turns. Multi-turn plan testing therefore belongs in application-controlled rollouts or a deliberately modified search implementation. [S1]

### 8.2 Bounded adversarial rollouts

For each promising plan:

1. Generate candidate next actions and screen their overall quality.
2. At opponent turns, test strong engine responses and responses that attack the plan's dependencies.
3. At the computer's later turns, use the plan's conditional action policy, with independent tactical checking.
4. Record whether the goal advances, becomes delayed, requires revision, fails, or is abandoned.
5. Stop at a defined budget, terminal state, or meaningful goal boundary.

The simulated policy must match the policy intended for actual play. Do not let Stockfish play both sides after the first move and describe that as validation of your planner.

A defensive response that forces abandonment can demonstrate good adaptive behavior, but not successful execution of the abandoned goal. Keep those outcomes separate.

### 8.3 Response selection and coverage

Use a mixture of strong responses and strategically relevant challenges. Several engine lines may express the same idea, so organize sampled responses by what they do as well as by their numerical rank.

A bounded set of tested branches is not a proof against every legal reply. Do not treat the fraction of sampled successful branches as a probability that the plan will work. Engine ranking alone is also not a model of how often a beginner chooses a response.

Record the explored horizon, selection method, tested branches, known refutations, and unresolved conditions. Claims should reflect those limits.

### 8.4 Assessment dimensions

Return a vector rather than a single unexplained "plan score":

| Dimension | Question |
|---|---|
| Chess quality | What losses or benefits occur in the tested continuations? |
| Goal progress | Does the policy actually reach useful subgoals? |
| Resistance | Which tested responses disrupt it, and how? |
| Cost of persistence | What is lost by insisting on this goal rather than adapting freely? |
| Contingency quality | Are alternative routes or revisions still supported? |
| Evidence coverage | How much of the claim was tested, and what remains uncertain? |

An implementation may use internal scores for ranking, but these dimensions must remain inspectable. Do not present arbitrary heuristic scores as calibrated probabilities.

---

## 9. Computer move selection and difficulty

### 9.1 Quality first, then plan-related preferences

Estimate the quality of candidate moves with an independent analyst. Within a configured acceptable set, prefer moves that support a tested plan, preserve useful continuity, and produce understandable play.

A conceptual selection rule is:

```text
acceptable_moves = candidates that satisfy the quality policy

chosen_move = preferred acceptable move according to:
    supported goal contribution
    resistance to tested challenges
    continuity with a still-valid plan
    instructional relevance
    variety, where appropriate
```

This is a constrained preference system, not permission to overwhelm a chess evaluation with an arbitrarily large "follows the plan" bonus.

Plans may introduce candidate moves that are absent from the engine's initial shortlist. Evaluate those moves explicitly rather than rejecting them solely because they were not initially returned.

### 9.2 Overrides and fallback

Immediate obligations take precedence over optional strategy. When the selected plan must be interrupted, record the reason and explain it when relevant.

When no supported plan offers an acceptable move, choose a checked fallback and expose only the claims that are available. A factual tactical explanation or silence is preferable to fabricated strategic intent.

The quality policy must handle already-lost positions and forced moves without producing an empty move set. An intentional, verified sacrifice is not equivalent to accidentally leaving a piece undefended. Define how terminal outcomes, tactical losses, and positional concessions are compared.

### 9.3 Strength is a separate control

Use an undiluted analysis configuration to evaluate the human and check explanations. Control playing difficulty in the opponent's move-selection policy, not by treating a weakened analyst as the authority.

A coherent-plan preference among nearly optimal moves may still produce an opponent that is too strong for beginners. Difficulty calibration is therefore a separate empirical task. Do not claim a target Elo without measurement.

For the first version, prefer a small number of clearly described difficulty profiles over elaborate human-error simulation. If a profile permits a suboptimal computer move, the coach must not falsely call it best or assume the human's previous move was sound because the computer failed to punish it.

---

## 10. Assessing the human's move

### 10.1 Axis A: overall quality

Let `s` be the position immediately before the human chooses a move, and let `h` be the move played. Define `Q_H(s, h)` as the analyst's estimate for that candidate, consistently normalized to the human's perspective.

For comparable, finite centipawn scores:

```text
estimated_loss(h) = Q_H(s, best_found_move) - Q_H(s, h)
```

This is an analysis estimate, not a mathematical proof of the position's value. Compare candidates from the same parent position where possible, explicitly analyze the played move, and use compatible budgets and settings. If a restricted search makes the played move appear better than the earlier best-found move, recheck or downgrade confidence rather than hiding the discrepancy.

Scores must carry their perspective. The `python-chess` engine interface provides perspective-aware score objects and separate centipawn and mate score types, illustrating a useful adapter design. [S5]

Do not subtract mate scores as centipawns. Preserve terminal outcomes, mate information, bounds, search metadata, and uncertainty separately. "No issue found within this budget" is not the same as "objectively best."

Thresholds for labels such as inaccurate or losing require calibration. Stockfish's optional WDL output models outcomes under specified engine self-play conditions; it is not a beginner's personal probability of winning. [S1]

### 10.2 Axis B: effect on the computer's recorded plan

Compare the human's move with the plan revision that existed before the response. Identify changed dependencies and consequences, such as:

| Impact | Meaning |
|---|---|
| Prevents | A necessary route or condition no longer works in the supported analysis. |
| Delays | Additional preparation is now needed; the goal may remain viable. |
| Reduces benefit | The goal remains achievable but is less useful. |
| Forces adaptation | The computer needs a different route, target, or plan. |
| Creates urgency | A new obligation takes precedence over the original goal. |
| Enables or accelerates | The response removes an obstacle or provides a new resource. |
| No supported material effect | Analysis found no consequential interaction with the recorded plan. |
| Unknown | The available evidence is insufficient. |

Impacts may coexist. A move can delay one route while creating a more serious opportunity elsewhere.

### 10.3 Keep the axes independent

| Plan impact | Overall assessment | Teaching interpretation |
|---|---|---|
| Disrupts the computer | Favorable | The response addresses the idea without an unacceptable cost. |
| Disrupts the computer | Unfavorable | The response succeeds locally but creates a worse problem. |
| Allows the computer | Favorable | The human obtains enough benefit elsewhere or creates a more urgent threat. |
| Allows the computer | Unfavorable | The human permits a consequential opportunity without sufficient compensation. |

"You played into my hands" is only appropriate when the supported analysis establishes both the enabling effect and its harmful significance. Simply allowing a goal is not enough.

### 10.4 Counterfactual assessment

Evaluate three related questions:

**Persistence:** What happens after the actual response if the computer continues pursuing its old goal?

**Adaptation:** What happens if the computer is free to change plans or exploit a new opportunity?

**Alternatives:** How do a few reasonable human alternatives from the same parent position compare?

This supports a precise explanation such as: "Your move prevents my original idea, but allows a stronger capture. Another defense avoids both problems."

Comparisons must start from legal alternatives to the same decision. Do not confuse the natural passage of a turn with a causal effect of the chosen move. Feature changes can identify a proposed cause; continuation analysis must establish why it matters.

### 10.5 Do not retroactively invent traps

A newly available tactic may be unrelated to the prior plan. Label it as a new opportunity. An immutable prior decision record should make it impossible for a renderer to claim that every human mistake was something the computer had planned all along.

---

## 11. Proposed domain contracts

These are conceptual contracts. The implementation planner should choose actual language types, serialization, and storage while preserving their meaning.

### 11.1 Core records

| Record | Required information |
|---|---|
| `GameState` | Game and branch IDs, revision, initial position, move history, current position, side to move, and outcome state. |
| `PositionFacts` | Typed relations, referenced pieces and squares, producing detector, and position revision. |
| `AnalysisResult` | Root state reference, engine and network identity, options, search budget, score perspective, score kind and bounds, candidates, variations, and completion status. |
| `PlanTemplate` | Applicability, binding rules, goal, dependencies, action generation, progress features, challenge generation, and lifecycle rules. |
| `PlanInstance` | Stable episode ID, template version, owner, bound entities, current subgoals, status, evidence, and revision history. |
| `PlanAssessment` | Tested actions and responses, progress outcomes, persistence cost, refutations, adaptations, coverage, and limitations. |
| `DecisionRecord` | Pre-move state, selected move, selected plan revision or fallback reason, candidate comparisons, deciding criteria, and evidence available at decision time. |
| `MoveAssessment` | Human move, independent quality assessment, plan-impact findings, tactical findings, compared alternatives, and uncertainty. |
| `ExplanationClaim` | Claim type, participants, supporting evidence IDs, certainty class, scope, and disclosure restrictions. |
| `TeachingEvent` | Selected claims, parent game revision, rendered text, visualization instructions, disclosure timing, and concept tags. |
| `ExperiencePreferences` | Guidance and commentary level, optional numerical detail, readability/theme preferences, motion/sound/haptic settings, and settings schema version. Keep these distinct from opponent strength and analyst configuration. |

Represent unavailable information explicitly. Missing evidence must not silently become a positive finding, a zero loss, or a neutral plan impact.

### 11.2 Important representation choices

Use machine-readable move identities, with notation generated for display. Store entity references in a way that survives movement, capture, and promotion. A square alone is not a stable piece identity across an episode.

Preserve history, not only the visible board. Stockfish's documentation recommends supplying move history for repetition handling. [S1] The implementation should also distinguish analysis caches from narrative state: a reusable position evaluation does not make two plan histories interchangeable.

Cache keys and result validation must account for relevant rule state, history, analysis configuration, and revision. A result for a different branch or an insufficiently specified repetition context must not be treated as interchangeable.

### 11.3 Suggested component interfaces

```text
analyze(gameState, candidateMoves?, budget, analystConfig)
    -> AnalysisResult

extractFacts(gameState)
    -> PositionFacts

proposePlans(gameState, facts, activePlan?)
    -> PlanInstance candidates

testPlan(gameState, plan, responsePolicy, budget)
    -> PlanAssessment

selectComputerMove(gameState, analyses, planAssessments, difficulty)
    -> DecisionRecord

assessHumanMove(parentState, move, priorDecision, priorPlan, budget)
    -> MoveAssessment

buildClaims(decisionOrAssessment, evidence)
    -> ExplanationClaim records

selectTeachingEvent(claims, learnerContext, disclosureSetting)
    -> TeachingEvent or no event
```

All long-running product operations should support cancellation and carry a state revision. The game controller must reject results that no longer belong to the current state.

---

## 12. Evidence, explanation generation, and uncertainty

### 12.1 Evidence-bearing claims

Separate claims from their wording. An explanation about a removed defender should identify the defender, the previously supported piece or square, the move that changed the relation, and the continuation supporting the consequence.

The board highlights and text should be generated from the same evidence. Otherwise, correct prose can still be paired with an incorrect arrow or a variation beginning from the wrong position.

Use certainty classes such as:

| Class | Meaning |
|---|---|
| Rule-verified | Directly checked through legal state transitions or an appropriately exhaustive rules test. |
| Analysis-supported | Supported by bounded search with its scope recorded. |
| Heuristic | A candidate interpretation or structural observation, not a demonstrated consequence. |
| Unknown | Not established with the available evidence. |

A legal mating continuation against one response is not proof of a forced mate against all responses. A sampled strategic plan is not guaranteed. Words such as "only," "forced," "cannot," and "wins" require evidence appropriate to their scope.

### 12.2 Templates first

The MVP should render deterministic templates using typed slots. For example:

```text
"Your {move_or_piece_change} removes {dependency}.
That means my plan to {goal} now {supported_effect}."
```

Do not fill slots with speculative effects merely to complete the sentence. A template may be suppressed when its required claims are unavailable.

The template library should support a brief explanation, an expanded explanation, and a related transferable idea. Avoid turning every event into a lecture about the whole position.

Apply the Chess & Cocoa voice from Section 4.6 at the rendering layer. Store brand copy and presentation resources separately from semantic rules, engine scores, and intention records. A warmer wording variant must preserve participants, consequences, uncertainty, and disclosure restrictions. Deterministic templates can supply the intended tone; a language model is not required to make the app welcoming.

### 12.3 Optional language-model layer

A later language model may adjust vocabulary, combine already-supported claims, or answer constrained questions using supplied evidence. It should not be responsible for inventing moves, deciding legality, estimating evaluations, or recovering the true historical intention.

Supply structured claims and validated variations, not just a FEN and an evaluation. Every concrete move mentioned in generated text must be validated in its stated position. General prose faithfulness is harder to verify than syntax, so retain template fallback and audit generated claims. Do not assume a prompt or a second language model guarantees correctness.

Treat imported PGN comments and other external text as untrusted data rather than instructions. Keep future language services outside the essential offline game loop and never package service secrets into the client.

---

## 13. Worked examples and fixtures

### 13.1 Strategic interaction example

This is a design scenario, not an engine-evaluated position.

The computer activates a plan to pressure a pawn using two pieces. Its decision log records the target, the expected second attacker, and the condition that both attackers can operate without allowing an urgent counterthreat.

The human exchanges one attacker. The feature layer records that the attacker is gone. Plan testing finds that the remaining pieces no longer generate the intended pressure in the tested lines. Independent analysis finds no overriding tactical cost for the human's exchange.

The app can explain:

> "Exchanging that piece removes an attacker my plan depended on. I no longer have the same pressure against your pawn."

A second version of the scenario has the same exchange but leaves the human's rook vulnerable. The app should retain the plan-disruption finding while changing the overall assessment:

> "The exchange stops my original pressure, but now I can win your rook. You addressed one problem while creating a more serious one."

The implementation must supply reviewed concrete positions for both cases. The same visible strategic effect must not force the same quality label.

### 13.2 Concrete tactical fixture

Use the following legal opening sequence as a rules-level fixture, with the computer playing White and the human Black:

```text
1. e4 e5 2. Bc4 Nc6 3. Qh5
```

Position after `3. Qh5`:

```text
r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3
```

For this fixture, construct a prior decision record in which White chose to create the conditional mating threat on f7. This is a test setup, not a claim that an implemented planner has already selected or evaluated the move.

**Response A:** `3...Nf6` permits `4.Qxf7#`. The explanation should identify that the knight attacks the queen but does not stop the mating reply, and that the bishop on c4 protects the queen on f7.

**Response B:** `3...g6` blocks the queen's immediate route from h5 to f7. This supports a specific finding that the immediate threat is interrupted. It does not, by itself, establish the move's overall quality or the success of any longer-term plan.

A proposed claim object for Response A is:

```json
{
  "claimType": "allows_mate_in_one",
  "parentPositionRef": "scholars-threat-before-black-response",
  "playedMoveUci": "g8f6",
  "evidencePosition": "after_played_move",
  "continuationUci": ["h5f7"],
  "supportingPieceSquares": ["c4"],
  "highlightSquares": ["h5", "f7", "c4"],
  "relatedPriorPlanId": "fixture-f7-threat",
  "certaintyRequired": "rule_verified"
}
```

The test harness must validate the fixture and the continuation before accepting that certainty classification. No numerical engine scores are supplied here.

This fixture tests legal evidence and response interpretation. It does **not** demonstrate long-term planning. Separate multi-turn strategic fixtures are required.

---

## 14. Android delivery, performance, and persistence

### 14.1 Local engine integration

Official Stockfish downloads include Android ARM builds. Android's NDK supports native C/C++ components. These are useful starting points, not proof that a particular engine package or integration method will work unchanged in the intended application. [S3, S8]

The implementation planner should choose and validate a supported integration approach, including the native boundary, command transport, lifecycle, cancellation, packaging, and device architectures. Keep the conceptual engine adapter independent of whether analysis is in-process, in another process, or temporarily provided by a development backend.

A backend can simplify experimentation, but introduces connectivity, operating-cost, and data-handling requirements. Prefer local gameplay and template explanations unless measurements justify a different product decision.

### 14.2 Bounded work and graceful degradation

Candidate generation, plan rollouts, and counterfactual analysis can multiply the number of engine searches. Start with a small shortlist and explicitly budget the total work, not only each individual search.

An experimental configuration might consider two to four plan candidates, a few opponent challenges, and a strategic horizon of a few computer turns. These are starting hypotheses for benchmarking, not validated performance targets or sufficient analysis for every position.

Prioritize legal gameplay and immediate tactical checking. Use remaining budget for plan validation and alternate explanations. When resources are insufficient, reduce explanation coverage rather than increasing confidence in unfinished analysis.

Do not block board interaction with engine work. Cancel stale work, handle interruption and resumption, bound memory and cache size, and test battery and thermal behavior on actual target devices. Avoid unlimited analysis during the human's turn by default.

Separate logical analyst settings from playing settings even when the implementation shares one engine instance. Prevent mutable options from leaking between roles; use isolated sessions or a serialized, explicit configuration protocol.

### 14.3 Save, resume, and branch behavior

Persist the game and its plan/decision history together. A resumed game must not reconstruct a fictional previous intention from the current board.

A takeback restores the appropriate plan state on the selected branch. New play creates new decisions rather than overwriting the abandoned line. Analysis views use separate branch state and cannot silently advance the live game.

Persist experience preferences separately from game history and analyst configuration. Returning to a game should restore the chosen guidance and sensory settings. Changing a theme or commentary level must not invalidate chess evidence or rewrite the opponent's earlier intention.

Support rule outcomes and special moves through the authoritative game core. The planner must not implement a second, inconsistent set of legality rules.

### 14.4 Licensing and data decisions

Stockfish is GPLv3, and the Lichess puzzle-generation repository is AGPLv3. Review the exact dependencies, copied or adapted code, distribution model, and applicable obligations before shipping. [S2, S7]

Make licensing a release gate and an early architecture decision, not a footnote after native integration. Do not assume that a process boundary or a wrapper automatically resolves license compatibility. Obtain qualified review where needed; this document does not determine legal compliance.

Before public release, complete a naming review for Chess & Cocoa in the intended markets and verify the chosen domain, handles, and application identifiers separately. Do not claim that this concept document clears the name.

An offline-first prototype need not collect personal data. Optional telemetry, cloud explanations, and learner profiles require explicit product decisions about collection, consent, retention, and deletion. Age-specific requirements should be evaluated once the intended audience and distribution markets are chosen.

---

## 15. MVP scope and expansion boundaries

### 15.1 Minimum product that preserves the concept

The MVP should support a complete standard game against the app, correct state handling, engine-backed move analysis, a small tactical vocabulary, and at least one genuinely multi-turn strategic plan family.

It must include persisted intentions, separate quality and plan-impact assessments, template explanations, a simple evidence view, and honest fallback outside its semantic coverage. Plan-aware behavior should work in reviewed representative positions; arbitrary-position strategic coverage is not an MVP promise.

The MVP must also establish the cozy baseline: the selected name and descriptor, a restrained readable theme, untimed default games, selective warm-but-accurate templates, control over guidance, and dependable save/resume. Essential feedback must work without optional audio, haptics, or decorative motion. These belong in the first playable experience; custom illustrations, ambient soundscapes, and elaborate branding assets do not.

An implementation containing only tactical labels or post-hoc explanations is a useful technical precursor, but is not yet the distinctive product described here. A cozy visual skin alone does not satisfy the concept either.

### 15.2 Recommended proof-of-concept demonstrations

Demonstrate an entire episode: the computer selects a goal, chooses a move because of it, continues after a nontrivial response, and recognizes a later disruption or completion.

Also demonstrate the contrasting cases where a human move stops the plan but is bad overall, and where allowing the plan is a good response. These cases are central, not optional polish.

Curated starting positions can make these demonstrations repeatable. Ordinary full games should remain playable, with explicitly limited strategic explanation coverage.

### 15.3 Later extensions

Expand the plan repertoire and improve calibration before investing in unrestricted natural-language conversation. Other extensions include richer endgame goals, learner-specific concept scheduling, optional questions about the player's intention, cloud-assisted analysis, and more advanced opponent modeling.

Do not infer mastery from a single good move or from success after a hint. A future learning model should distinguish independent play, assisted play, repeated patterns, and successful transfer to a new position.

---

## 16. Validation and acceptance criteria

### 16.1 Chess correctness

Test legal move handling, notation, promotions, castling, en passant, terminal positions, draw handling, takebacks, and save/resume. Replay every displayed variation against the exact referenced state.

Test score perspective across both colors and alternating turns. Cover mate results, incomplete analysis, bound scores, changing search budgets, and the played move being absent from the first candidate set.

Do not make tests unnecessarily dependent on an exact centipawn value or one preferred move from an evolving engine. Pin versions for reproducibility and test semantic invariants where appropriate.

### 16.2 Semantic correctness

Each detector and plan family needs positive examples, near-miss negatives, and adversarial cases. Include geometrical forks that do not win material, pinned defenders, adequate compensation, defensive moves that fail tactically, and seemingly useful targets that lack practical value.

Evaluate false explanations separately from missing explanations. Early releases should prefer high precision with visible gaps over broad but unreliable coverage.

### 16.3 Decision faithfulness

| Test | Required outcome |
|---|---|
| Plan influence | In suitable controlled positions, changing or removing the selected plan changes candidate preference or the recorded deciding criteria. Attaching labels after selection fails. |
| Temporal integrity | No evidence learned after the human's move appears in the frozen earlier decision record. |
| Persistence | A valid goal survives multiple relevant decisions rather than receiving a new unrelated label each turn. |
| Adaptation | Breaking a dependency leads to a supported revision, suspension, or abandonment. |
| False-plan rejection | A sound first move followed by a failed plan is not accepted merely because unrestricted engine play recovers. |
| Axis independence | Plan disruption does not automatically produce a favorable human-move label. |
| Honest novelty | A new tactical opportunity is not described as a previously intended trap without prior evidence. |
| Abstention | Missing support yields a limited explanation or no explanation, not invented facts. |

Ablation does not require a different move in every position. It should establish that the planner is causally part of the selection mechanism, especially in controlled positions with multiple acceptable choices.

### 16.4 Explanation and user-interface correctness

Every displayed material claim needs traceable evidence. Highlights must identify the right pieces, continuations must start from the right revision, and disclosure settings must prevent unwanted future-move spoilers.

Takebacks and rapid input should not reveal stale explanations. Crash recovery should preserve or explicitly invalidate incomplete decisions rather than manufacture intent.

Include the following cozy-experience acceptance cases:

| Case | Required outcome |
|---|---|
| Unhurried default | A new standard game is untimed, requires no preliminary lesson, and does not nag the player for thinking. |
| Assistance control | Reducing commentary or requesting a hint does not change analyst configuration or silently change opponent strength. |
| Warm but accurate feedback | A losing move receives an honest explanation, not praise or a misleadingly minor description. Rendering variants preserve the same claims and uncertainty. |
| Quiet and accessible play | Essential game state and teaching content remain usable with sound, haptics, and decorative motion disabled; color alone conveys no essential meaning. Test text scaling and the selected assistive interaction approach. |
| Resume and exploration | Guidance preferences survive resume; takebacks remain explicit; exploration does not corrupt the live branch or historical intention. |

Test at least one representative session from first launch through a mistake, optional help, a pause/resume, and a short review. Verify the experience as a flow, not just a collection of individually styled screens.

### 16.5 Learning evaluation

Measure whether players understand the point and can apply it in new positions or against a different response. Also measure interruption burden, hint dependence, and whether explanations improve decisions rather than only subjective satisfaction.

For the cozy positioning, separately evaluate perceived pressure, comfort asking for help, explanation clarity, and whether the presentation feels patronizing or distracting. Comfort is a product goal, not proof of learning; preserve the distinction and do not claim stress-reduction or educational benefits without evaluation.

Useful comparisons include ordinary play without commentary, a post-hoc engine-explanation baseline, and the plan-aware tutor. Those comparisons can separate the value of any explanation from the value of persistent, faithful plan explanations.

No educational improvement is assumed to have been demonstrated by this concept document.

### 16.6 Metrics to define during planning

Track move legality, evidence replay success, claim error rate, explanation coverage, plan-influence evidence, supported plan continuity, justified switching, fallback frequency, and latency on target hardware.

Do not maximize plan lifetime or completion rate in isolation: an appropriate abandonment is good behavior. Do not maximize engagement at the expense of accurate feedback or learner autonomy.

The implementation plan should assign measurable release thresholds and identify which require expert review or user testing. Avoid unsupported numerical performance or learning claims.

---

## 17. Open decisions and principal risks

| Decision or risk | Required treatment in the implementation plan |
|---|---|
| Generic planning may be too ambitious | Start with explicit templates and define supported positions; retain fallback. |
| First-move analysis can validate the wrong rationale | Build policy-controlled plan testing and false-plan regression cases early. |
| Goal metrics can reward meaningless progress | Require named benefits, opponent challenges, and independent quality checks. |
| Strong plan-aware play may overwhelm beginners | Treat difficulty as a separate calibration problem; preserve analyst strength. |
| Too many searches may exceed mobile budgets | Benchmark an end-to-end episode and counterfactual analysis, not only one engine call. |
| Natural-language output may invent claims | Use templates first; constrain and audit any later language model. |
| Narrow templates may produce sparse commentary | Measure coverage and improve targeted repertoire without weakening evidence requirements. |
| Plan continuity may become stubbornness | Define tactical overrides, abandonment, and explicit uncertainty handling. |
| Revealing intentions may remove challenge | Implement configurable disclosure and measure its effects. |
| Dependency licensing may constrain distribution | Resolve before committing to packaging and release strategy. |
| Cozy presentation may become decorative clutter or obscure feedback | Prioritize board readability, restrained copy, accessibility, and quiet-mode acceptance tests. |
| Warmth may become false reassurance or unannounced easier play | Preserve independent analysis, explicit difficulty controls, and evidence-equivalent wording. |
| Selected brand may still require clearance | Resolve naming, domain, handle, and identifier checks before public release; selection is not verification. |

The product name and cozy positioning are selected and should not be reopened by the implementation planner without a concrete conflict. Exact visual assets and palette values remain design decisions. Remaining product decisions include the exact learner audience, acceptable opening assumptions, difficulty profiles, plan families, offline requirement, minimum device class, and distribution model. The next planner should propose reasoned defaults and mark which decisions need owner approval rather than silently treating them as settled.

---

## 18. Handoff instructions for an implementation-planning LLM

### 18.1 Requested output

Produce an implementation plan grounded in this concept, not a replacement concept or a generic chess-app roadmap. Include the following deliverables:

| Deliverable | Expected detail |
|---|---|
| Decisions and assumptions | Recommended defaults, alternatives, tradeoffs, and unresolved owner decisions. |
| Product identity and experience | Use Chess & Cocoa and its selected descriptor. Specify the core game flow, warm-but-accurate copy, guidance controls, quiet defaults, accessibility approach, and minimal theme resources. Distinguish required cozy behavior from optional decoration. |
| Architecture | Concrete modules, responsibility boundaries, data flow, and state/cancellation strategy. |
| Dependency review | Current libraries, engine integration, maintenance status, device support, and license implications, verified from primary sources. |
| Core contracts | Language-specific types for plans, evidence, decision records, analysis results, and assessments. |
| Algorithms | Candidate generation, bounded plan testing, move selection, impact attribution, and teaching-event selection. |
| Risk-reduction experiments | Small tests for plan influence, faithful continuation, mobile feasibility, and explanation precision. |
| Milestones | Dependency-ordered stages with runnable demonstrations and acceptance criteria. |
| Task breakdown | Concrete implementation tasks, likely files/modules, dependencies, and test requirements. |
| Validation strategy | Reviewed fixtures, automated tests, performance benchmarks, ablations, and learning evaluation. |
| Requirement traceability | Show where R1–R15 are implemented and how each is tested. |

A risk-first prototype should demonstrate the plan-aware decision loop before extensive user-interface polish. Include the inexpensive cozy baseline in that playable prototype rather than deferring all tone and interaction decisions to a final reskin. A conventional engine integration alone does not resolve the principal uncertainty.

### 18.2 Planning constraints

Preserve the distinction between chess strength, plan viability, move impact, and teaching value. Do not merge these into a single opaque quality score.

Do not assume an existing library already supplies this entire semantic layer. Treat code examples and record names in this document as proposed interfaces, not available APIs.

Do not invent evaluated chess positions, engine scores, throughput measurements, or learning gains. Generate and check fixtures explicitly. Verify current third-party details rather than depending on version claims from memory.

Do not start by training a model or rewriting Stockfish. Propose those only if a measured limitation justifies them. Keep the MVP small enough to test while retaining at least one genuine multi-turn plan episode.

Do not interpret "cozy" as an instruction to weaken analysis, guarantee wins, gamify drinks, or build a conversational persona. Treat pacing, helpfulness, readability, and learner control as concrete behavior to implement and test. Keep exact art direction flexible within that positioning.

### 18.3 Ready-to-use planning prompt

```text
You are the software architect for "Chess & Cocoa - a cozy chess teaching
app," the Android application described in this document. Produce a
concrete, risk-first implementation plan.

Treat R1–R15 as mandatory conceptual requirements. Distinguish fixed
requirements, recommended defaults, and open decisions. Choose practical
defaults where possible and explain material tradeoffs.

The product must contain a planner that influences the opponent's actual
moves, records its intention before the human responds, tests conditional
plans rather than only their first moves, and evaluates human move quality
independently of plan disruption. Do not replace this with a generic engine
plus post-hoc commentary.

Preserve the selected name "Chess & Cocoa" and descriptor "a cozy chess
teaching app." Make the cozy identity behavioral: untimed default play,
selective patient explanations, optional hints, reliable pause/resume,
readable board-first presentation, and optional sensory effects. Keep
important feedback accurate and explicit. Do not make cozy synonymous
with easy wins or build a cafe simulation. Specify voice examples,
experience settings, accessibility work, and acceptance tests; elaborate
artwork and ambience are not prerequisites.

Specify the architecture, dependency choices, core data models, algorithms,
state and cancellation handling, mobile integration, evidence pipeline,
MVP scope, test fixtures, performance experiments, and release gates.
Include dependency-ordered milestones, concrete tasks, and acceptance tests.
Map the plan back to R1–R15.

Verify current dependency and licensing information from primary sources.
Label assumptions and estimates. Do not fabricate measured results or
pretend that the illustrative scenarios are validated datasets.

Prioritize a runnable, observable multi-turn plan episode and the two-axis
human-move assessment before broad strategy coverage, elaborate UI polish,
or optional language-model features.
```

---

## 19. Verified technical references

These technical references are retained from version 0.1, where they were recorded as checked on October 16, 2026. This branding revision does not refresh the third-party research. The sources support the existing-tool facts cited in this document, not the proposed architecture's effectiveness. Recheck current versions, options, and licenses during implementation planning.

**[S1] Stockfish: UCI Protocol and Stockfish Commands.** Engine communication, candidate lines, restricted root search, history handling, search configuration, and WDL interpretation.  
`https://official-stockfish.github.io/docs/stockfish-wiki/UCI-Protocol-and-Stockfish-Commands.html`

**[S2] Official Stockfish repository.** Source distribution and GPLv3 licensing information.  
`https://github.com/official-stockfish/Stockfish`

**[S3] Official Stockfish downloads.** Available platform builds, including Android ARM.  
`https://stockfishchess.org/download/`

**[S4] python-chess core documentation.** Board APIs, legal-state operations, attack semantics, and absolute-pin helpers.  
`https://python-chess.readthedocs.io/en/latest/core.html`

**[S5] python-chess engine documentation.** Engine integration and perspective-aware centipawn/mate score types.  
`https://python-chess.readthedocs.io/en/latest/engine.html`

**[S6] Lichess puzzle tagger source.** Examples of rule-based motif recognition over puzzle continuations.  
`https://raw.githubusercontent.com/ornicar/lichess-puzzler/master/tagger/cook.py`

**[S7] Lichess puzzle-generation repository.** Project scope and AGPLv3 license declaration.  
`https://github.com/ornicar/lichess-puzzler`

**[S8] Android Developers: Get started with the NDK.** Native C/C++ integration foundation.  
`https://developer.android.com/ndk/guides`

---

## 20. Definition of success

A successful implementation of **Chess & Cocoa** feels like an unhurried game with a patient teaching opponent. It can say what the computer was trying to do, show that the intention influenced its play, recognize how the human changed the situation, and support its feedback with the actual game state and tested consequences.

It can also say, in effect: "My original idea no longer works," "Your response was useful but had another cost," or "I do not have enough evidence for a strategic explanation here."

The defining feature is not that the computer talks about chess, nor that the board has a cozy theme. It is that the experience makes room for learning while its explanations faithfully connect **goals, decisions, resistance, adaptation, and learning**.

**Product promise:** A comfortable place to play, with clear reasons behind the moves.
