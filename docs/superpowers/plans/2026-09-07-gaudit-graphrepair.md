# Weatherloom Gaudit Graph Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair every confirmed finding from the authoritative current-state `/Gaudit` without changing Weatherloom's deterministic offline gameplay architecture or weakening any existing verification gate.

**Architecture:** Use one isolated repair branch/PR per independent graph node or tightly coupled finding cluster. Every behavior or policy change follows RED -> observed failure -> minimal GREEN -> targeted tests -> full CI -> adversarial graph-integrity review -> PR CI -> merge to `develop`; release/governance work happens only after required check names stabilize.

**Tech Stack:** Kotlin/Android/Compose, Gradle, Kotlin Serialization, GitHub Actions, Python tooling, existing Weatherloom deterministic source-contract tests.

**Spec:** Authoritative `/Gaudit` completed against `main@1451ba562aa312e9314b3cfe05048167d5561822`, plus `docs/superpowers/plans/2026-09-03-weatherloom-production-readiness.md`.

## Global Constraints

- Start from the newest verified `develop`; never reset newer legitimate work.
- Preserve the deterministic simulation, reward authority, Terrarium discovery authority, and offline-first Android runtime.
- Do not add `android.permission.INTERNET`, accounts, backend services, analytics, commerce, or runtime OpenAI dependencies.
- Never claim a test, build, commit, PR, merge, workflow, artifact, checksum, or review that was not directly observed.
- Never continue across a red verification gate.
- Production code/config changes require a failing regression/policy test first unless the change is repository-only administration that cannot be test-driven in code.
- Keep fixes minimal; no opportunistic redesign/refactor.

---

### Task 1: SEC-01 Kotlin Build-Tool Security Floor

**Files:**
- Create: `android/app/src/test/java/com/rork/weatherloom/build/BuildDependencyPolicyTest.kt`
- Modify: `android/gradle/libs.versions.toml`

**Interfaces:**
- Consumes: version-catalog `kotlin` value.
- Produces: an executable policy requiring Kotlin Gradle Plugin >= 2.4.20.

- [ ] Write `BuildDependencyPolicyTest` using the established repository-file lookup helper pattern. Parse `kotlin = "x.y.z"` and assert semantic version >= `2.4.20`.
- [ ] Run `./gradlew testDebugUnitTest --tests com.rork.weatherloom.build.BuildDependencyPolicyTest --stacktrace`; observe failure on Kotlin `2.3.10`.
- [ ] Change only `kotlin = "2.4.20"` initially.
- [ ] Re-run the targeted test, then `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace` and the full GitHub CI.
- [ ] If build compatibility fails, investigate the exact dependency/plugin boundary before changing any second version.

---

### Task 2: PERF-01 Terminal Growth State Stability

**Files:**
- Modify/Test: `android/app/src/test/java/com/rork/weatherloom/core/terrarium/GrowthPulseAdversarialTest.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/GrowthPulseService.kt`

**Interfaces:**
- Consumes: `GrowthState`, `GrowthProfile`, relevant `WeatherEchoSnapshot`.
- Produces: terminal Bloom growth is a true no-op for all future relevant Echoes.

- [ ] Add a regression test that applies many unique relevant Echo IDs to an already-terminal plant and asserts the complete `GrowthState` remains unchanged.
- [ ] Observe the test fail because `growthPulsesApplied`/`appliedEchoIds` currently grow.
- [ ] Add the minimal terminal-stage short circuit after validating existing state/profile identity.
- [ ] Re-run targeted Growth Pulse tests and full CI.

---

### Task 3: DATA-02 Future-Schema Non-Destructive Handling

**Files:**
- Modify/Test: `android/app/src/test/java/com/rork/weatherloom/data/SaveMigrationTest.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Modify: persistence boundary only as required by the chosen contract.

**Interfaces:**
- Consumes: raw future-schema JSON.
- Produces: a contract that cannot silently erase fields unknown to the current binary.

- [ ] Add a round-trip regression test with `schema=99`, a nested unknown field, and a mutation to a known field.
- [ ] Observe current code erase the unknown field when re-serialized.
- [ ] Implement the smallest safe policy: future-schema saves are non-writable by the older binary unless raw unknown JSON can be preserved exactly.
- [ ] Add tests proving known data remains readable and unknown data cannot be destructively rewritten.
- [ ] Run save/migration tests and full CI.

---

### Task 4: DATA-03 Corrupt-Save Recovery Contract

**Files:**
- Modify/Test: `android/app/src/test/java/com/rork/weatherloom/data/SaveMigrationTest.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Modify/create repository recovery state only if required by tests.

**Interfaces:**
- Consumes: malformed stored JSON.
- Produces: a recoverable/quarantined corrupt-save outcome rather than irreversible silent replacement.

- [ ] Add a regression proving corrupt raw data remains recoverable until an explicit reset/recovery decision.
- [ ] Observe current fallback-to-`SaveData()` behavior fail that contract.
- [ ] Implement minimal quarantine/recovery metadata without exposing file paths or stack traces to production UI.
- [ ] Verify restart and migration tests.

---

### Task 5: DATA-01 Durable Persistence Boundary

**Files:**
- Modify/Test: persistence tests under `android/app/src/test/java/com/rork/weatherloom/data/`
- Modify: `GameRepository.kt`
- Create a focused persistence abstraction if required.
- Add DataStore only if evidence shows it is the narrowest robust solution.

**Interfaces:**
- Consumes: serialized `SaveData` mutations.
- Produces: observable durable commit/failure semantics; UI state must not claim persisted success before the durable boundary accepts it.

- [ ] Add an injectable persistence test double that fails a write and assert in-memory authoritative state does not advance as if persistence succeeded.
- [ ] Observe current `persist()` contract cannot represent failure.
- [ ] Refactor only the persistence boundary needed to make successful mutation and durable commit ordering explicit.
- [ ] Add restart/concurrency tests and run full CI.

---

### Task 6: ACC-02/ACC-03 Accessibility Foundation

**Files:**
- Modify/Test: theme/source-contract tests.
- Modify: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `MainActivity.kt`, `AppNavigation.kt`, `AlmanacScreen.kt`, animation call sites.

**Interfaces:**
- Consumes: `SaveData.highContrast`, `SaveData.reducedMotion`.
- Produces: compliant small-text contrast and global reduced-motion behavior while preserving Weatherloom visual identity.

- [ ] Add deterministic contrast assertions for representative semantic foreground/background pairs and a source contract requiring `highContrast` to reach `AppTheme`.
- [ ] Observe failures against the current palette/unwired high-contrast flag.
- [ ] Add a high-contrast semantic palette and wire the persisted setting.
- [ ] Add source/behavior tests proving decorative entrance/infinite animation is static when Reduced Motion is enabled.
- [ ] Make only the animation changes needed by those tests and run full CI.

---

### Task 7: ACC-01 Accessible Puzzle Board

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/board/BoardAccessibility.kt`
- Modify: `DioramaBoard.kt`, `PuzzleScreen.kt`
- Add JVM semantics-model tests first; add `androidTest` Compose semantics tests when the test runner is introduced.

**Interfaces:**
- Consumes: board cells, armed `ThreadType`, current path, level max-cell budget.
- Produces: semantic board state and non-drag actions that can construct the same `DrawnStroke` consumed by existing gameplay.

- [ ] Define and test a pure accessibility path builder that selects cells and produces the same normalized grid-cell path contract as pointer drawing.
- [ ] Observe RED before wiring UI.
- [ ] Add semantic labels/actions without removing pointer/stylus gestures.
- [ ] Add Compose instrumentation proving a representative thread can be created without pointer dragging.
- [ ] Run accessibility/instrumentation and full CI.

---

### Task 8: CI-02 Dependency Governance

**Files:**
- Create: `.github/dependabot.yml`
- Add dependency-review workflow if repository support allows.
- Lock/hash Python harness dependencies or add a generated lock artifact with deterministic verification.

- [ ] Add a policy test/source check for the required governance files before creating them.
- [ ] Observe RED.
- [ ] Add Dependabot coverage for Gradle, GitHub Actions, and Python/pip where supported.
- [ ] Add dependency review with least-privilege permissions.
- [ ] Verify CI and review exact check names for later branch protection.

---

### Task 9: CI-01 Repository Ruleset Enforcement

**Files:** repository settings/rulesets; no production source change.

**Interfaces:**
- Consumes: stable CI check names from completed earlier waves.
- Produces: enforced PR + CI gates on `main` and `develop`, force-push/deletion protection.

- [ ] Read current rulesets and branch state immediately before mutation.
- [ ] Create rules only after required check names are stable and known.
- [ ] Verify repository APIs report the rules active.
- [ ] Do not claim a blocked-push proof unless an actual safe rejection can be observed.

---

### Task 10: REL-01 Signed Android Release Pipeline

**Files:**
- Create: `.github/workflows/android-release.yml`
- Create: `docs/release/ANDROID_SIGNING.md`, `docs/release/RELEASE_IDENTITY.md`, `docs/release/RELEASE_CHECKLIST.md`
- Create: `tools/check_release_identity.py` + tests.
- Modify: `android/app/build.gradle.kts`.

**Interfaces:**
- Consumes: release tag/version metadata and GitHub signing secrets when configured.
- Produces: signed `app-release.aab`, signer verification, SHA-256, release metadata, and attestation.

- [ ] TDD the release identity/tag/version parser first.
- [ ] Add environment-only signing configuration that cannot silently fall back to debug signing.
- [ ] Add manual/tag release workflow with immutable action SHAs, minimum permissions, temp-keystore cleanup, `bundleRelease`, signer verification, checksum, and attestation.
- [ ] Add R8/resource shrinking only after release-like tests prove compatibility.
- [ ] Verify a non-secret dry-run path; never fabricate signing secrets or claim a signed release until an actual signed workflow is observed.

## Convergence Gate

After all nodes are merged to `develop`:

- [ ] Run a brand-new full `develop` CI.
- [ ] Run an adversarial graph-integrity/source-contract review across the complete delta.
- [ ] Checkpoint `develop -> main` only when every required gate is green.
- [ ] Run a brand-new full `main` CI and independently verify the resulting artifact checksum.
