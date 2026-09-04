# Weatherloom Living Terrarium — Adversarial Graph Review

## Scope
Architecture/spec review for `docs/superpowers/specs/2026-09-03-weatherloom-living-terrarium-design.md` and its implementation plan.

## Tool disclosure
A literal installed `/graphRepair` command is **not exposed in this ChatGPT tool surface**. This review is therefore the explicit **Graph Integrity Pass** required by the project protocol. It must not be reported as a literal `/graphRepair` execution.

If `/graphRepair` is available in a later execution environment, run it at the same gates in addition to tests.

## Adversarial method
Attempt to disprove the architecture by looking for:

- circular domain dependencies;
- duplicated state authority;
- nondeterministic behavior entering the causal model;
- non-idempotent reward/reaction writes;
- save migration loss;
- UI becoming state authority;
- OpenAI/runtime networking entering Android;
- animation changing logical state;
- store/economy becoming required for core play;
- unsafe parallel-edit boundaries;
- ambiguous agent/reviewer approval authority;
- branch policy that could promote unverified work.

## Findings and repairs

### G1 — Growth state duplicated inside layout
**Severity:** high

Initial draft placed `growthStage` on `TerrariumLayout` instances while also defining a separate `GrowthState` domain. Moving an item could therefore accidentally couple biological progression to placement operations.

**Repair:** `TerrariumLayout` now owns placement only. `GrowthState` is a separate durable domain keyed by stable instance/growth identity.

**Status:** repaired.

### G2 — Single Weather Echo classification loses causal information
**Severity:** high

Initial draft treated Rain/Snow/Wind/Clear as a single classification. A puzzle can be rainy and windy at the same time, and future reactions may need both conditions.

**Repair:** replaced with `WeatherEchoSnapshot`, supporting an ordered set of simultaneous non-Clear phenomena plus deterministic intensities and a derived primary kind for concise UI.

**Status:** repaired.

### G3 — Reward changes were described as transaction-like but not atomic enough
**Severity:** critical

If level completion, XP, inventory, and echo updates were persisted through separate writes, interruption/replay could grant half a reward or duplicate one portion.

**Repair:** `RewardService` now requires one serialized persistence mutation boundary. Durable events use stable IDs/idempotent application.

**Status:** repaired in architecture; implementation must prove it with tests.

### G4 — Reaction evaluation mixed ephemeral visuals with durable progression
**Severity:** high

Re-evaluating a Terrarium after every open/layout change could repeatedly grant the same discovery/visitor/growth event if the engine returned one undifferentiated result.

**Repair:** `ReactionResult` explicitly separates recomputable ephemeral visual state from stable-ID durable events applied idempotently exactly once.

**Status:** repaired.

### G5 — “Every agent has a reviewer” can recurse indefinitely
**Severity:** medium

Literal recursive reviewer pairing would never terminate.

**Repair:** every implementation/work-product agent has an independent gate reviewer. Reviewer effectiveness is tested with seeded defects/evals. The Orchestrator is separately challenged by `IntegrationGraphAdversary` before convergence/promotion.

**Status:** repaired.

### G6 — Sandbox coding isolation is useful but currently a beta SDK surface
**Severity:** medium

Hard-requiring Sandbox Agents would couple the development harness to a beta interface.

**Repair:** basic worker/reviewer orchestration and deterministic tests must work without sandbox-specific APIs. Sandbox isolation is preferred/feature-gated for coding tasks; reviewers receive fresh/read-only snapshots or diffs rather than a builder's mutable workspace.

**Status:** repaired.

### G7 — OpenAI API could accidentally violate Weatherloom’s offline-first runtime
**Severity:** critical

The requested OpenAI developer harness could tempt adding an Android dependency, `INTERNET`, API key, or runtime call.

**Repair:** all OpenAI code is constrained to `tools/ai/`; Android remains offline-first, does not receive `OPENAI_API_KEY`, and must not regain the `INTERNET` permission for this tooling.

**Status:** architectural invariant; CI/adversarial fixture must enforce.

### G8 — Animation could become hidden simulation authority
**Severity:** high

Per-frame visitor/plant/water animation must not mutate rewards, discoveries, weather, growth, or puzzle state.

**Repair:** animation consumes immutable logical/visual snapshots. Reaction evaluation happens only on meaningful state events; presentation cannot grant durable state.

**Status:** repaired in architecture; performance/Reduced Motion adversary must verify implementation.

### G9 — Parallelism could corrupt central state contracts
**Severity:** high

Save schema, inventory authority, RewardService, navigation, and branch merges are high-conflict shared nodes.

**Repair:** those nodes are explicitly serialized. Parallelism is allowed only after contracts freeze for content, isolated UI, animation, tests, docs, independent release work, and reviewer pairs.

**Status:** repaired.

## Dependency-direction verdict

Approved direction:

```text
Simulation -> PuzzleOutcome -> RewardService -> durable player state
                                  |
                                  v
                         WeatherEchoSnapshot
                                  |
                                  v
Layout + Growth + Environment -> ReactionEngine -> visual state / durable event IDs
                                                  |
                                                  v
                                              Presentation
```

Forbidden back-edges:

- Terrarium -> `SimulationEngine` mutation;
- UI -> authoritative inventory/reward state;
- animation -> logical state mutation;
- store -> required Terrarium core;
- OpenAI tools -> Android runtime dependency.

## Current verdict

**APPROVED FOR WRITTEN-SPEC REVIEW / NOT YET APPROVED FOR MUTABLE TERRARIUM FEATURE CODE.**

The architecture has no known unresolved graph blocker after the repairs above. The next required gate is review/approval of the written spec/plan, followed by the implementation sequence beginning with the developer harness and SaveMigration work.
