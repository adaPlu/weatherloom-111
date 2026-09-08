# Weatherloom App Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended where real isolated subagents/worktrees are available) or `superpowers:executing-plans` to implement this plan task-by-task. Every implementation/work-product agent must have an independent adversarial reviewer. Use `/graphRepair` wherever the literal skill is exposed; otherwise run the explicit Graph Integrity Pass and report that substitution accurately.

**Goal:** Complete Weatherloom as a polished Android release while preserving the original deterministic weather-puzzle game and expanding it into the approved persistent, animated Living Terrarium with customization, growth, visitors, discoveries, progression, accessibility, production release gates, and the planned post-MVP cozy-content systems.

**Architecture:** Preserve the existing deterministic simulation as the root authority. Puzzle outcomes flow through one serialized reward/save boundary into player progression, Terrarium inventory, `WeatherEchoSnapshot`, `EnvironmentState`, the pure `ReactionEngine`, durable growth/discovery/visitor state, and finally presentation. UI and animation render immutable logical state and send intents; they never become state authority. Release, accessibility, localization, performance, backup, and supply-chain systems wrap the same offline-first Android core.

**Tech Stack:** Kotlin 2.3.10, Jetpack Compose/Material 3, Android SDK 36, Gradle 8.14.1 / AGP 8.13.2, Kotlin Serialization, SharedPreferences + StateFlow, Python 3 tooling, GitHub Actions, OpenAI Agents SDK developer tooling only.

**Specs:**
- `docs/superpowers/specs/2026-09-03-weatherloom-living-terrarium-design.md`
- `docs/superpowers/specs/2026-09-03-weatherloom-production-readiness-design.md`
- `docs/superpowers/UI_STYLE_GUARDRAILS.md`
- `docs/superpowers/INTEGRATION_CADENCE.md`

**Historical plans folded into this plan:**
- `docs/superpowers/plans/2026-09-03-weatherloom-living-terrarium.md`
- `docs/superpowers/plans/2026-09-03-weatherloom-production-readiness.md`

## Global Constraints

- Preserve the existing deterministic puzzle engine and authored puzzle semantics.
- Preserve the approved Weatherloom felt/wool/embroidered miniature-diorama visual language.
- Keep core play offline-first; do not add a runtime OpenAI dependency or `INTERNET` permission for developer tooling.
- Keep `compileSdk=36` and `targetSdk=36` for the current release unless a validated platform requirement changes them.
- Do not change `applicationId = "com.rork.weatherloom"` without explicit owner approval before first Play publication.
- Never commit signing secrets, keystore bytes, API keys, or private credentials.
- All third-party GitHub Actions remain pinned to immutable full commit SHAs.
- Save evolution remains explicit, idempotent, and forward-tolerant.
- Animation may present logical state but may not award XP, growth, discoveries, visitors, or rewards frame-by-frame.
- Reduced Motion must preserve state meaning while removing/reducing ambient/decorative motion.
- Every feature follows RED -> GREEN -> adversarial review -> `/graphRepair` or explicit Graph Integrity Pass -> full CI -> commit/push -> PR.
- No more than 1-2 completed features may sit on `develop` before a green `develop -> main` checkpoint.
- Do not merge red or incomplete work into `develop` or `main`.

## Current Repository Snapshot — 2026-09-04

- `main`: `d430e5834768bd5462602e145258e2c53536abe9` — through Feature 8 (`WeatherEchoSnapshot`).
- `develop`: `6323be08ee72bdcafbe7cf14f9390f7e9c2fe547` — additionally includes Feature 9 (`EnvironmentState` + deterministic `ReactionEngine`).
- `feature/terrarium-causal-vertical-slice`: `2fa6aed915b10e1ee7c213830edd6ff0cea47ef5` — RED tests only for Feature 10; production implementation is not complete.
- Current CI already validates authored/daily puzzle content, deterministic OpenAI harness behavior, Android JVM tests, lint, debug APK build, checksum, and retained APK artifact.

## Current Platform Requirements Confirmed During Plan Refresh

1. Google Play requires new apps and updates to target Android 16 / API 36 starting August 31, 2026. Weatherloom already targets API 36.
2. Android 16 large-screen behavior makes adaptive layout work increasingly important; Android 17 removes the temporary large-screen orientation/resizability opt-out.
3. New Play apps use Android App Bundles and Play App Signing; Weatherloom still needs a real upload-key/release workflow.
4. Android 12+ backup policy should use `android:dataExtractionRules`; Android 11 and lower still need legacy backup rules.
5. Compose interactive elements should provide at least 48dp effective touch targets and appropriate semantics/custom accessibility actions for nonstandard interactions.
6. Macrobenchmark + Baseline Profiles remain the recommended way to measure startup/jank improvements.
7. GitHub artifact attestations can establish build provenance for public repositories.
8. OpenAI Agents SDK deterministic testing utilities can test orchestration without live model calls; tracing can include sensitive tool/model data by default, so Weatherloom keeps sensitive trace capture disabled. Sandbox Agents are useful for isolated coding/review but remain beta and optional.

Official references are collected at the end of this plan.

---

# COMPLETION ROADMAP

## Checkpoint A — Finish the Causal Vertical Slice

### Task A1: Persist puzzle Weather Echo and Terrarium environment

**Status:** IN PROGRESS — RED tests already pushed on `feature/terrarium-causal-vertical-slice`.

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/PuzzleSolveService.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/data/TerrariumReactionSaveService.kt`
- Test: existing Feature 10 tests on `feature/terrarium-causal-vertical-slice`

**Interfaces:**
- `PuzzleSolveService.recordSolve(..., weatherEcho: WeatherEchoSnapshot?) -> PuzzleSolveResult`
- `SaveData.terrariumEnvironment: EnvironmentState?`
- `SaveData.appliedTerrariumEventIds: Set<String>` or serialized canonical equivalent
- `TerrariumReactionSaveService.evaluateAndApply(save: SaveData): TerrariumReactionSaveResult`

**Required behavior:**
- A solved puzzle with a derived Rain echo persists the exact deterministic snapshot.
- A solve with no new echo does not erase the prior Terrarium environment.
- Opening/re-evaluating the same Terrarium recomputes ephemeral Rainbell visual tags but applies durable discovery IDs exactly once.
- Save/restart/reload preserves environment and durable event history.
- Schema migration retains all existing levels, XP, collectibles, inventory, layout, growth, settings, and daily history.

**TDD gate:** run `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`; initial Feature 10 tests must fail before production code and pass after implementation.

**Adversary:** stale echo, double durable-event application, restart divergence, schema downgrade, wrong puzzle echo, environment erasure.

**Commit:** `feat: persist puzzle weather into Terrarium reactions`

### Task A2: Integrate Feature 9 + Feature 10 checkpoint to `main`

- Wait for Feature 10 branch CI and PR CI to be fully green.
- Merge Feature 10 -> `develop`.
- Run post-merge `develop` CI.
- Run IntegrationGraphAdversary over `SimResult -> WeatherEchoSnapshot -> SaveData EnvironmentState -> ReactionEngine -> durable IDs`.
- Merge green `develop -> main` because Features 9 and 10 form a two-feature checkpoint.
- Verify fresh `main` CI and retained APK artifact.

---

## Checkpoint B — Make the Terrarium Actually Customizable

The old plan created the domain/layout services but skipped the player-facing editor. This is now a mandatory completion gap.

### Task B1: Render persisted `TerrariumLayout` instead of fixed collectible slots

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/board/TerrariumScene.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/navigation/AppNavigation.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumRenderModel.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/terrarium/TerrariumRenderModelTest.kt`

**Behavior:** map persisted normalized coordinates/depth/rotation into the existing cloche composition; bootstrap unmoved legacy collectibles into deterministic old-style slots; preserve the approved cloche/art hierarchy.

**Adversary:** layout ignored in favor of collectibles list, depth-order instability, moving a plant resetting growth, style drift.

**Commit:** `feat: render persistent Terrarium layouts`

### Task B2: Add Weatherloom-style Arrange mode

**Files:**
- Create: `ui/terrarium/TerrariumEditorViewModel.kt`
- Create: `ui/terrarium/TerrariumEditorState.kt`
- Create: `ui/terrarium/TerrariumInventoryTray.kt`
- Create: `ui/terrarium/TerrariumEditorControls.kt`
- Modify: `ui/screens/TerrariumScreen.kt`
- Modify: `data/GameRepository.kt` with domain-service wrappers only
- Tests: JVM editor-state tests + Compose interaction tests

**Behavior:** enter Arrange from the existing Terrarium screen; select owned item; place via snap-assisted anchors; drag/move; rotate only through authored rotations; store/remove; undo/redo; auto-save committed edits; provide accessible non-drag move controls.

**Visual rule:** no generic builder dashboard; use existing cream/parchment cards, coral primary action, quiet compact controls, and world-first hierarchy.

**Adversary:** UI owning inventory, drag-only accessibility, overlap/invalid placement bypass, undo restoring invalid state, state loss after restart.

**Commit:** `feat: add Terrarium Arrange mode`

**Checkpoint:** B1+B2 -> green `develop` -> `main`.

---

## Checkpoint C — Progression, Growth, and Meaningful Unlocks

### Task C1: Finish player-facing XP progression

The XP ledger exists, but milestone unlock presentation and creative capability unlocks do not.

**Files:**
- Modify: `data/PlayerXpProgression.kt`
- Create: `core/terrarium/ProgressionUnlocks.kt`
- Modify: `ui/screens/TerrariumScreen.kt`
- Create: `ui/terrarium/ProgressionCard.kt`
- Tests: threshold, replay, migration, unlock-boundary tests

**Behavior:** retain cumulative Seedling/Bloom/Flourish XP (100/150/200) and non-farmable rating deltas; expose deterministic milestone unlocks; normal progression never requires Flourish; mastery rewards remain separate prestige.

**Initial milestone contract:** use data-driven milestone IDs rather than hard-coded UI conditionals. Unlock Terrarium editing/ground-cover/plant-family/water-feature/visitor-family capability in staged milestones; final numeric thresholds are stored in one table and test-covered.

**Commit:** `feat: expose player progression milestones`

### Task C2: Implement Growth Pulses

**Files:**
- Modify: `core/terrarium/GrowthState.kt`
- Create: `core/terrarium/GrowthEngine.kt`
- Create: `android/app/src/main/assets/terrarium_growth.json`
- Modify: `data/TerrariumReactionSaveService.kt`
- Tests: growth bounds/idempotence/move-store-replace/restart tests

**Behavior:** Seed -> Sprout -> Young -> Mature -> Bloom according to authored growth profile; pulses come from play/relevant weather events; no plant death, mandatory watering, hunger, or wall-clock farming; moving/storing/replacing preserves biological state.

**Adversary:** duplicate pulse replay, max-stage overflow, clock manipulation, missing profile, move/reset exploit.

**Commit:** `feat: add deterministic Terrarium growth pulses`

**Checkpoint:** C1+C2 -> `main`.

---

## Checkpoint D — Visitors and Discoveries

### Task D1: Durable discovery registry and first visitor

**Files:**
- Create: `core/terrarium/discovery/DiscoveryModels.kt`
- Create: `core/terrarium/discovery/DiscoveryCatalog.kt`
- Create: `android/app/src/main/assets/terrarium_discoveries.json`
- Extend: `core/terrarium/reaction/ReactionModels.kt`
- Modify: `data/SaveData` through migration
- Tests: exactly-once discovery, missing-content, restart, repeated visitor tests

**Behavior:** a qualifying Rain + pond/reeds combination can produce recurring frog presence plus exactly-once `Pond Chorus` discovery. Visitor presence is logical reaction output; animation is presentation only.

**Commit:** `feat: add Terrarium discoveries and first visitor`

### Task D2: Expand Almanac into Species / Weather / Discoveries

**Files:**
- Modify: `ui/screens/AlmanacScreen.kt`
- Create: `ui/almanac/SpeciesSection.kt`
- Create: `ui/almanac/WeatherSection.kt`
- Create: `ui/almanac/DiscoveriesSection.kt`
- Modify: `ui/navigation/AppNavigation.kt`
- Tests: section state/semantics/localized clue tests

**Behavior:** preserve existing species and causal rulebook; add encountered Weather Echoes and personally triggered discoveries; undiscovered entries provide useful clues instead of a wall of opaque `???` entries.

**Commit:** `feat: expand Almanac with weather and discoveries`

**Checkpoint:** D1+D2 -> `main`.

---

## Checkpoint E — Living Animation, Sound, and Haptics

### Task E1: State-driven Terrarium animation controller

**Files:**
- Create: `ui/terrarium/TerrariumVisualState.kt`
- Create: `ui/terrarium/TerrariumAnimationPolicy.kt`
- Modify: `ui/board/TerrariumScene.kt`
- Tests: logical-state mapping + Reduced Motion policy tests

**Animate where logically appropriate:**
- rain strands and droplet/ripple impacts;
- snow drift/accumulation variants;
- cloud and mist drift;
- pond/stream/waterfall ripples, flow, shimmer, rain rings;
- grass/reeds/flowers/tree canopies responding to wind;
- flowers opening and authored growth transitions;
- windmills/water wheels/chimes/signs responding to environment state;
- butterfly/bee/frog/bird authored behavior loops;
- later fireflies/dragonflies/rare visitors.

Existing puzzle rain/snow/cloud/fog/wind/water/reed/flower/windmill animation remains intact.

**Reduced Motion:** informational state changes remain; ambient loops slow/freeze; decorative particles minimize/disable.

**Adversary:** per-frame state mutation, offscreen work, uncontrolled recomposition, animation contradicting reaction state.

**Commit:** `feat: animate living Terrarium state`

### Task E2: Reactive ambience and haptic controls

**Files:**
- Modify: `audio/LoomAudio.kt`
- Create: `audio/TerrariumAudioState.kt`
- Modify: `data/SaveData` and migration to add `hapticsEnabled`
- Modify: Comfort controls in `AlmanacScreen.kt`
- Tests: preference migration and audio-state mapping

**Behavior:** pair logical state with restrained rain/water/windmill/frog/bird/chime ambience; add haptic toggle; never make sound/haptics authoritative gameplay state.

**Commit:** `feat: add Terrarium reactive audio and haptics`

**Checkpoint:** E1+E2 -> `main`.

---

## Checkpoint F — MVP Content Scale

### Task F1: Expand placeable catalog to the approved MVP set

**Files:**
- Modify: `assets/terrarium_items.json`
- Add approved felt assets under `res/drawable-nodpi/`
- Extend content validator to load/validate Terrarium catalog

**Target content:** approximately 24 placeables: 8 flowers/plants, 3 trees/shrubs, 3 fungi/ground items, 3 geological/environment details, 2 water features, 2 structures, 3 accents.

**Rules:** use data rather than item-specific Kotlin; stable IDs; provenance; Garden/Notable/Treasured/Seasonal/Masterwork/Heirloom vocabulary; no duplicate junk rewards.

### Task F2: Expand reactions and visitors to MVP breadth

**Files:**
- Modify: `assets/terrarium_reactions.json`
- Modify: `assets/terrarium_discoveries.json`
- Add visitor definitions if a separate `terrarium_visitors.json` improves schema clarity
- Extend validator/tests

**Target:** at least 10 authored causal reactions and visitor families Butterfly / Bee / Frog / Bird using Rain/Snow/Wind/Clear Weather Echo snapshots.

**Commit pair:** `content: expand Terrarium MVP catalog` / `content: expand Terrarium reactions and visitors`

**Checkpoint:** F1+F2 -> `main`.

---

## Checkpoint G — Accessibility, Localization, and Adaptive UI

### Task G1: Resource-backed copy + pseudolocale

- Create/expand `res/values/strings.xml` and plural resources.
- Replace player-facing literals across navigation, Terrarium, Levels, Daily, Almanac, puzzle draw/playback/results, editor and settings.
- Enable pseudo-locales for debug/test builds.
- Add instrumentation smoke for expanded strings.

### Task G2: High Contrast + Reduced Motion completion

- Wire existing `SaveData.highContrast` into theme selection and Comfort UI.
- Keep Weatherloom palette families; increase semantic contrast without generic Material restyle.
- Inventory and gate every continuous/entrance animation under Reduced Motion.
- Add representative automated accessibility checks.

### Task G3: Accessible puzzle board and editor

- Create `ui/board/BoardAccessibility.kt`.
- Add semantic descriptions for terrain/weather/cell state.
- Add non-drag accessibility actions for weather placement and Terrarium editing.
- Preserve pointer drawing as the primary visual interaction.
- Enforce >=48dp effective targets, state descriptions, traversal order, and TalkBack smoke tests.

### Task G4: Adaptive layout architecture

- Create `ui/adaptive/WindowLayout.kt` with one app-level width policy.
- Test at 360dp, 600dp+, 840dp+.
- Preserve the same Weatherloom components and visual hierarchy while reflowing them.
- Adapt puzzle/Almanac/Terrarium first, then Levels/Daily/results.
- Do not rely on portrait lock as the long-term large-screen strategy.

**Checkpoint cadence:** G1+G2 -> `main`; G3+G4 -> `main`.

---

## Checkpoint H — Product Completeness UX

### Task H1: Tap-to-inspect weather/cell panel

Create `ui/puzzle/CellInspector.kt`; wire board/playback taps to localized terrain/elevation/temperature/moisture/cloud/water/snow/fog/wind summaries; compact bottom sheet + expanded supporting pane; screen-reader accessible.

### Task H2: Guided tutorial and replayable help

Create `ui/tutorial/TutorialState.kt` + `TutorialOverlay.kt`; finite first-level tutorial, skip/replay, Reduced Motion, TalkBack; use existing `tutorialSeen` persistence and add replay from Almanac/Help.

### Task H3: Reset/progress management

Expose the existing repository reset capability through an explicit destructive confirmation flow; preserve settings if that UX contract is selected; verify restart behavior.

### Task H4: Malformed-content recovery UX

Represent `LevelLibrary` load failures explicitly; add `ContentErrorScreen`; no production stack traces/file paths; unit parser-failure and instrumentation recovery tests.

**Checkpoint cadence:** H1+H2 -> `main`; H3+H4 -> `main`.

---

## Checkpoint I — Test, Screenshot, and Performance Gates

### Task I1: Device/instrumentation test matrix

Add Compose instrumentation coverage for core navigation, puzzle solve/playback/result, Terrarium editor, growth/discovery, accessibility, pseudolocale, restart, and compact/expanded layouts.

### Task I2: Screenshot regression suite

Add deterministic golden tests for canonical states:
- Terrarium Home;
- Draw the Front;
- The Loom Runs/playback;
- The Weather Settles/result;
- Almanac/Comfort;
- Terrarium Arrange;
- high-contrast and expanded-layout variants.

Freeze animation phase and use deterministic fixtures; CI uploads diffs on failure.

### Task I3: Macrobenchmark + Baseline Profile

Create `android/benchmark/`; benchmark cold startup and critical journeys (Terrarium -> Continue -> puzzle -> playback/result, Levels -> puzzle, Almanac scroll, Arrange mode). Generate Baseline Profile, compare enabled/disabled startup/rendering, report traces in CI before defining stable blocking thresholds.

**Checkpoint:** I1+I2 -> `main`; I3 may share the next checkpoint with release optimization.

---

## Checkpoint J — Android Production Release

### Task J1: Production signing workflow

**Files:**
- Create `.github/workflows/android-release.yml`
- Create `docs/release/ANDROID_SIGNING.md`
- Modify `android/app/build.gradle.kts`

Read signing only from `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Decode keystore only into runner temp storage, delete in `always()`, build signed `app-release.aab`, verify certificate, checksum, and upload artifact. Never silently fall back to debug signing.

### Task J2: Release identity/version gate

Create `tools/check_release_identity.py`, tests, and `docs/release/RELEASE_IDENTITY.md`. Verify tag/versionName/versionCode/applicationId contract before signing. Keep current package ID unless owner explicitly approves a change before first Play publication.

### Task J3: Artifact provenance

Add GitHub artifact attestation with minimum required permissions and immutable action SHA; verify attestation for a real signed release artifact.

### Task J4: Explicit backup policy

Create `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, and `docs/release/BACKUP_POLICY.md`; include only intended Weatherloom SharedPreferences/save state; wire both manifest attributes and test restore expectations.

### Task J5: Dependency/branch governance

Create `.github/dependabot.yml`; add dependency review if supported; require PR + Weatherloom CI checks on `main`, then `develop`; block force pushes/deletion. Keep AI dependency advice informational—deterministic GitHub Actions remain enforcement authority.

### Task J6: Release optimization

Enable R8/minification/resource shrinking only after instrumentation/screenshot gates exist; add only evidence-driven keep rules; compare release artifact size and critical flows.

### Task J7: Release checklist + Play internal test

Create `docs/release/RELEASE_CHECKLIST.md` and README release section. Verify:
- API 36 target;
- application ID decision;
- signed AAB/upload key;
- Play App Signing enrollment;
- version increment;
- content/JVM/lint/build/device/accessibility/screenshot gates;
- backup/restore;
- pseudolocale;
- phone/tablet/foldable smoke;
- checksum + attestation;
- current Play policy/Data Safety/content-rating/store-listing requirements;
- internal testing track before production rollout.

**Checkpoint cadence:** J1+J2 -> `main`; J3+J4 -> `main`; J5+J6 -> `main`; J7 is the release-candidate gate.

---

# POST-MVP / PLANNED EXPANSION ROADMAP

These remain planned product features but are not required to prove the first complete causal Living Terrarium loop. Implement them after the Android v1 quality gates above unless explicitly pulled earlier.

## K1 — Additional Weather Echoes and phenomena

Extend the snapshot schema compatibly with Mist, Warm, Cold, then authored rare phenomena such as Rainbow, Thunderstorm, Sunshower, Heavy Snow, Moonlit Mist. Continue deriving from actual simulation outcomes; do not use a real-world weather API.

## K2 — More visitors and habitats

Add Dragonfly, Ladybug, Firefly, Owl, Hummingbird, Luna Moth, Salamander, Fox as authored visitor families with habitat prerequisites and lightweight behavior loops. No feeding/hunger/health/breeding obligations.

## K3 — Paths, streams, bridges, and larger structures

Add placeable path segments, stream pieces, bridges, ponds/waterfalls, cottage/garden/woodland structures while keeping the snap-assisted 2.5D editor rather than a freeform 3D engine.

## K4 — Masterwork / Heirloom mastery sets

Use existing Flourish performance as mastery signal. Add chapter milestone rewards and visually prestigious non-purchasable objects. Normal progression remains achievable without Flourish.

## K5 — Seasons + Archive Garden

Data-driven Spring Bloom / Summer Meadow / Autumn Woodland / Winter Frost, later Mushroom Season / Moonlight Garden / Pollinator / Rain Festival. Seasonal transforms may affect visuals, visitors, discoveries, and content. Missed content returns through season reruns or Archive Garden; no aggressive permanent FOMO.

## K6 — Multiple saved Terrarium layouts

Add 2-3 explicit layout slots only after single-layout editing is stable. Switching layouts must not duplicate inventory, growth, discoveries, or rewards.

## K7 — Screenshot sharing

Generate a clean share image of the Terrarium without app/browser chrome; no account or social graph required.

## K8 — One soft currency + catalog, only if engagement justifies it

Working name `Dew`. Use for ordinary cosmetic/catalog items only. Do not add multiple currencies, energy, paid moves, or pay-to-progress.

## K9 — Optional direct cosmetic purchases

If monetization is approved later, use direct cosmetic SKUs/bundles behind a separate entitlement interface and restore purchases correctly. Masterwork/Heirloom mastery items are never purchasable. Store failure/offline state must not break core Terrarium play.

## K10 — Platform port feasibility

After Android v1 contracts stabilize, create a separate spec before iOS/Desktop/Web implementation. First inventory which simulation/content contracts can remain pure Kotlin or portable reference behavior.

---

# EXPLICITLY NOT PLANNED / CUT

- replacing the deterministic puzzle engine;
- redesigning the approved Weatherloom visual identity;
- fully freeform 3D editor;
- plant death from neglect;
- mandatory watering/hunger;
- creature breeding as a core system;
- five-currency economy;
- loot boxes;
- energy/stamina;
- pay-to-progress;
- real-world weather API dependency;
- live PvP;
- guilds/clans/chat;
- always-running full ecosystem simulation;
- mandatory backend/account for core play;
- aggressive seasonal FOMO.

---

# STATUS INVENTORY — IMPLEMENTED

## Core game

- Native Android/Kotlin/Jetpack Compose app, min SDK 24, target/compile SDK 36.
- 28 handcrafted puzzles across 9 chapters.
- Four core threads: Warm Front, Cold Front, Wind Band, Moisture Ribbon.
- Deterministic fixed-step simulation and replayable state history.
- Terrain/elevation/temperature/moisture/cloud/fog/rain/snow/wind/runoff/water-storage interactions.
- Reservoir, flower, windmill, fog, snow, crop-freeze, wetland and flood objectives.
- Freehand weather drawing, inventories/limits, undo/redo/clear, hints/canonical solution support, replay/playback and causal event explanations.
- Seedling/Bloom/Flourish ratings, attempts, best strokes/cells and level unlock progression.
- Deterministic offline Daily Forecast and history/streak.
- Existing Terrarium home/cloche with nine botanical collectibles.
- Almanac/species/rulebook baseline.
- Music, rain/wind ambience and reactive SFX.
- Existing puzzle animation for rain, snow, clouds, fog, wind, water, reeds, flowers and windmills; basic Terrarium specimen sway.
- Reduced Motion, music and sound preferences.
- Crop amber tone softened to the approved quieter wheat/felt token.

## Determinism, data, persistence, CI

- Kotlin deterministic simulation tests and Python authored/daily content validator.
- Replay hash includes mutable simulation state needed for deterministic verification.
- Explicit save migration engine and canonicalization.
- Serialized `SaveStateMutator` preventing lost concurrent updates.
- Terrarium catalog/item/inventory/layout/growth-state domain contracts.
- Nine existing botanical IDs mapped into `terrarium_items.json`.
- Pure placement/move/remove operations and durable Terrarium save/load path.
- Legacy collectible migration/bootstrap into Terrarium inventory.
- Puzzle reward -> Terrarium inventory bridge with idempotent provenance.
- Player XP ledger: Seedling 100 / Bloom 150 / Flourish 200 cumulative best-rating awards, replay-safe deltas, migration backfill.
- Deterministic `WeatherEchoSnapshot` derivation for Rain/Snow/Wind/Clear, including mixed weather and stable IDs.
- `EnvironmentState`, data-driven `ReactionEngine`, Rain + Rainbell bloom/wet visual reaction and durable discovery candidate on `develop`.
- OpenAI Agents SDK developer-only adversarial harness with structured worker/reviewer pairs, deterministic no-live-API tests, graph-integrity fixtures, tracing-sensitive-data disabled for live repo workflows.
- GitHub Actions on feature/develop/main: content validation, deterministic harness tests, JVM tests, lint, debug build, checksum, retained APK artifact.
- Immutable SHA-pinned GitHub Actions.
- Canonical UI style guardrail and short feature->develop->main integration cadence documented.

---

# STATUS INVENTORY — PARTIAL / IN PROGRESS

- Feature 10 puzzle -> Weather Echo persistence -> ReactionEngine -> restart/idempotent durable-event vertical slice: RED tests committed/pushed; production implementation not complete.
- Terrarium customization: domain placement exists, but current home rendering still uses legacy fixed planting-slot presentation and there is no player-facing Arrange editor.
- Growth: durable `GrowthState` contract exists, but Growth Pulse progression is not implemented.
- XP: storage/replay-safe award logic exists, but player-facing milestone unlocks/progression presentation are not complete.
- High Contrast: persisted flag/setter exists, but theme/Comfort UI wiring is incomplete.
- Reduced Motion: supported in several existing animation paths, but not yet audited/gated across every planned animation/transition.
- Tutorial: persistence flag exists, but no complete guided tutorial/replay UX.
- Reset: repository reset exists, but no player-facing destructive-confirmation/progress-management UI.
- Release: debug CI/artifact retention exists; signed production AAB workflow does not.
- Backup: `allowBackup=true` exists; explicit modern/legacy backup rules do not.

---

# STATUS INVENTORY — STILL TO IMPLEMENT

## Living Terrarium
- Complete Feature 10 environment persistence/durable-event vertical slice.
- Persisted-layout rendering in the cloche.
- Arrange editor: place/move/rotate/store/undo/redo/non-drag accessibility.
- Player-facing XP milestone/unlock system.
- Growth Pulse engine and authored growth progression.
- Visitor state and discovery registry.
- Almanac Species/Weather/Discoveries expansion.
- State-driven Terrarium rain/snow/cloud/mist/water/vegetation/mechanism/animal animation.
- Reactive Terrarium ambience and haptic control.
- Expand to ~24 MVP placeables, ~10 reactions, 4 visitor families.

## Accessibility/product UX
- Resource-backed strings/localization foundation.
- Pseudolocale/layout safety gate.
- High Contrast visual theme.
- Complete Reduced Motion audit.
- Puzzle-board semantics and non-drag actions.
- >=48dp custom touch-target/traversal audit.
- Adaptive phone/tablet/foldable layouts.
- Tap-to-inspect cell/weather panel.
- Guided tutorial and replay help.
- Reset/progress management UX.
- Malformed-content recovery screen.

## Verification/performance/release
- Instrumented Compose/device tests.
- Screenshot regression suite.
- Macrobenchmark + Baseline Profile.
- Production signed AAB workflow.
- Release identity/version gate.
- Artifact attestation/provenance.
- Explicit backup rules.
- Dependabot/dependency-review/branch-protection governance.
- Release minification/resource shrinking after regression gates.
- Formal release checklist, Play internal test and final Play policy/Data Safety/content/store-listing review.

## Planned post-MVP expansions
- Mist/Warm/Cold and rare Weather Echo phenomena.
- More visitors/habitats.
- Paths/streams/bridges/structures.
- Masterwork/Heirloom chapter mastery sets.
- Seasons + Archive Garden.
- Multiple Terrarium layouts.
- Screenshot sharing.
- One soft currency/catalog if validated.
- Optional direct cosmetic purchases with restoration.
- Separate iOS/Desktop/Web feasibility plan after Android v1.

---

# REQUIRED TEST MATRIX BEFORE ANDROID V1 RELEASE

Every relevant checkpoint must cover:
- fresh/default save;
- schema-1 through current migration path;
- migration idempotence and corrupt/future fields;
- app restart and background/foreground where relevant;
- no runtime network/OpenAI dependency for core play;
- duplicate/replayed reward application;
- XP replay/rating-upgrade behavior;
- inventory/layout consistency and invalid/deprecated item references;
- editor placement/move/rotate/store/undo/redo;
- growth bounds and move-without-reset;
- Weather Echo mixed cases and stable IDs;
- reaction/discovery exactly-once semantics;
- visitor prerequisites;
- Reduced Motion/High Contrast;
- TalkBack semantics and non-drag alternatives;
- 48dp effective touch targets;
- 360dp, 600dp+, 840dp+ layouts;
- deterministic core regression and authored/daily content validation;
- lint/build/debug artifact;
- signed release AAB, signer verification, checksum, attestation;
- backup/restore;
- pseudolocale;
- screenshot goldens;
- Macrobenchmark/Baseline Profile evidence.

# AGENTIC EXECUTION POLICY

For each task:

```text
builder
 -> focused RED test
 -> minimal GREEN implementation
 -> focused tests
 -> paired adversarial reviewer
    -> APPROVED: continue
    -> NEEDS_CHANGES: repair -> tests -> fresh review
    -> REJECTED: stop/escalate design blocker
 -> /graphRepair OR explicit Graph Integrity Pass
 -> full applicable CI
 -> commit + push
 -> PR to develop
```

At convergence:

```text
post-merge develop CI
 -> IntegrationGraphAdversary
 -> /graphRepair or explicit Graph Integrity Pass
 -> develop -> main after 1-2 completed features
 -> main CI + retained artifact
```

Parallelize only independent targets after contracts freeze: content data, isolated UI components, animation presentation, docs, test fixtures, release workflow files, and reviewer/eval work. Serialize save-schema migrations, `SaveData`, inventory authority, reward transaction semantics, navigation-wide refactors, and branch merges.

# OPENAI DEVELOPER TOOLING COMPLETION

OpenAI remains a development accelerator, never a runtime game dependency.

Keep the current Agents SDK harness and add the following only as development tooling:
- focused eval cases for known failure classes (duplicate reward, missing migration, style drift, runtime network addition, nondeterministic reaction, Reduced Motion violation);
- deterministic `ScriptedModel` CI remains the blocking orchestration test;
- optional live/manual eval lane using an account-configured model and `OPENAI_API_KEY` outside the repo;
- optional Sandbox Agent execution for isolated workspace-centric coding/review, behind a feature flag because Sandbox Agents are beta;
- reviewer agents receive immutable diff/snapshot inputs rather than sharing builder mutable state;
- `trace_include_sensitive_data=false` for live repository workflows and tracing disabled in deterministic tests;
- never expose signing material, tokens, API keys, or private credentials in prompts/traces.

# OFFICIAL RESEARCH REFERENCES

- Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Android adaptive orientation/resizability: https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability
- Android 17 restriction changes: https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored
- Compose accessibility semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Compose minimum touch target guidance: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android Auto Backup: https://developer.android.com/identity/data/autobackup
- Android app signing: https://developer.android.com/studio/publish/app-signing
- Play bundle upload: https://developer.android.com/studio/publish/upload-bundle
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Baseline Profile measurement: https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile
- GitHub artifact attestations: https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations
- GitHub Dependabot/Actions: https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-on-actions
- OpenAI Agents SDK: https://openai.github.io/openai-agents-python/
- OpenAI deterministic Agents SDK testing: https://openai.github.io/openai-agents-python/testing/
- OpenAI Agents SDK tracing: https://openai.github.io/openai-agents-python/tracing/
- OpenAI Sandbox Agents: https://openai.github.io/openai-agents-python/sandbox/guide/

# PLAN SELF-REVIEW

- **Spec coverage:** deterministic gameplay, Living Terrarium, customization, XP, growth, visitors/discoveries, animation, accessibility, adaptive UI, release, testing, performance, seasons, optional economy/monetization and future ports are all mapped.
- **Stale-plan correction:** player-facing Arrange mode is explicitly restored as a missing required feature; Feature 9 is recognized as implemented on `develop`; Feature 10 is correctly marked RED/in-progress rather than complete.
- **No placeholder scope:** every release-blocking system has a concrete target and test gate; conditional post-MVP features are explicitly marked conditional rather than silently required for v1.
- **Type/authority consistency:** simulation -> Weather Echo -> Environment -> Reaction Engine -> durable save -> presentation remains one-way; UI/animation never becomes authority.
- **Platform currency:** current API 36 Play requirement, Android 16/17 adaptive direction, Play App Signing, modern backup rules, accessibility guidance, Baseline Profiles and GitHub attestations are incorporated.
