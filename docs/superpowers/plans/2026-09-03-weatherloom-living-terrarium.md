# Weatherloom Living Terrarium Implementation Plan

**Goal:** Expand the existing deterministic Weatherloom puzzle game into a persistent, animated, customizable Living Terrarium without changing the original gameplay model or approved visual identity.

**Spec:** `docs/superpowers/specs/2026-09-03-weatherloom-living-terrarium-design.md`

**Branch policy:** `feature/* -> develop -> main`; no more than **1–2 completed features** may accumulate on `develop` before a green integration checkpoint to `main`.

**Review policy:** every implementation/work-product agent has an independent adversarial reviewer. Builder output cannot advance until the reviewer returns `APPROVED`; Orchestrator convergence is separately challenged by `IntegrationGraphAdversary`.

**Graph policy:** use the installed `/graphRepair` skill/tool wherever exposed. In environments where it is unavailable, run the explicit Graph Integrity Pass defined in the spec and report that substitute accurately.

---

## Program dependency graph

```text
release-readiness baseline
        |
        +--> AI developer harness + adversarial loop
        |
        +--> SaveMigration ---------------------------------------+
        |                                                        |
        +--> Release signing/identity lane                        |
                                                                 v
Terrarium domain contracts --> inventory/layout persistence --> minimal editor
                                                                 |
PuzzleOutcome/RewardService --> XP -------------------------------+
                                                                 |
SimResult --> WeatherEchoSnapshot --> EnvironmentState --> ReactionEngine
                                                                 |
                                             GrowthState <--------+
                                               |
                                             Visitors --> Discoveries --> Almanac
                                               |
                                             Animation presentation
                                               |
                                 Accessibility/instrumentation/performance
                                               |
                                      content scale / seasons / economy later
```

Central save/reward/inventory mutation work is serialized. Independent release, content, isolated UI, animation, documentation, and test work may run concurrently after contracts are frozen.

---

# PHASE 0 — CLOSE THE EXISTING INTEGRATION GAP

## Feature 0A — Integrate release-readiness into `develop`

**Current branch:** `feature/release-readiness`

**Already implemented:**
- retained debug APK + SHA-256 artifact;
- feature-branch CI;
- immutable Action SHA pins;
- integration cadence policy;
- canonical UI style guardrails;
- production-readiness spec/plan.

**Gate:**
1. PR CI green.
2. Independent adversarial diff review confirms no gameplay source changes relative to `develop`, no secret leakage, and no runtime networking.
3. Merge PR to `develop`.
4. Wait for `develop` push CI green.

## Feature 0B — Integrate green `develop` into `main`

Because `main` is already behind the repaired `develop` baseline, create `develop -> main` PR immediately after 0A succeeds.

**Gate:** full CI on PR and `main` after merge.

**Do not begin mutable Terrarium state work from an older branch.**

---

# CHECKPOINT 1 — DEVELOPMENT HARNESS + SAVE SAFETY

These two features can be developed in parallel because the OpenAI harness stays under `tools/ai/` and the save migration changes Android persistence.

## Feature 1 — OpenAI developer harness with mandatory adversarial pairing

**Branch:** `feature/ai-agent-harness`

**Files:**
- Create: `tools/ai/README.md`
- Create: `tools/ai/requirements.txt`
- Create: `tools/ai/schemas.py`
- Create: `tools/ai/roles.py`
- Create: `tools/ai/orchestrator.py`
- Create: `tools/ai/graph_integrity.py`
- Create: `tools/ai/tests/test_orchestration.py`
- Create: `tools/ai/tests/test_review_fixtures.py`
- Modify: `.gitignore` if local trace/cache artifacts need exclusion.

**Architecture:**
- Python OpenAI Agents SDK; development only.
- Manager/code-orchestrated hybrid.
- Structured Pydantic reviewer output.
- Every implementation/work-product worker maps to one adversarial reviewer role.
- Orchestrator convergence is challenged by `IntegrationGraphAdversary`.
- `NEEDS_CHANGES` loops worker -> focused checks -> new reviewer pass.
- Independent pairs may run concurrently via `asyncio.gather` only when their file/state targets are independent.
- Trace metadata identifies workflow/feature/reviewer, with sensitive trace content disabled by default for repo work.
- Model names configured by environment variables, not hard-coded availability assumptions.
- `OPENAI_API_KEY` read only from the developer environment; never committed or forwarded to Android.
- Sandbox-based coding isolation may be enabled when useful, but the basic harness cannot require beta sandbox APIs.

**Initial roles:**
- AgentHarnessWorker / AgentHarnessAdversary
- DomainModelWorker / DomainModelAdversary
- PersistenceWorker / PersistenceAdversary
- RewardWorker / RewardAdversary
- WeatherEchoWorker / WeatherEchoAdversary
- ReactionWorker / ReactionAdversary
- UIWorker / UIStyleAccessibilityAdversary
- AnimationWorker / AnimationPerformanceReducedMotionAdversary
- ContentWorker / ContentSchemaAdversary
- TestWorker / MutationEdgeCaseAdversary
- ReleaseWorker / SupplyChainSecretAdversary
- Orchestrator / IntegrationGraphAdversary

**Reviewer schema:**

```python
class Finding(BaseModel):
    severity: Literal["low", "medium", "high", "critical"]
    invariant: str
    evidence: str
    failure_path: str
    required_fix: str

class ReviewResult(BaseModel):
    verdict: Literal["APPROVED", "NEEDS_CHANGES", "REJECTED"]
    findings: list[Finding]
```

**Acceptance tests:**
- deterministic SDK test: one worker output -> reviewer APPROVED;
- deterministic SDK test: reviewer NEEDS_CHANGES -> second worker turn -> reviewer APPROVED;
- Orchestrator cannot promote work without a reviewer result and IntegrationGraphAdversary gate;
- independent pair scheduling demonstrates concurrency without shared mutable file targets;
- seeded defects are rejected: duplicate reward, missing migration, runtime INTERNET dependency, nondeterministic reaction, style drift, Reduced Motion violation;
- test suite runs with no live model call using Agents SDK testing utilities;
- reviewer does not share the builder's mutable workspace in sandbox mode.

**Live smoke:** once `OPENAI_API_KEY` is configured locally, run one read-only repository review through the harness. No automatic pushes from the harness in the first version.

### Adversarial reviewer for Feature 1
`AgentHarnessAdversary` attempts to prove:
- builder can approve its own work;
- unreviewed patch can advance;
- schemas accept vague/unstructured output;
- secrets can enter traces/prompts;
- parallel tasks can edit the same central file;
- tests accidentally hit the live API;
- sandbox beta behavior is required for basic tests.

---

## Feature 2 — Explicit SaveMigration engine

**Branch:** `feature/save-migration`

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Create: `android/app/src/test/java/com/rork/weatherloom/data/SaveMigrationTest.kt`

**Contract:**

```kotlin
const val CURRENT_SAVE_SCHEMA = 2

object SaveMigration {
    fun decode(raw: String?, json: Json): SaveData
}
```

Also introduce a serialized repository mutation boundary before multi-field rewards are added.

**Acceptance tests:**
- null save -> valid defaults;
- exact schema-1 save -> schema-2 current save with all progress/settings retained;
- current save is idempotent;
- unknown JSON fields ignored;
- corrupt JSON follows documented fallback policy;
- migration never silently discards valid `levels`, collectibles, daily history, tutorial, accessibility, or audio settings;
- serialized mutation boundary cannot interleave two state updates into a lost update.

### Adversarial reviewer for Feature 2
`PersistenceAdversary` attacks missing fields, negative/unknown rating values, duplicate collectible IDs, corrupt JSON, future unknown fields, interrupted writes, lost updates, and accidental reset-to-default paths.

### Checkpoint 1 integration
- targeted tests;
- full CI;
- paired adversarial approvals;
- IntegrationGraphAdversary convergence review;
- `/graphRepair` or explicit Graph Integrity Pass;
- integrate each green feature to `develop`;
- because 2 features are complete, merge `develop -> main` after full green gate.

---

# CHECKPOINT 2 — TERRARIUM DOMAIN + PERSISTENT LAYOUT

Serialize these features because they define central state contracts.

## Feature 3 — Terrarium catalog, inventory, layout, and growth contracts

**Branch:** `feature/terrarium-domain-model`

**Files:**
- Create package: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/`
- Create: `TerrariumItem.kt`
- Create: `TerrariumCatalog.kt`
- Create: `PlayerInventory.kt`
- Create: `TerrariumLayout.kt`
- Create: `GrowthState.kt`
- Create asset: `android/app/src/main/assets/terrarium_items.json`
- Create tests under `android/app/src/test/java/com/rork/weatherloom/core/terrarium/`
- Extend save DTOs only through the migration path from Feature 2.

**MVP contracts:**
- stable item IDs;
- category, visual family, footprint, allowed rotations, reaction tags, growth profile;
- inventory ownership separate from placement;
- layout instance IDs separate from item IDs;
- layout contains normalized position + logical footprint + depth + rotation only;
- `GrowthState` is separate from layout and keyed by stable instance/growth identity.

**Compatibility:** existing nine botanical collectible IDs map into the new catalog without losing unlock history.

### Adversarial reviewer
`DomainModelAdversary` attacks ID instability, runtime-object serialization, catalog/inventory coupling, growth/layout duplication, invalid footprints, duplicate instance IDs, impossible rotations, and circular package dependencies.

---

## Feature 4 — Persistent layout operations and migration bootstrap

**Branch:** `feature/terrarium-layout-save`

**Files:**
- Modify save data/repository through explicit migration.
- Create: `TerrariumRepository.kt` or a focused domain service if separation from `GameRepository` reduces coupling.
- Create layout operation tests.
- Modify Terrarium rendering adapter to read a layout snapshot but keep existing visuals.

**Operations:**
- grant/own;
- place;
- move;
- rotate;
- store/remove;
- validate collision/bounds;
- persist/reload.

**Bootstrap rule:** a schema-1 player with existing collectibles receives a deterministic initial layout matching the current fixed-slot presentation as closely as possible.

**Required end-to-end proof:** own one existing Rainbell -> place -> restart/reload -> same placement and same independent growth state.

### Adversarial reviewer
`PersistenceAdversary` attacks duplicate grants, invalid/missing item references, conflicting footprints, placement outside allowed area, deleted content IDs, replaying the same mutation, growth reset on move, and serialization/reload drift.

### Checkpoint 2 integration
Full tests -> paired reviews -> IntegrationGraphAdversary -> Graph Integrity -> feature PRs -> `develop`; then `develop -> main` after these two green features.

---

# CHECKPOINT 3 — EDIT UX + REWARD AUTHORITY

These can be implemented partly in parallel after domain/layout contracts freeze: editor UI owns no state authority; RewardService touches puzzle completion/repository.

## Feature 5 — Minimal Weatherloom-style Terrarium edit mode

**Branch:** `feature/terrarium-editor`

**Files likely:**
- `ui/screens/TerrariumScreen.kt`
- `ui/board/TerrariumScene.kt`
- new `ui/terrarium/*` editor components/viewmodel
- accessibility semantics tests where practical.

**Scope:** one complete accessible editor path before content expansion:
- enter Arrange mode from the existing home hierarchy;
- choose one owned object;
- drag/snap placement;
- non-drag accessible move alternative;
- move/rotate/store;
- undo/redo;
- commit automatically to layout service.

**Visual invariant:** cloche, cream surfaces, coral actions, rounded tactile controls, and existing bottom navigation remain stylistically unchanged.

### Adversarial reviewer
`UIStyleAccessibilityAdversary` compares against style guardrails and attacks generic Material restyling, undersized targets, missing content descriptions, inaccessible drag-only actions, layout clipping, and state accidentally stored in Composables.

---

## Feature 6 — PuzzleOutcome + RewardService extraction

**Branch:** `feature/reward-service`

**Files likely:**
- Create `core/reward/PuzzleOutcome.kt`
- Create `core/reward/RewardService.kt`
- Modify `PuzzleViewModel.kt`
- Modify persistence interfaces minimally.
- Add regression tests proving all existing level rating/collectible behavior is unchanged.

**Goal:** extract transaction authority from `PuzzleViewModel.finish()` before XP, Weather Echoes, inventory, or discovery are added.

**Required proof:** every existing solve path still produces the same Seedling/Bloom/Flourish and collectible result as before extraction; one outcome is applied atomically/idempotently.

### Adversarial reviewer
`RewardAdversary` attacks double completion, repeated `finish()`, interrupted claim, Daily vs authored levels, replaying result screen, same reward granted twice, partial transaction writes, and rating downgrade.

### Checkpoint 3
Full green gate + paired reviews + graph integrity + merge after 2 features.

---

# CHECKPOINT 4 — PROGRESSION + WEATHER ECHO SNAPSHOT

These can be developed in parallel once RewardService contract is stable.

## Feature 7 — Player XP and milestone model

- add one player XP track;
- deterministic reward table;
- migration-safe defaults;
- no second mastery rating;
- existing Flourish drives mastery progress.

**Tests:** threshold boundaries, duplicate completion policy, replay reward policy, migration defaults.

**Adversary:** `RewardAdversary` attacks XP duplication/exploit paths and normal progression blocked by Flourish.

## Feature 8 — WeatherEchoSnapshot derivation from SimResult

**Files:** new pure `WeatherEchoSnapshot.kt` / `WeatherEchoDeriver.kt` and tests.

MVP supports simultaneous non-Clear kinds: Rain, Snow, Wind. `Clear` is derived only when the explicit clear-condition contract is met. Persist deterministic intensities and derive a primary display kind separately.

**Tests:** representative handcrafted `SimResult`/frames, rain+wind mixed cases, deterministic repeated derivation, stable ordering/ID, no wall-clock/random dependency.

**Adversary:** `WeatherEchoAdversary` attacks ambiguous mixed weather, empty/no-event runs, Clear conflicting with precipitation, multiple simultaneous conditions, and iteration-order drift.

### Checkpoint 4
Full gate -> paired reviews -> IntegrationGraphAdversary -> Graph Integrity -> develop -> main.

---

# CHECKPOINT 5 — REACTION ENGINE + FIRST CAUSAL VERTICAL SLICE

Serialize shared reaction contracts first, then UI/presentation may parallelize.

## Feature 9 — EnvironmentState + data-driven ReactionEngine

**Files:**
- new `core/terrarium/reaction/*`
- `terrarium_reactions.json`
- deterministic evaluator tests.

First reaction: Rain + Rainbell -> Rainbell bloom/wet ephemeral reaction state plus stable discovery candidate/event ID.

`ReactionResult` must separate recomputable visual state from idempotent durable event IDs.

**Adversary:** `ReactionAdversary` attacks nondeterminism, rule-order dependence, missing items, contradictory outputs, repeated evaluation granting duplicate durable events, and content-schema drift.

## Feature 10 — Puzzle -> Rain Echo -> Rainbell reaction vertical proof

Connect RewardService `WeatherEchoSnapshot` output to persistent Terrarium environment and evaluate on Terrarium open/meaningful state change.

**Required proof:** solve representative puzzle -> receive Rain kind -> restart app if desired -> Terrarium Rainbell reacts consistently -> durable discovery event can apply exactly once.

**Adversary:** paired integration adversary tries to cause stale echo, double application, missing save, wrong puzzle outcome, and restart divergence.

### Checkpoint 5
Full gate -> graph integrity -> main.

---

# CHECKPOINT 6 — GROWTH + DISCOVERY/VISITOR

## Feature 11 — Growth Pulse/state system

Stages: Seed -> Sprout -> Young -> Mature -> Bloom (content may omit stages when not applicable).

Growth comes primarily from play/weather events, not mandatory elapsed-time care. Moving an item must not reset biological state.

**Adversary:** attacks clock manipulation, regressions, repeated pulses, max-stage overflow, species without growth profiles, and move/store/re-place state loss.

## Feature 12 — First visitor + discovery registry

First complete combination: Rain + pond/reeds (or another authored MVP combination) -> frog visitor -> `Pond Chorus` discovery.

Persist discovery exactly once while allowing visitor presentation to recur according to deterministic conditions.

**Adversary:** attacks duplicate discoveries, visitor without prerequisites, disappearing Almanac state, invalid content IDs, and animation granting logical rewards.

### Checkpoint 6
Green -> Graph Integrity -> main.

---

# CHECKPOINT 7 — ALMANAC + LIVING ANIMATION

Can run in parallel after discovery/visual-state contracts freeze.

## Feature 13 — Almanac Species / Weather / Discoveries expansion

Extend existing Almanac without changing its visual language.

**Adversary:** attacks lost existing species/rules, inaccessible sections, opaque completion anxiety, and hard-coded runtime enums leaking into localized copy.

## Feature 14 — Terrarium state-driven animation layer

Preserve existing puzzle animation; add Terrarium weather/vegetation/water/mechanism/visitor animation driven only by logical/visual snapshots.

MVP:
- Rain animation in cloche;
- cloud drift where weather state requires it;
- Rainbell/vegetation response;
- pond/water ripple;
- wind response and windmill animation where matching placed content exists;
- first animal behavior loop;
- Reduced Motion alternatives.

**Adversary:** `AnimationPerformanceReducedMotionAdversary` attacks frame-driven game-state mutation, excessive recomposition/allocation, Reduced Motion violations, animations that contradict logical state, and offscreen work.

### Checkpoint 7
Green -> performance smoke -> Graph Integrity -> main.

---

# CHECKPOINT 8 — CONTENT SCALE + ACCESSIBILITY HARDENING

## Feature 15 — Expand to MVP content/reaction set

Only after the single vertical chain is proven. Add data, not bespoke code, toward:
- ~24 placeables;
- MVP echo kinds Rain/Snow/Wind/Clear with mixed non-Clear snapshots;
- ~10 reactions;
- 4 visitor families.

Parallelize content authoring and fixtures because shared schemas are frozen.

Every ContentWorker is paired with ContentSchemaAdversary.

## Feature 16 — Terrarium accessibility/adaptive-layout hardening

- 48dp effective targets;
- TalkBack semantics;
- non-drag editing controls;
- compact/expanded layout checks;
- Reduced Motion coverage;
- preserve approved style.

### Checkpoint 8
Green device/instrumented checks where available -> Graph Integrity -> main.

---

# PRODUCTION-READINESS LANE — RUN IN PARALLEL WHERE SAFE

The following work remains required before public Android v1 and can run beside Terrarium work when it does not touch the same central files:

1. production release workflow skeleton and secret-backed signing;
2. release identity/version gate;
3. checksum/provenance/attestation;
4. explicit backup policy;
5. localization/pseudolocale;
6. instrumentation/accessibility tests;
7. Macrobenchmark/Baseline Profile;
8. branch/dependency governance;
9. final release checklist.

A `ReleaseWorker` always has `SupplyChainSecretAdversary`. Signing credentials are never exposed to builder/reviewer prompts or traces.

If release work touches `build.gradle.kts`, CI workflows, navigation, or shared save contracts at the same time as another branch, serialize those edits or rebase before convergence.

---

# POST-MVP CHECKPOINTS

After the causal MVP is proven:

- Mist/Warm/Cold Weather Echo fields/tags;
- additional visitors and rare phenomena;
- paths, streams, bridges, structures;
- Masterwork/Heirloom sets;
- data-driven seasons + archive return path;
- multiple saved layouts;
- screenshot sharing;
- one soft currency/catalog if engagement supports it;
- optional direct cosmetic entitlements with purchase restoration;
- never make backend/store required for core play.

Each remains subject to the 1–2-feature main-integration rule.

---

# REQUIRED TEST MATRIX

For every relevant checkpoint, test:

- fresh install/default save;
- schema-1 existing save;
- migration idempotence;
- app restart;
- background/foreground where relevant;
- offline mode/no network permission;
- duplicate reward calls;
- interrupted/replayed reward claim;
- inventory consistency;
- invalid/deprecated item references;
- multiple placements/collision;
- undo/redo;
- growth bounds and move-without-reset;
- discovery preconditions/idempotence;
- mixed Weather Echo cases;
- Reduced Motion;
- small portrait layout;
- expanded layout when affected;
- deterministic core regression;
- content validator;
- lint/build;
- downloadable debug artifact.

Critical state systems get automated tests before UI polish.

---

# ADVERSARIAL REVIEW + GRAPH REPAIR LOOP

For every feature:

```text
builder
 -> focused tests
 -> paired adversarial reviewer
    -> APPROVED: continue
    -> NEEDS_CHANGES: repair -> focused tests -> new independent review pass
    -> REJECTED: stop and escalate design blocker
 -> /graphRepair or explicit Graph Integrity Pass
 -> integration tests
 -> full applicable CI
 -> commit/push
 -> feature PR
```

Before branch convergence or `develop -> main`:

```text
full CI
-> save/migration tests where affected
-> deterministic validator
-> IntegrationGraphAdversary
-> /graphRepair or explicit Graph Integrity Pass
-> merge
-> run destination-branch CI again
```

Do not claim `/graphRepair` ran unless the literal skill/tool was available; otherwise report `Graph Integrity Pass` explicitly.

---

# OPENAI API DEVELOPMENT REFERENCES

Use current official OpenAI guidance when implementing the harness:

- Agents SDK: https://openai.github.io/openai-agents-python/
- Agent orchestration: https://openai.github.io/openai-agents-python/multi_agent/
- Testing: https://openai.github.io/openai-agents-python/testing/
- Tracing: https://openai.github.io/openai-agents-python/tracing/
- Sandbox agents: https://openai.github.io/openai-agents-python/sandbox/guide/
- OpenAI API models: https://developers.openai.com/api/docs/models
- Responses API: https://developers.openai.com/api/reference/resources/responses/methods/create

The live harness requires `OPENAI_API_KEY` in the developer environment only. The Android application must not read or contain that variable.

---

# REPORTING AFTER EACH CHECKPOINT

Report exactly:

**COMPLETED** — actual features finished.

**TESTS** — exact tests/CI and pass/fail.

**ADVERSARIAL REVIEW** — reviewer verdict and repaired findings.

**GRAPH** — literal `/graphRepair` result if available; otherwise explicit Graph Integrity Pass result.

**PERFORMANCE** — relevant measurements/observations.

**FILES / SYSTEMS CHANGED** — major areas.

**COMMITS** — exact hashes/messages.

**REMOTE** — push/PR status.

**MERGE STATUS** — feature/develop/main state.

**KNOWN ISSUES** — unresolved items.

**NEXT CHECKPOINT** — exact next one or two features.
