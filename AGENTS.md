# AGENTS.md

Chess & Cocoa is a cozy Android chess app that teaches through play. The
computer opponent pursues explicit plans, records them before the player
replies, and explains how the player's move affected them; Stockfish judges
move quality independently. `chess_and_cocoa_concept.md` is the design
brief, `docs/DESIGN.md` maps it onto the code, `docs/experiments/` holds
measured results.

## Build, test, lint

```sh
scripts/build-stockfish-host.sh              # host Stockfish for tests and the harness (once)
./gradlew test testDebugUnitTest lintDebug   # what CI runs, plus assembleDebug/assembleRelease
scripts/build.sh [--debug]                   # APK staged into dist/
./gradlew :harness:installDist && harness/build/install/harness/bin/harness --games 12 --difficulty STEADY
python3 tools/doccheck/checkdocs.py .        # docs: links, anchors, fences, tables
python3 tools/pieces/svg2vector.py --check   # piece drawables match media-sources/pieces
python3 tools/icons/gen_icons.py             # after changing media-sources/icon.png
python3 tools/fonts/build_fonts.py --check  # bundled fonts match media-sources/fonts (needs fontTools)
```

- JDK 17+ (bytecode target 17). Android SDK via `local.properties` or
  `ANDROID_HOME`, with the NDK and CMake versions pinned in
  `gradle/libs.versions.toml`.
- Engine-backed tests read `-Dstockfish.path` (set by Gradle to
  `build/stockfish-host/stockfish`) and skip themselves when it is absent.
- Screenshot tests write `docs/screenshots/*.png`; run
  `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` after UI
  changes and review the images.

## Layers (do not punch through)

`:app` UI -> `:app` session -> `:coach` -> `:engine` / `:chess`. The UI reads
`SessionState` and calls `GameSession`; it never runs chess rules or engine
searches. `:coach` is pure Kotlin (no `android.*`). Only `:chess` decides
legality. Only `:engine` speaks UCI. `:harness` sits on `:coach` and is never
shipped.

## Toolchain quirks

- `compileSdkVersion("android-37.0")` is paired with
  `android.suppressUnsupportedCompileSdk=37`; move them together.
- AGP 9 has built-in Kotlin for `:app`; the JVM modules apply
  `kotlin-jvm`. Robolectric tests use `@Config(sdk = [35])`: SDK 36 fails on
  JDK 21 inside Robolectric's file-descriptor shim.
- Stockfish is vendored unmodified (`third_party/stockfish/PROVENANCE.md`).
  Do not edit it; the network (`*.nnue`) is fetched and verified by
  `scripts/fetch-nnue.sh`, never committed.
- **One engine per process.** The JNI bridge redirects the process-wide
  `std::cin`/`std::cout`; `EngineHost` owns the single engine for the app's
  lifetime. A second `NativeStockfishTransport` throws.
- Release builds ship `arm64-v8a` only; debug adds `x86_64` for the
  emulator. Native libraries are 16 KB page aligned (checked with
  `llvm-readelf -lW`).

## Conventions and footguns

- Every source file starts with `SPDX-License-Identifier: GPL-3.0-or-later`.
  Dependencies must be GPLv3-compatible.
- **Decision records are frozen.** Never edit a `DecisionRecord` or an
  existing `PlanRevision`; new information is a new revision. Explanations
  may only call a plan the reason for a move when `DecisionRecord.planIsReason`.
- **Analysis is deterministic.** One thread, node limits, hash cleared per
  search. Anything recorded or compared must keep that profile; the plan
  policy must stay a pure function of position, plan, window and budget
  (policy identity, checked by `DecisionTest` and the harness).
- **The analyst never weakens.** Difficulty lives only in `Difficulty`
  (selection budget, plan window, concessions); `AnalystBudgets` checks the
  player at full strength.
- **No claim without evidence.** New claim types need a continuation or a
  rule check in `ClaimVerifier`, and wording in `Renderer` that does not
  exceed the claim's certainty.
- UI text is a string resource; the coach's explanation templates live in
  `Renderer` (English only for now). Colour never carries meaning alone:
  board marks pair colour with a shape.
- The session discards results for stale revisions and cancelled jobs, and
  writes saves in order on one IO thread; keep both properties when adding
  intents.

<!-- shared-rules:start -->

## Working practices

- Follow explicit task instructions over the default workflow below.
- Writing the code is not finishing the task. A task is finished when
  its changes are merged to main through a PR that passed CI and review,
  or when the user explicitly accepts a different end state.
- Start every task on current code. Fetch first, then cut the task
  branch from origin/main — never from a stale local branch or an old
  checkout. To continue existing work, rebase or merge the latest
  origin/main into it before editing. Never overwrite existing work to
  update.
- Resolve ambiguity before making consequential changes. State low-risk
  assumptions; ask when scope, safety, or expected behavior is unclear.
- Keep changes focused. Do not modify unrelated code, formatting, or comments.
- Prefer surgical edits over whole-file rewrites when the result is equivalent.
- Stage only intended files. Inspect the diff before committing.

## Communication

- Be concise, factual, and direct. Preserve necessary context and uncertainty.
- Avoid praise, motivational filler, emojis, and em dashes in new prose.
- Address the reader directly in user-facing copy.
- Report what was verified and what remains unverified. Never imply that an
  unavailable check passed.

## Code design

- Prefer early returns and shallow nesting. Separate logical blocks with
  blank lines.
- Use descriptive constants or enums for meaningful or repeated values.
  Use existing standard definitions for protocol/specification constants.
  Keep obvious, one-off values inline.
- Use enums for behavioral modes that would otherwise require ambiguous
  boolean arguments.
- Default members to private. Widen visibility only for required consumers,
  and review the change as an API design decision.
- Follow the repository's declared dependency boundaries. UI and controllers
  must use application services rather than directly accessing databases,
  subprocesses, sockets, or other low-level mechanisms.
- Encapsulate low-level mechanics behind domain-oriented interfaces.
- Reuse genuinely shared logic. Avoid speculative abstractions and layers
  that only forward calls.
- Prefer pure functions for business rules and immutable data where practical.
  Isolate side effects; document non-obvious state ownership or synchronization.
- Explain non-obvious intent, constraints, and tradeoffs in comments.
  Do not narrate obvious code. Add examples or diagrams when they clarify it.

## Validation and errors

- Validate untrusted input at entry points. Where practical, represent valid
  states in types and enforce persistent invariants in database schemas.
- Represent absence and failure explicitly.
- Use assertions for internal programming invariants, not external-input
  validation or required runtime error handling.
- Prefer explicit, actionable errors over silent failure or undocumented
  fallback. Document intentional recovery behavior.
- Never report a skipped or failed operation as successful.

## Bug fixes

1. Identify the root cause and define an observable success criterion.
2. Add a regression test and observe the relevant failure before fixing it.
3. Implement the fix and observe the test passing.
4. Check surrounding behavior for regressions and architectural consistency.

If an automated regression test is impractical, document the reproduction
and verification procedure. State any inability to reproduce the failure.

## Verification

- Run relevant tests and lint after changes.
- Choose coverage by affected behavior and risk, not patch size.
- Use integration or end-to-end tests for critical workflows and boundaries;
  test isolated business rules at the lowest effective level.
- Run broader suites for cross-cutting or high-risk changes, and the full
  required release checks before releasing.
- Validate the requested command, options, platform, and configuration.
  Unrelated green CI is not proof that the reported problem is fixed.
- Recheck after the final edit. Distinguish local checks from CI results.

## Commit messages

- Use a capitalized, imperative subject without a final period.
- Target 50 characters; never exceed 72.
- Separate the subject and body with one blank line.
- Wrap body text at 72 characters.
- Explain what changed and why. Leave implementation mechanics to the code.

## Implementation and review

Unless explicitly instructed otherwise:

1. Work on a focused branch cut from the latest origin/main and open a PR
   against main before reporting the task as done.
2. Inspect CI results and completed review feedback for the latest commit.
   A successful reviewer job does not mean the review found no problems.
3. Address important findings or explain why they do not apply. Handle minor
   findings according to the stopping rules below.
4. Evaluate each fix in the surrounding project, add regression coverage,
   and rerun affected checks before pushing.
5. Repeat until a stopping criterion is met.
6. Merge without asking again once the stopping criterion is met, required
   checks pass on the latest commit, and no unresolved blockers or required
   human review requests remain.

### Reviewer context limits

The automated PR reviewer does not see the user's original prompt or
conversation. It may suggest changes that go against or beyond what the
user asked for. Do not implement such suggestions. Note each conflict and
report it to the user at the end of the thread.

### Automated review stopping rules

Judge findings by verified impact, not the reviewer's severity label.
Important findings concern correctness, security, data loss, broken builds,
or materially degraded behavior/performance.

Track completed review rounds and consecutive rounds without important
findings. Reruns of the same revision and integration failures do not count.

- No applicable actionable feedback: finish immediately.
- First minor-only round: optionally fix worthwhile, low-risk findings.
  Do not manufacture another push merely to obtain another review.
- Two consecutive rounds without important findings: stop responding to
  automated nitpicks, even if actionable minor suggestions remain.
  Defer worthwhile leftovers rather than continuing the cycle.
- A confirmed important finding resets the minor-only streak. Address it
  and verify the fix before continuing.

After ten completed rounds, enter stabilization:

- Stop optional cleanup, refactoring, and nitpick fixes.
- One completed review without confirmed important findings is sufficient
  to finish, even if minor suggestions remain.
- Continue only for confirmed important defects. If resolving them stalls,
  report the blockers rather than continuing indefinitely.

These limits end optional automated-feedback work. They do not waive
confirmed blockers, unresolved human review requests, or required checks.

### Reviewer integration failures

After two consecutive reviewer-integration failures, stop and report the
review gap. Do not treat failures as approval. An explicit user instruction
may waive review; report that waiver rather than claiming review passed.

## Ending a task

- A task ends with its changes merged to main — not with code written,
  and not with a PR merely opened. An open PR is work in progress:
  monitor CI on the latest commit, address review findings per the
  stopping rules, and merge once the criteria are met.
- Never finish with uncommitted changes or unpushed commits in the
  worktree. Commit, push, and open or update the PR first.
- If a step is impossible (missing push access, CI failure, reviewer
  outage), report the exact blocker instead. Never present unreviewed or
  unmerged work as finished.
- Before finishing, confirm: the requested behavior is implemented
  without unrelated changes; relevant checks pass on the latest code;
  important review findings are addressed or rejected with reasons;
  deferred suggestions, remaining risks, and validation gaps are
  disclosed.
- The final response states where the work stands: branch, PR, CI
  status, review rounds completed, and whether it is merged.

<!-- shared-rules:end -->
