# Weatherloom Earned-First Terrarium Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the Living Terrarium first, then add unified earned rewards, seasons, one Dew currency, and a cosmetic Terrarium shop while preserving deterministic offline-first play and permanently earned-only prestige.

**Architecture:** Extend the existing `PlayerProgression`, `PuzzleRewardBridge`, Terrarium inventory/layout, shared save mutation boundary, Weather Echo, growth, reaction, visitor, and discovery systems. New economy features add data-driven reward/provenance, season, Dew, and offer layers; real-money commerce remains a later entitlement/reconciliation boundary outside deterministic gameplay.

**Tech Stack:** Kotlin 2.3.10, Jetpack Compose, Kotlin Serialization, SharedPreferences + StateFlow, JVM unit tests, Android Gradle 8.13.2 / Gradle 8.14.1, Python validation tooling, GitHub Actions, Figma for UI validation; Railway/Cloudflare only in the deferred commerce lane.

**Spec:** `docs/superpowers/specs/2026-09-06-weatherloom-earned-first-terrarium-economy-design.md`

## Global Constraints

- Preserve deterministic `SimulationEngine` behavior.
- Preserve `docs/superpowers/UI_STYLE_GUARDRAILS.md`; no generic Material redesign.
- Core puzzle play, Terrarium, progression, discoveries, and shipped season data remain offline-first.
- One lifetime XP track, one seasonal XP track when a season is active, and one soft currency named `Dew`.
- No energy/stamina, extra-move purchases, paid power, loot boxes, or multi-currency clutter.
- `Masterwork` and `Heirloom` are never purchasable with Dew or real money.
- Durable grants/spends use stable idempotency keys and the serialized save boundary.
- Save schema changes require deterministic migration tests before merge.
- UI and animation never own game state.
- Reduced Motion remains a first-class contract.
- Every feature uses RED test -> minimal GREEN implementation -> adversarial review -> Graph Integrity Pass -> PR -> `develop`.
- Maximum 1–2 completed features on `develop` before a green `develop -> main` checkpoint and fresh `main` CI.

---

## Program dependency order

```text
Checkpoint 7: Almanac + Living Animation
    Feature 13 -> Almanac expansion
    Feature 14 -> state-driven Terrarium animation
            |
Checkpoint 8: Content + Accessibility
    Feature 15 -> MVP placeables/reactions/visitors
    Feature 16 -> adaptive/accessibility hardening
            |
Checkpoint 9: Unified Earned Rewards
    Feature 17 -> provenance + reward idempotency contracts
    Feature 18 -> XP/chapter/Flourish/discovery item grants
            |
Checkpoint 10: Seasons
    Feature 19 -> season model + seasonal XP
    Feature 20 -> seasonal reward track + archive/rerun
            |
Checkpoint 11: Dew + Local Shop
    Feature 21 -> Dew ledger
    Feature 22 -> Dew Terrarium shop
            |
Checkpoint 12: Presentation + First Season
    Feature 23 -> season/shop Weatherloom UI
    Feature 24 -> first production season pack + telemetry hooks
            |
Deferred Commerce Lane
    Feature 25 -> entitlement/multi-source ownership boundary
    Feature 26 -> Google Play Billing lifecycle
    Feature 27 -> Railway/Cloudflare verification + catalog sync
    Feature 28 -> commerce observability/reconciliation release gate
```

The program is intentionally split by subsystem. Before executing each checkpoint, create a checkpoint-specific child plan with exact code snippets for its RED/GREEN tests. This roadmap fixes dependency order, ownership boundaries, file responsibilities, and acceptance gates so later child plans cannot silently change architecture.

---

# CHECKPOINT 7 — ALMANAC + LIVING ANIMATION

## Feature 13 — Almanac Species / Weather / Discoveries expansion

**Branch:** `feature/almanac-discoveries`

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/almanac/AlmanacSection.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/almanac/AlmanacEntryViewData.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt` only if a presentation-safe snapshot accessor is missing.
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/almanac/AlmanacEntryViewDataTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/screens/AlmanacScreenContractTest.kt`

**Interfaces:**
- Consumes: existing `terrariumDiscoveries`, Weather Echo history/current environment, botanical/species catalog data.
- Produces: immutable `AlmanacEntryViewData` grouped into `Species`, `Weather`, and `Discoveries` sections.

**Acceptance contract:**
- existing species/rule content remains present;
- `rainbell_after_rain` appears as discovered after Feature 12 state exists;
- undiscovered discovery entries may expose authored clues but never opaque pressure-only counters;
- section ordering is deterministic;
- no runtime enum names leak directly into localized/player-facing copy;
- visual language remains cream/tactile/Weatherloom-native.

**TDD gate:** first commit contains only failing contract tests; implementation follows only after CI confirms RED.

**Adversarial gate:** attack lost existing species, inaccessible tabs/sections, ordering drift, unknown discovery IDs, TalkBack semantics, and UI-state authority.

## Feature 14 — Terrarium state-driven animation layer

**Branch:** `feature/terrarium-living-animation`

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumVisualSnapshot.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumAnimationPolicy.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/terrarium/TerrariumScene.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/terrarium/TerrariumVisualSnapshotTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/terrarium/TerrariumAnimationPolicyTest.kt`

**Interfaces:**
- Consumes: immutable layout, growth state, environment state, `ReactionResult.visualStates`, `ReactionResult.visitors`, and reduced-motion preference.
- Produces: immutable render snapshot and presentation-only animation policy.

**Acceptance contract:**
- Rain/Snow/Wind visuals derive only from environment/logical state;
- Rainbell wet/bloom state derives from reaction/growth state;
- butterfly presence derives only from `ReactionResult.visitors`;
- animation does not write save state, rewards, discoveries, or growth;
- Reduced Motion preserves logical meaning while suppressing ambient/decorative motion;
- recomputing identical logical inputs produces identical logical render state.

**Checkpoint gate:** both feature PRs green -> `develop` green -> Graph Integrity -> `develop -> main` -> fresh `main` Android CI including checksum/artifact upload.

---

# CHECKPOINT 8 — CONTENT SCALE + ACCESSIBILITY

## Feature 15 — Expand to MVP content/reaction set

**Branch:** `feature/terrarium-mvp-content`

**Files:**
- Modify: `android/app/src/main/assets/terrarium_items.json`
- Modify: `android/app/src/main/assets/terrarium_reactions.json`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/TerrariumCatalog.kt` only for schema-compatible validation additions.
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/reaction/ReactionCatalog.kt` only for schema-compatible validation additions.
- Test: `android/app/src/test/java/com/rork/weatherloom/core/terrarium/TerrariumCatalogAdversarialTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/terrarium/reaction/ReactionCatalogAdversarialTest.kt`
- Create: `android/app/src/test/java/com/rork/weatherloom/core/terrarium/TerrariumMvpContentContractTest.kt`

**Target content:**
- approximately 24 placeables across plants, terrain/ground treatments, water features, stones/logs/pots, and miniature structures/decorations;
- approximately 10 data-driven reactions;
- 4 visitor families: butterfly, bee, frog, bird;
- Rain/Snow/Wind/Clear environment semantics with mixed non-Clear states.

**Acceptance contract:** content growth is data-driven rather than bespoke reaction code; stable IDs are unique/canonical; no Masterwork/Heirloom store metadata exists; visitor/discovery rules remain deterministic.

## Feature 16 — Terrarium accessibility/adaptive-layout hardening

**Branch:** `feature/terrarium-accessibility`

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/AlmanacScreen.kt`
- Modify/create focused components under `android/app/src/main/java/com/rork/weatherloom/ui/terrarium/`
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/terrarium/TerrariumAccessibilityContractTest.kt`

**Acceptance contract:**
- 48dp effective touch targets;
- TalkBack semantics for owned item, placement, rotate, store/remove, visitor/discovery state;
- non-drag move/placement alternative;
- compact and expanded width layouts preserve the same Weatherloom visual system;
- Reduced Motion remains compatible with all new content.

**Figma gate:** validate adaptive layouts/accessibility against the existing cloche/cream/coral/Nunito reference. Figma must not introduce a new visual system.

**Checkpoint gate:** two green feature PRs -> `develop` -> `main` -> full CI.

---

# CHECKPOINT 9 — UNIFIED EARNED REWARDS

## Feature 17 — Provenance + reward idempotency contracts

**Branch:** `feature/reward-provenance`

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/core/reward/RewardSource.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/reward/RewardGrantLedger.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/AcquisitionProvenance.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/PlayerInventory.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/reward/RewardGrantLedgerTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/data/RewardProvenanceMigrationTest.kt`

**Interfaces:**

```kotlin
enum class RewardSourceType {
    LEVEL_REWARD,
    XP_MILESTONE,
    CHAPTER_MILESTONE,
    FLOURISH_MASTERY,
    DISCOVERY,
    SEASON_REWARD,
    DEW_SHOP,
    REAL_MONEY_ENTITLEMENT,
    LEGACY_MIGRATION
}

@Serializable
data class AcquisitionProvenance(
    val sourceType: RewardSourceType,
    val sourceId: String,
    val sourceLabelKey: String
)
```

`RewardGrantLedger` owns stable applied `sourceId` keys; it does not calculate puzzle outcomes.

**Acceptance contract:** duplicate source IDs cannot grant twice; migration preserves existing inventory; legacy items receive deterministic `LEGACY_MIGRATION` provenance; provenance does not affect puzzle or reaction rules.

## Feature 18 — Lifetime XP/chapter/Flourish/discovery Terrarium rewards

**Branch:** `feature/unified-earned-rewards`

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/PuzzleRewardBridge.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/PlayerXpProgression.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/reward/RewardCatalog.kt`
- Create asset: `android/app/src/main/assets/terrarium_rewards.json`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/reward/RewardCatalogTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/data/UnifiedEarnedRewardTest.kt`

**Rules:**
- first Flourish on a designated level is the “perfect completion” reward trigger; no new rating tier is introduced;
- XP milestone, chapter milestone, Flourish mastery, and discovery rewards use one data-driven catalog;
- replaying or downgrading a level never repeats a reward;
- Masterwork/Heirloom catalog entries accept only earned source types.

**Checkpoint gate:** features 17+18 green -> checkpoint to `main`.

---

# CHECKPOINT 10 — SEASONS

## Feature 19 — Data-driven season model + seasonal XP

**Branch:** `feature/seasons-core`

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/core/season/SeasonDefinition.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/season/SeasonProgress.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/season/SeasonService.kt`
- Create asset: `android/app/src/main/assets/seasons.json`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/season/SeasonServiceTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/data/SeasonMigrationTest.kt`

**Acceptance contract:** lifetime XP never resets; one active season progress record is authoritative; duplicate solve/source IDs cannot farm seasonal XP; shipped season data works offline; season environment modifiers cannot mutate deterministic puzzle simulation.

## Feature 20 — Seasonal reward track + archive/rerun policy

**Branch:** `feature/season-reward-track`

**Files:**
- Extend: `android/app/src/main/assets/seasons.json`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/season/SeasonService.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/reward/RewardCatalog.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/season/SeasonRewardTrackTest.kt`

**Acceptance contract:** stable tier IDs; exactly-once tier grants; no punitive login streak; every Seasonal reward has an archive/rerun/return policy; Masterwork/Heirloom season rewards remain earned-only.

**Checkpoint gate:** features 19+20 green -> checkpoint to `main`.

---

# CHECKPOINT 11 — DEW + LOCAL TERRARIUM SHOP

## Feature 21 — Dew ledger

**Branch:** `feature/dew-ledger`

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/core/economy/DewLedger.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/economy/DewTransaction.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/economy/DewLedgerTest.kt`

**Interfaces:**

```kotlin
@Serializable
data class DewWallet(
    val balance: Int = 0,
    val appliedTransactionIds: List<String> = emptyList()
)
```

**Acceptance contract:** no negative balances; exact-once grants and spends; invalid/duplicate transaction IDs rejected deterministically; Dew never changes puzzle outcomes; migration defaults safely to zero.

## Feature 22 — Dew Terrarium shop catalog + local purchase flow

**Branch:** `feature/dew-terrarium-shop`

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/core/store/TerrariumOffer.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/store/TerrariumStoreCatalog.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/store/TerrariumStoreService.kt`
- Create asset: `android/app/src/main/assets/terrarium_store.json`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/store/TerrariumStoreServiceTest.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/store/TerrariumStoreCatalogAdversarialTest.kt`

**Acceptance contract:** one atomic operation debits Dew and grants inventory/provenance; Masterwork/Heirloom offers are rejected at catalog validation; duplicate purchase request cannot double-spend; permanent baseline catalog exists; rotation is deterministic data policy; expired offers never remove ownership.

**Checkpoint gate:** features 21+22 green -> checkpoint to `main`.

---

# CHECKPOINT 12 — PRESENTATION + FIRST PRODUCTION SEASON

## Feature 23 — Weatherloom season/shop presentation

**Branch:** `feature/season-store-ui`

**Files:**
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumStoreScreen.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/season/SeasonProgressCard.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/ui/store/TerrariumOfferCard.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/ui/navigation/` route definitions only as needed for the store sheet/screen.
- Test: `android/app/src/test/java/com/rork/weatherloom/ui/store/TerrariumStorePresentationContractTest.kt`

**Figma requirements:**
- extend the existing glass-cloche home composition rather than replace it;
- season progress appears as compact tactile status/chip/card content;
- shop uses cream/parchment sheets/cards and specimen-like item presentation;
- coral remains the limited primary action accent;
- quiet bottom navigation remains secondary;
- 48dp effective targets and TalkBack labels;
- no generic storefront dashboard aesthetic.

## Feature 24 — First production seasonal content pack + balance telemetry hooks

**Branch:** `feature/spring-bloom-season`

**Files:**
- Modify: `android/app/src/main/assets/seasons.json`
- Modify: `android/app/src/main/assets/terrarium_items.json`
- Modify: `android/app/src/main/assets/terrarium_store.json`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/telemetry/EconomyEvent.kt`
- Create: `android/app/src/main/java/com/rork/weatherloom/core/telemetry/EconomyTelemetrySink.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/telemetry/EconomyTelemetryContractTest.kt`

**Acceptance contract:** telemetry sink defaults to no-op/offline-safe; no identifiers beyond what balancing needs; one complete season can be earned and archived/rerun; Dew pricing and XP thresholds are data-driven and test fixtures cover boundary tiers.

**Checkpoint gate:** features 23+24 green -> `main` -> full Android/puzzle/harness/checksum/artifact CI.

---

# DEFERRED COMMERCE LANE — ONLY AFTER TERRARIUM/SEASONS VALIDATE

## Feature 25 — Entitlement boundary + multi-source ownership

Introduce `OwnershipSource` collections only when real-money work begins. A revoked purchase source cannot remove an item still owned through an earned source.

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/core/commerce/Entitlement.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/core/terrarium/PlayerInventory.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/core/commerce/EntitlementOwnershipTest.kt`

## Feature 26 — Google Play Billing lifecycle

Add Android billing only after Feature 25 is green. Local purchase callback creates a pending receipt, never permanent ownership. Verified entitlement reconciliation grants inventory.

**Required tests:** pending, acknowledged, restored, duplicated callback, cancelled, failed, refunded/revoked, offline-at-callback, and app-restart reconciliation.

## Feature 27 — Railway/Cloudflare authoritative catalog + entitlement reconciliation

Backend scope is limited to remote catalog snapshots, purchase verification/reconciliation, webhook/event ingestion, optional season config, and privacy-minimized telemetry. Deterministic puzzle/Terrarium code never calls the network directly.

**Deployment rule:** choose the smallest operational topology after measuring needs. Railway can host the authoritative service/database; Cloudflare can front/cache catalog/config or receive supported edge/webhook traffic where useful. Do not duplicate authority across two independent databases.

## Feature 28 — Commerce observability/release gate

Require purchase/refund/revocation synthetic tests, reconciliation failure alerts, catalog version checks, rollback procedure, privacy review, and production release audit before enabling real-money offers.

**Slack/ops:** alerting is for actionable commerce integrity failures, not player-engagement pressure.

---

# Graph Integrity gates for every checkpoint

Before promotion, explicitly prove:

```text
PuzzleSimulation
  -> PuzzleOutcome / existing reward bridge
  -> reward/progression/economy domain
  -> SaveData / PlayerInventory
  -> Terrarium logical state
  -> UI/animation
```

Forbidden back-edges:

```text
UI -> puzzle authority
Animation -> reward authority
Store -> SimulationEngine
Season -> SimulationEngine mutation
Remote backend -> frame-by-frame Terrarium logic
Commerce callback -> unverified permanent ownership
```

For Features 17+, also prove all item ownership flows converge on one inventory domain and one serialized save boundary.

# CI / merge protocol

For every feature:

- [ ] Create branch from current green `develop`.
- [ ] Commit RED tests first.
- [ ] Require CI to fail for the intended missing behavior only.
- [ ] Implement minimum GREEN behavior.
- [ ] Run targeted tests plus full Android CI.
- [ ] Run adversarial domain review and Graph Integrity Pass.
- [ ] Open PR to `develop`.
- [ ] Require PR CI green before merge.
- [ ] Require post-merge `develop` CI green.
- [ ] After one or two completed features, open `develop -> main` checkpoint PR.
- [ ] Require checkpoint PR CI green.
- [ ] Merge and require fresh `main` push CI green, including APK checksum and artifact upload.
- [ ] Fast-forward/sync `develop` to the resulting `main` checkpoint before the next feature branch.

# Self-review

Spec coverage checked against the approved earned-first direction:

- Living Terrarium remains first: covered by Checkpoints 7–8.
- Unified rewards and exact-once acquisition: Checkpoint 9.
- Reward classes / earned-only prestige: spec + Features 17–18 and catalog validation.
- Seasonal XP and return path: Checkpoint 10.
- Single Dew currency: Checkpoint 11.
- Cosmetic shop with no gameplay power: Feature 22.
- Real money later through same inventory: Features 25–28.
- Railway/Cloudflare deferred to authoritative commerce boundary: Feature 27.
- UI style preserved and Figma used as validation, not redesign: Features 16 and 23.
- 1–2 feature checkpoint cadence: global protocol.

No `TBD`, `TODO`, alternate mastery rating, premium gameplay currency, or backend requirement for core play is permitted by this roadmap.
