# Weatherloom Living Terrarium Design

## Status
Approved product direction. This document consolidates the existing Weatherloom repository, the approved visual references, the causal Terrarium product vision, the engineering execution protocol, the animation requirement, and the OpenAI-assisted development workflow into one architecture.

No part of this design replaces the existing deterministic puzzle game. The puzzle engine remains the authoritative core.

## Product thesis

**Weatherloom is a deterministic causal weather-puzzle game whose solutions gradually build a living miniature world unique to each player.**

The player learns how warm air, cold air, moisture, wind, elevation, terrain, plants, structures, and water interact by solving handcrafted weather puzzles. The weather the player actually creates produces lasting echoes in a persistent Terrarium. Puzzle achievements unlock plants, terrain, structures, and prestige objects. Players arrange those objects. Weather changes plants and water; those conditions create habitats; habitats attract visitors; combinations create discoveries; discoveries fill the Almanac and suggest new experiments.

The Terrarium is therefore not a separate decorating minigame. It is the persistent expression of the same causal rules the player learns in puzzles.

## Non-negotiable invariants

1. **Preserve the deterministic puzzle engine.** The same level plus the same weather threads must continue to produce the same simulation result.
2. **Preserve the approved Weatherloom visual identity.** `docs/superpowers/UI_STYLE_GUARDRAILS.md` is authoritative.
3. **Remain offline-first.** Core play, Terrarium, progression, discoveries, shipped seasons, and saves must not require a backend or OpenAI API connection.
4. **Animation presents state; it does not invent state.** Rain, clouds, vegetation, water, mechanisms, and creatures may animate richly, but logical state comes from the puzzle/Terrarium domain model.
5. **Reduced Motion is a first-class contract.** Informational state changes remain understandable while ambient motion is reduced or removed.
6. **UI is never authoritative game-state storage.** Domain services own state; Compose renders immutable snapshots and sends intents.
7. **Save evolution is migration-driven.** No destructive schema changes, no runtime-object serialization, and no silent reset on unknown content IDs.
8. **No pay-to-progress architecture.** Store/IAP, if added later, remains outside basic simulation, inventory, layout, and progression authority.
9. **One or two completed features maximum before `develop -> main`.** Red CI never moves forward.
10. **Every implementation/work-product agent has an independent adversarial reviewer.** The builder is never the final approver of its own work. Reviewer agents are gate roles, not an infinitely recursive chain of reviewers.
11. **Integration authority is independently challenged.** The Orchestrator's convergence/merge recommendation is reviewed by an Integration/Graph Adversary before promotion.

## Existing repository capabilities to reuse

The current app already provides:

- Kotlin/Jetpack Compose Android application targeting API 36.
- 28 authored levels across 9 chapters.
- Four weather thread types: Warm Front, Cold Front, Wind Band, Moisture Ribbon.
- Deterministic fixed-step `SimulationEngine` with replayable `SimState` frames and causal events.
- Terrain/features covering meadows, crops, villages, reservoirs, rivers, lakes, wetlands, forests, mountains, stone, roads, soil, flowers, windmills, and houses.
- Objectives covering reservoirs, flowers, crop freezing, snow, village fog, windmills, flooding, and wetland water.
- Seedling/Bloom/Flourish ratings.
- Offline Daily Forecasts.
- `GameRepository` persistence using JSON in SharedPreferences with StateFlow.
- Nine botanical collectible IDs already earned from level rewards.
- Existing Terrarium cloche renderer and nine fixed planting slots.
- Almanac species collection and world-rule reference.
- Existing rain, snow, fog, cloud, wind, water, reed, flower, and windmill animation in puzzle presentation.
- Reduced Motion, music, and sound preferences.
- Headless Python level validation, JVM determinism tests, lint, debug APK CI, checksum, and retained Actions artifacts on the production-readiness branch.

The expansion must evolve these systems instead of duplicating them.

## Architectural overview

```text
                            existing / protected
                      +-----------------------------+
                      |     PuzzleSimulation        |
                      | Level + Threads -> SimResult|
                      +--------------+--------------+
                                     |
                                     v
                           +-------------------+
                           | PuzzleOutcome     |
                           +---------+---------+
                                     |
                                     v
                           +-------------------+
                           | RewardService     |
                           +---+-----------+---+
                               |           |
                              XP    WeatherEchoSnapshot
                               |           |
                     +---------v--+   +----v---------------+
                     |Progression |   | EnvironmentState   |
                     +------------+   +----+---------------+
                                           |
                   +-----------------------+----------------------+
                   |                                              |
                   v                                              v
          +------------------+                           +------------------+
          | TerrariumLayout  |-------------------------->| ReactionEngine   |
          +--------+---------+                           +----+--------+----+
                   ^                                          |        |
                   |                                          |        |
          +--------+---------+                                v        v
          | PlayerInventory  |                         GrowthState   Visitors
          +--------+---------+                                |        |
                   ^                                          +---+----+
                   |                                              |
          +--------+------------+                                 v
          | TerrariumCatalog    |                            Discoveries
          +---------------------+                                 |
                                                                  v
                                                               Almanac
```

Persistence owns serializable player state beneath mutable domain services. Presentation reads immutable snapshots and emits intents.

## Domain boundaries

### TerrariumContentCatalog
Defines what can exist, not what the player owns.

Suggested static fields:

```text
id
nameKey
category
rarityClass
footprint
allowedRotations
visualFamily
growthProfileId?
reactionTags
biomeTags
unlockMetadata
assetRefs
```

Content files carry their own schema/version. Stable IDs become permanent contracts once shipped.

### PlayerInventory
Owns what the player can place.

```text
itemId
variantId
quantityMode: finite | unlimitedAfterUnlock
quantity
unlockSource
unlockedAt
```

All grants pass through one mutation authority. Duplicate grants must be idempotent or explicitly converted according to a defined rule.

### TerrariumLayout
Owns **placement only**. Biological progression does not live here.

```text
instanceId
itemId
variantId
xNormalized
yNormalized
rotation
logicalFootprint
depthLayer
```

The initial implementation uses a 2.5D snap-assisted placement surface. Logical footprints prevent overlap while artwork may visually overflow footprints to preserve the handcrafted look.

### GrowthState
Owns persistent biological progression separately from placement, keyed by a stable placed instance or another explicitly documented growth identity.

```text
instanceId
growthStage
growthPulsesApplied
lastRelevantEchoId?
```

Plants use authored stages such as Seed -> Sprout -> Young -> Mature -> Bloom. Primary progression is **Growth Pulses** earned from play and relevant weather events, not mandatory real-time watering. Time away never kills or damages content. A nonpunitive catch-up pulse may be added later but cannot become a required timer loop.

Separating growth from layout prevents moving an item from accidentally resetting or duplicating biological state.

### PlayerProgression
One primary XP track. Existing Seedling/Bloom/Flourish remains the performance system; do not create a second perfect-rating concept.

### RewardService
The single authority that translates a completed puzzle outcome into durable progression changes.

It receives a frozen `PuzzleOutcome` and executes **one serialized save mutation** covering:

- level record update;
- XP grant;
- direct collectible/item grant;
- mastery progress where applicable;
- Weather Echo snapshot derivation/recording;
- daily completion where applicable;
- reward presentation payload.

`PuzzleViewModel` may call this service but must not independently mutate inventory/XP/discovery state.

The persistence layer must expose one transaction-like `mutate/update` boundary so a crash or replay cannot leave half of a reward applied.

### WeatherEchoSnapshot
A compact deterministic causal summary derived from the actual puzzle simulation, never random loot.

A single enum is too lossy because a puzzle may be both rainy and windy. The durable MVP shape therefore supports simultaneous phenomena:

```text
id
kinds: ordered set of Rain | Snow | Wind | Clear
rainIntensity: 0..N
snowIntensity: 0..N
windIntensity: 0..N
primaryKind: derived for concise UI only
```

`Clear` is mutually exclusive with precipitation/fog-heavy conditions according to an explicit derivation rule. Later schema-compatible fields/tags can add Mist, Warm, Cold, Rainbow, Thunderstorm, Sunshower, Heavy Snow, or Moonlit Mist.

The derivation function must be deterministic and unit-tested. The same frozen `SimResult` produces the same ordered snapshot and ID. It must not depend on wall-clock time, hash iteration order, or frame rate.

### EnvironmentState
The Terrarium's current environmental input. It contains the active `WeatherEchoSnapshot` plus stable season/environment modifiers. It is not a continuously simulated copy of `SimState`.

### ReactionEngine
A pure, deterministic evaluator:

```text
TerrariumLayout
+ GrowthState
+ EnvironmentState
+ Catalog
+ prior durable discovery/visitor state
-> ReactionResult
```

Reaction rules are data-driven. Example:

```text
requires:
  tags: [rainbell]
  weatherKinds: [Rain]
result:
  visualState: BLOOM_WET
  discovery: rainbell_after_rain
```

Another:

```text
requires:
  tags: [pond, reeds]
  weatherKinds: [Rain]
result:
  visitor: frog
  discovery: pond_chorus
```

`ReactionResult` separates:

- **ephemeral visual state** — safe to recompute whenever inputs change;
- **durable events** — discoveries, first-visit records, growth pulses, or unlocks that carry stable IDs and are applied idempotently exactly once.

Rules must not depend on unordered collection iteration, wall-clock randomness, or frame rate. If visual variety is desired, use deterministic seeds derived from stable state.

### Visitors
Animals are primarily condition-driven visitors rather than owned inventory objects.

MVP visitor families:

- butterfly;
- bee;
- frog;
- bird.

Later examples: dragonfly, ladybug, firefly, owl, hummingbird, luna moth, salamander, fox.

Visitor **presence** is a ReactionEngine result. Visitor **motion** is a lightweight authored presentation state machine. Presentation may be seeded for repeatable variety but cannot grant rewards or mutate logical conditions frame-by-frame.

### Discoveries / Almanac
The existing Almanac expands into three conceptual sections under the same visual language:

1. Species — plants, fungi, visitors.
2. Weather — existing world rules plus encountered Weather Echoes/phenomena.
3. Discoveries — environmental combinations personally triggered by the player.

Undiscovered entries should expose clues where useful rather than a wall of opaque `???` entries.

## Puzzle-to-Terrarium data flow

```text
player draws threads
 -> SimulationEngine.run()
 -> frozen SimResult
 -> rating calculation
 -> PuzzleOutcome
 -> RewardService.apply(outcome)
      -> ONE serialized persistence mutation
         -> LevelRecord
         -> XP
         -> item/collectible grant
         -> WeatherEchoSnapshot
 -> persisted save
 -> Terrarium opens / environment changes
 -> ReactionEngine.evaluate()
 -> ephemeral visual state + durable event IDs
 -> idempotent durable-event application
 -> presentation animations
```

This keeps the original puzzle loop authoritative while making its consequences persist outside the level.

## Animation architecture

### Principle
**The world should visibly respond to its state.**

Weatherloom's miniature environments are not static illustrations. Logical movement should normally be animated while preserving performance and Reduced Motion.

### Already present and preserved
Puzzle presentation already animates rain, snow, fog, drifting wool clouds, moving wind indicators, water ripples/sheens, wetland reeds, flowers, and spinning windmills. New work should reuse the same phase-driven/state-driven philosophy.

### Terrarium animation families

Weather:
- falling rain and snow;
- drifting clouds and mist;
- wind-driven particles;
- later restrained lightning/rainbow transitions.

Water:
- pond ripples;
- stream direction;
- waterfall/foam where authored;
- rain impact rings;
- subtle reflection/light shimmer.

Vegetation:
- species-specific idle movement;
- wind response proportional to plant form;
- bloom/open state transitions;
- growth-stage transitions;
- snow loading/frost variants;
- rain/wet response where visually useful.

Mechanisms/structures:
- windmills and future water wheels respond to environmental state;
- signs/ribbons/chimes move in wind;
- chimney smoke, lantern flicker, and similar ambience only where logical.

Visitors:
- butterfly flutter/land;
- bee loop/pause at bloom;
- frog idle/hop/swim/ripple;
- bird idle/look/peck/hop/takeoff;
- later dragonfly hover/dart and firefly drift/glow.

### Reduced Motion
Each animation is classified as:

- **informational** — preserve state meaning with simpler/slower transition;
- **ambient** — reduce substantially or freeze;
- **decorative particle** — disable/minimize.

Reduced Motion never changes logical simulation, ReactionEngine results, growth, discoveries, or rewards.

### Performance boundary
Do not run a full per-object ecological simulation every frame. Reaction evaluation happens on meaningful events such as:

- puzzle completion;
- Weather Echo change;
- committed layout edit;
- growth pulse;
- season/environment change;
- app restore where durable state needs reconciliation.

The resulting logical/visual snapshot is cached. Animation controllers render that snapshot efficiently and must avoid offscreen work where practical.

## Terrarium editing UX

The approved cloche remains the hero view. Editing is an integrated mode, not a replacement screen with a generic dashboard.

MVP operations:

- select owned item;
- place using snap-assisted logical cells/anchors;
- drag/move;
- rotate through authored rotations where supported;
- store/remove;
- undo;
- redo;
- persist committed edits automatically.

Arbitrary object scaling is not MVP. Authored size/growth variants preserve visual consistency and touch usability.

Accessibility must provide a non-drag alternative for placement/move operations.

## Rewards, XP, and mastery

Use one main Player XP track. Small levels may grant ordinary items or XP; milestone levels unlock creative capability/content families.

Existing ratings remain:

- Seedling — solved;
- Bloom — efficient;
- Flourish — expert/mastery.

Flourish contributes toward visually prestigious Masterwork/Heirloom rewards but never blocks normal progression.

Suggested player-facing rarity/provenance vocabulary:

- Garden
- Notable
- Treasured
- Seasonal
- Masterwork
- Heirloom

Rarity and provenance are separate. Example: `Rainbell Flower / Treasured / Earned: Chapter 2 Flourish`.

Masterwork and Heirloom rewards are never purchasable.

## Economy and store

Not part of Terrarium MVP.

If engagement validates a catalog later, introduce at most one normal soft currency initially (working name: Dew). Store/IAP stays behind an interface and cannot be required for core Terrarium operation.

Explicitly excluded:

- paid power;
- puzzle energy/stamina;
- paid extra moves;
- randomized paid loot boxes;
- mandatory paid land/capacity;
- multi-currency clutter;
- purchased mastery prestige.

## Seasons

Season infrastructure is post-MVP and data-driven. Initial candidates: Spring Bloom, Summer Meadow, Autumn Woodland, Winter Frost.

Seasonal visual transformation and discoveries are desirable. Aggressive FOMO is not. Missed seasonal content must have a documented return/archive path.

Do not use real-world weather APIs for core Terrarium state.

## Save and migration design

A migration engine is required before adding Terrarium state to the save.

Current schema-1 saves must remain readable. The durable shape should evolve toward:

```text
schema
levels
dailyHistory
settings
playerProgression
inventory
terrariumLayout
growthState
discoveries
visitorHistory
lastWeatherEchoSnapshot
appliedDurableEventIds
```

Rules:

- stable string IDs only;
- no serialized runtime classes or bitmap references;
- deterministic/idempotent migrations;
- unknown/deprecated item IDs produce graceful placeholders/ignored instances, never total save loss;
- corruption fallback is explicit and tested;
- all central mutations go through a serialized repository/store mutation boundary;
- reward and durable reaction event IDs make replayed operations idempotent;
- migration tests precede irreversible schema changes.

## Visual contract

`docs/superpowers/UI_STYLE_GUARDRAILS.md` is authoritative.

New systems must preserve:

- glass-cloche hero presentation;
- felt/wool/fiber miniature art;
- embroidered fronts and tactile weather motifs;
- cream/parchment surfaces;
- moss/forest text tones;
- coral primary actions;
- muted natural/weather accents;
- rounded chips/cards/sheets;
- scenic world dominating chrome.

Accessibility or framework defaults never justify a generic Material restyle.

## OpenAI-assisted development harness

OpenAI is a **development tool only**, not a runtime Weatherloom dependency.

Recommended implementation: a Python harness under `tools/ai/` using the OpenAI Agents SDK/Responses API.

### Orchestration style
Use a manager + code-orchestrated hybrid:

- one Orchestrator owns the task graph and proposes the final gate;
- an Integration/Graph Adversary independently challenges convergence and promotion;
- specialists perform bounded work;
- each implementation/work-product agent is independently evaluated by a paired adversarial reviewer;
- independent worker/reviewer pairs may run concurrently;
- central state changes are serialized;
- structured reviewer results are machine-readable.

### Required worker/reviewer pairs

- AgentHarnessWorker <-> AgentHarnessAdversary
- DomainModelWorker <-> DomainModelAdversary
- PersistenceWorker <-> PersistenceAdversary
- RewardWorker <-> RewardAdversary
- WeatherEchoWorker <-> WeatherEchoAdversary
- ReactionWorker <-> ReactionAdversary
- UIWorker <-> UIStyleAccessibilityAdversary
- AnimationWorker <-> AnimationPerformanceReducedMotionAdversary
- ContentWorker <-> ContentSchemaAdversary
- TestWorker <-> MutationEdgeCaseAdversary
- ReleaseWorker <-> SupplyChainSecretAdversary
- Orchestrator <-> IntegrationGraphAdversary

Reviewer agents are final gate roles for the work item they review; they are not recursively reviewed by another reviewer. Their effectiveness is instead validated through deterministic harness tests and seeded defect/eval cases.

### Reviewer contract
Structured output fields:

```text
verdict: APPROVED | NEEDS_CHANGES | REJECTED
findings[]:
  severity
  invariant
  evidence
  failurePath
  requiredFix
```

A worker's patch cannot advance until its paired reviewer returns APPROVED. `NEEDS_CHANGES` loops through worker -> focused tests -> a new independent review pass. A design blocker is documented rather than churned indefinitely.

### Workspace isolation
For coding/refactor work, prefer isolated workspaces so builders do not mutate the integration checkout directly. The Agents SDK Sandbox Agent capability is suitable for this class of workflow, but it is currently beta; the harness must therefore keep the basic orchestration/reviewer contract usable without requiring sandbox-specific APIs. When sandbox mode is enabled, reviewer agents receive a fresh/read-only snapshot or diff rather than inheriting the builder's mutable workspace.

### Model policy
Model IDs are configuration, not product contracts. As of this plan, a reasonable default is:

- architecture/migration/adversarial review: highest-capability model available to the account (for example `gpt-5.6-sol`; optionally newer flagship access when available);
- normal worker tasks: balanced model such as `gpt-5.6-terra`;
- bulk content/doc linting: efficient model such as `gpt-5.6-luna`;
- environment overrides for all model choices.

The harness must not require a rolling/limited model tier.

### Tracing and privacy
Use Agents SDK tracing for development observability where appropriate. Do not include secrets, keystore material, raw API keys, or private credentials in prompts/traces. Sensitive trace capture is disabled by default for repository workflows; deterministic tests disable tracing and live calls.

### Harness tests
Use deterministic Agents SDK testing utilities for orchestration shape. Seed adversarial fixtures such as:

- duplicate inventory grant;
- removed save migration;
- nondeterministic reaction rule;
- UI style drift;
- accidental `INTERNET` permission/runtime API dependency;
- Reduced Motion violation;
- reward granted twice after interrupted claim;
- unknown content ID corrupting save.

The reviewer system is only trusted after it demonstrates that it catches representative seeded defects.

## Graph integrity / `/graphRepair` gate

A literal `/graphRepair` command should be used wherever that installed skill/tool is available. In environments where it is not exposed, run an explicit Graph Integrity Pass covering the same concerns and do not claim the literal command ran.

Required graph checks:

1. dependency direction remains `core simulation -> outcome`, never Terrarium -> puzzle engine;
2. UI does not become state authority;
3. RewardService is the sole puzzle-reward transaction authority;
4. inventory mutations pass through one domain/persistence authority;
5. layout and biological GrowthState remain separate;
6. save migrations remain one-way/idempotent;
7. ReactionEngine remains deterministic/pure for a given input snapshot;
8. durable reaction events are idempotent;
9. OpenAI/runtime networking does not enter the Android app;
10. animation does not mutate authoritative state;
11. store/economy does not become required by basic gameplay;
12. no circular domain dependencies;
13. branch/CI gates remain intact.

Run after shared-domain changes, migrations, converging parallel work, merge-conflict repair, and immediately before integration to `develop`/`main`.

## Parallelism policy

Safe to parallelize after shared contracts are frozen:

- static content definitions;
- isolated UI components;
- animation presentation;
- Almanac data structures;
- test fixtures;
- authored reaction/content data;
- documentation;
- OpenAI harness reviewers;
- release workflow work that does not overlap central Android state files.

Serialize:

- save schema and migrations;
- central domain model contracts;
- inventory mutation authority;
- RewardService transaction semantics;
- navigation architecture;
- economy transaction authority;
- branch merges.

Every parallel implementation/work-product agent has its own adversarial reviewer before convergence.

## Integration cadence

Branch flow:

```text
feature/* -> develop -> main
```

Rules:

- feature branch must pass targeted tests and full applicable CI;
- paired adversarial reviewer must APPROVE;
- Integration/Graph Adversary reviews convergence where multiple branches meet;
- run `/graphRepair` or the explicit Graph Integrity Pass;
- merge feature to `develop` only when green;
- after every one or two completed features, merge green `develop` to `main`;
- rerun the full gate on `main`;
- never force-push `main`;
- stop adding features when a batch is unstable.

## MVP vertical proof

Before scaling content, prove one complete causal chain:

```text
solve representative puzzle
-> RewardService grants Rainbell + XP + WeatherEchoSnapshot{Rain,...}
-> inventory owns Rainbell
-> player places Rainbell
-> layout persists
-> app restarts
-> Rainbell remains in place
-> Rain echo evaluates
-> Rainbell gets bloom/wet visual reaction state
-> a qualifying visitor/reaction can appear
-> discovery persists exactly once
-> Almanac displays the discovery
-> Reduced Motion renders equivalent logical state with less motion
```

Only after this chain is green should content expand toward the planned ~24 MVP placeables and multiple visitor/reaction rules.

## MVP scope

- one Terrarium layout;
- ~24 placeable objects after vertical proof;
- approximately 8 flowers/plants, 3 trees/shrubs, 3 fungi/ground items, 3 geological/environment details, 2 water features, 2 structures, 3 accents;
- MVP Weather Echo kinds: Rain, Snow, Wind, Clear, with simultaneous non-Clear kinds allowed;
- approximately 10 reaction rules;
- 4 visitor families: butterfly, bee, frog, bird;
- inventory and persistent placement;
- move/rotate/store/undo/redo;
- plant growth stages and Growth Pulses;
- one Player XP track;
- puzzle reward bridge;
- discovery registry and Almanac integration;
- state-driven animation and sound pairing;
- save migrations and recovery behavior;
- no economy/store/backend dependency.

## Post-MVP direction

After the causal loop is proven:

- Mist/Warm/Cold Echo tags/fields;
- larger content catalog;
- more visitors;
- paths/streams/bridges;
- chapter Masterwork sets;
- data-driven seasons and archive behavior;
- multiple saved layouts;
- screenshot sharing;
- one soft currency/catalog if engagement justifies it;
- optional direct cosmetic purchases behind a separate entitlement interface;
- additional rare weather phenomena.

## Explicit exclusions

- replacing the deterministic puzzle engine;
- redesigning Weatherloom's approved visual identity;
- fully freeform 3D editing;
- plant death from neglect;
- mandatory watering/hunger;
- energy/stamina;
- randomized paid loot boxes;
- five-currency economies;
- pay-to-progress;
- real-world weather API dependency;
- live PvP;
- chat/guilds/clans;
- always-running full ecosystem simulation;
- mandatory backend/account for core play;
- aggressive seasonal FOMO.

## Definition of done for each feature

A feature is done only when:

- domain behavior is implemented;
- persistence behavior is correct where needed;
- failure/edge cases are handled;
- automated tests exist where appropriate;
- mobile UI behavior is verified where relevant;
- style and Reduced Motion contracts are preserved;
- paired adversarial review is APPROVED;
- Integration/Graph Adversary has no unresolved blocker at convergence;
- `/graphRepair` or explicit Graph Integrity Pass is clean/understood;
- applicable CI is green;
- code is committed and pushed.
