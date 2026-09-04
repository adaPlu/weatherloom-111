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
3. **Remain offline-first.** Core play, Terrarium, progression, discoveries, seasons that ship with the app, and saves must not require a backend or an OpenAI API connection.
4. **Animation presents state; it does not invent state.** Rain, clouds, vegetation, water, mechanisms, and creatures may animate richly, but their logical state comes from the puzzle/Terrarium domain model.
5. **Reduced Motion is a first-class contract.** Informational state changes remain understandable while ambient motion is reduced or removed.
6. **UI is never authoritative game-state storage.** Domain services own state; Compose renders and sends intents.
7. **Save evolution is migration-driven.** No destructive schema changes, no serializing runtime objects, and no silent reset on unknown content IDs.
8. **No pay-to-progress architecture.** Store/IAP, if added later, remains outside basic simulation, inventory, layout, and progression authority.
9. **One or two completed features maximum before `develop -> main`.** Red CI never moves forward.
10. **Every implementing agent has an independent adversarial reviewer.** The builder is never the final approver of its own work.

## Existing repository capabilities to reuse

The current app already provides:

- Kotlin/Jetpack Compose Android application targeting API 36.
- 28 authored levels across 9 chapters.
- Four weather thread types: Warm Front, Cold Front, Wind Band, Moisture Ribbon.
- Deterministic fixed-step `SimulationEngine` with replayable `SimState` frames and causal events.
- Terrain and feature vocabulary covering meadows, crops, villages, reservoirs, rivers, lakes, wetlands, forests, mountains, stone, roads, soil, flowers, windmills, and houses.
- Objectives covering reservoirs, flowers, crop freezing, snow, village fog, windmills, flooding, and wetland water.
- Seedling/Bloom/Flourish ratings.
- Offline Daily Forecasts.
- `GameRepository` persistence using JSON in SharedPreferences with StateFlow.
- Nine botanical collectible IDs already earned from level rewards.
- Existing Terrarium cloche renderer and nine fixed planting slots.
- Almanac species collection and world-rule reference.
- Existing rain, snow, fog, cloud, wind, water, reed, flower, and windmill animation in puzzle presentation.
- Reduced Motion, music, and sound preferences.
- Headless Python level validation, JVM determinism tests, lint, debug APK CI, checksum, and retained Actions artifact on the production-readiness branch.

The expansion must evolve these systems instead of duplicating them.

## Architectural overview

```text
                            existing / protected
                      +----------------------------+
                      |    PuzzleSimulation        |
                      | Level + Threads -> SimResult|
                      +-------------+--------------+
                                    |
                                    v
                          +-------------------+
                          | PuzzleOutcome      |
                          | rating + SimResult |
                          +---------+---------+
                                    |
                                    v
                          +-------------------+
                          | RewardService      |
                          +--+--------+-------+
                             |        |
                            XP   WeatherEcho
                             |        |
              +--------------v-+   +--v----------------+
              | Progression    |   | EnvironmentState  |
              +----------------+   +--+----------------+
                                     |
              +----------------------+----------------------+
              |                                             |
              v                                             v
      +------------------+                          +------------------+
      | TerrariumLayout  |------------------------->| ReactionEngine   |
      +--------+---------+                          +----+--------+----+
               ^                                         |        |
               |                                         |        |
      +--------+---------+                               v        v
      | PlayerInventory  |                         GrowthState  Visitors
      +--------+---------+                               |        |
               ^                                         +---+----+
               |                                             |
      +--------+------------+                                v
      | TerrariumCatalog    |                           Discoveries
      +---------------------+                                |
                                                            v
                                                         Almanac
```

Persistence owns serializable player state beneath the mutable domain services. Presentation reads immutable snapshots and emits intents.

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

Content should be data-driven and versioned. Stable IDs are permanent contracts once shipped.

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
Owns placed instances only.

```text
instanceId
itemId
variantId
xNormalized
yNormalized
rotation
logicalFootprint
depthLayer
growthStage
```

The initial implementation uses a 2.5D snap-assisted placement surface. Logical footprints prevent overlap while artwork may visually overflow footprints to preserve the handcrafted look.

### PlayerProgression
One primary XP track. Existing Seedling/Bloom/Flourish remains the performance system; do not create a second perfect-rating concept.

### RewardService
The single authority that translates a completed puzzle outcome into durable progression changes.

It receives a frozen `PuzzleOutcome` and performs one transaction-like mutation covering:

- level record update;
- XP grant;
- direct collectible/item grant;
- mastery progress where applicable;
- Weather Echo derivation/recording;
- daily completion where applicable;
- reward presentation payload.

`PuzzleViewModel` may call this service but must not independently mutate inventory/XP/discovery state.

### WeatherEcho
A compact causal summary derived from the actual puzzle simulation, never random loot.

MVP:

- Rain
- Snow
- Wind
- Clear

Next:

- Mist
- Warm
- Cold

Later authored phenomena may include Rainbow, Thunderstorm, Sunshower, Heavy Snow, and Moonlit Mist.

The derivation function must be deterministic and unit-tested. The same frozen `SimResult` produces the same echo classification.

### EnvironmentState
The Terrarium's current environmental input. It contains the active Weather Echo plus any stable season/environment modifiers. It is not a continuously simulated copy of `SimState`.

### ReactionEngine
A pure, deterministic evaluator:

```text
TerrariumLayout + EnvironmentState + Catalog + previous durable reaction state
                              -> ReactionResult
```

Reaction rules are data-driven. Example:

```text
requires:
  tags: [rainbell]
  weather: Rain
result:
  visualState: BLOOM
  discovery: rainbell_after_rain
```

Another:

```text
requires:
  tags: [pond, reeds]
  weather: Rain
result:
  visitor: frog
  discovery: pond_chorus
```

Rules must not depend on unordered collection iteration, wall-clock randomness, or frame rate. If variety is desired, use deterministic seeds derived from stable state.

### GrowthState
Plants use authored stages such as Seed -> Sprout -> Young -> Mature -> Bloom.

Primary progression is **Growth Pulses** earned from play and relevant weather events, not mandatory real-time watering. Time away never kills or damages content. A nonpunitive catch-up pulse may be added later but cannot become a required timer loop.

### Visitors
Animals are primarily condition-driven visitors rather than owned inventory objects.

MVP visitor families:

- butterfly;
- bee;
- frog;
- bird.

Later examples: dragonfly, ladybug, firefly, owl, hummingbird, luna moth, salamander, fox.

Visitors have lightweight authored behavior state machines for presentation. Their presence is a deterministic reaction outcome; animation is presentation.

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
             -> LevelRecord
             -> XP
             -> item/collectible grant
             -> WeatherEcho
        -> persisted save
        -> Terrarium opens
        -> ReactionEngine.evaluate()
        -> growth / visitor / discovery changes
        -> persisted durable reaction outcomes
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

Reduced Motion never changes logical simulation or rewards.

### Performance boundary
Do not run a full per-object ecological simulation every frame. Reaction evaluation happens on meaningful events such as:

- puzzle completion;
- Weather Echo change;
- layout edit commit;
- growth pulse;
- season/environment change;
- app restore where durable state needs reconciliation.

The resulting state is cached/persisted. Animation controllers render that state efficiently.

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
- save automatically on committed edits.

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

Current schema-1 saves must remain readable. The new durable shape should evolve toward:

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
lastWeatherEcho
```

Rules:

- stable string IDs only;
- no serialized runtime classes or bitmap references;
- deterministic/idempotent migrations;
- unknown/deprecated item IDs produce graceful placeholders/ignored instances, never total save loss;
- corruption fallback is explicit and tested;
- reward/inventory mutations should be written atomically at the repository abstraction level;
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

Recommended implementation: a Python harness under `tools/ai/` using the OpenAI Agents SDK and Responses API.

### Orchestration style
Use a manager/code-orchestrated hybrid:

- one Orchestrator owns the task graph and final gate;
- specialists are exposed as bounded workers;
- each worker output is independently evaluated by a paired adversarial reviewer;
- independent worker/reviewer pairs may run concurrently;
- central state changes are serialized;
- structured reviewer results are machine-readable.

### Required worker/reviewer pairs

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

A worker's patch cannot advance until its paired reviewer returns APPROVED. `NEEDS_CHANGES` loops back through worker -> focused tests -> independent reviewer. A design blocker is documented rather than churned indefinitely.

### Model policy
Model IDs are configuration, not hard-coded product contracts. Default development policy:

- highest-capability available model for architecture, migration, and adversarial review;
- balanced model for normal worker tasks;
- cost-efficient model for bulk content/doc linting;
- environment overrides for all model choices.

The harness must not assume access to a rolling/limited model tier.

### Tracing and privacy
Use Agents SDK tracing for development observability where appropriate. Do not include secrets, keystore material, raw API keys, or private credentials in prompts/traces. Support disabling sensitive trace content and disabling tracing entirely for tests.

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
3. RewardService is the sole reward/inventory transaction entry point;
4. save migrations remain one-way/idempotent;
5. ReactionEngine remains deterministic/pure for a given input snapshot;
6. OpenAI/runtime networking does not enter the Android app;
7. animation does not mutate authoritative state;
8. store/economy does not become required by basic gameplay;
9. no circular domain dependencies;
10. branch/CI gates remain intact.

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
- OpenAI harness reviewers.

Serialize:

- save schema and migrations;
- central domain model contracts;
- inventory mutation authority;
- RewardService transaction semantics;
- navigation architecture;
- economy transaction authority;
- branch merges.

Every parallel worker has its own adversarial reviewer before convergence.

## Integration cadence

Branch flow:

```text
feature/* -> develop -> main
```

Rules:

- feature branch must pass targeted tests and full applicable CI;
- paired adversarial reviewer must APPROVE;
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
-> RewardService grants Rainbell + XP + Rain WeatherEcho
-> inventory owns Rainbell
-> player places Rainbell
-> layout persists
-> app restarts
-> Rainbell remains in place
-> Rain WeatherEcho evaluates
-> Rainbell changes to bloom reaction state
-> a qualifying visitor/reaction can appear
-> discovery persists
-> Almanac displays the discovery
-> Reduced Motion renders equivalent logical state with less motion
```

Only after this chain is green should content expand toward the planned ~24 MVP placeables and multiple visitor/reaction rules.

## MVP scope

- one Terrarium layout;
- ~24 placeable objects after vertical proof;
- approximately 8 flowers/plants, 3 trees/shrubs, 3 fungi/ground items, 3 geological/environment details, 2 water features, 2 structures, 3 accents;
- 4 Weather Echoes: Rain, Snow, Wind, Clear;
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

- Mist/Warm/Cold Echoes;
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
- `/graphRepair` or explicit Graph Integrity Pass is clean/understood;
- applicable CI is green;
- code is committed and pushed.
