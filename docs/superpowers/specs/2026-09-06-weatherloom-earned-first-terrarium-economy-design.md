# Weatherloom Earned-First Terrarium Economy Design

## Status

Approved product direction as of 2026-09-06. This document extends, rather than replaces, `docs/superpowers/specs/2026-09-03-weatherloom-living-terrarium-design.md`.

The existing deterministic puzzle game remains authoritative. The Terrarium remains offline-first and player-customizable. Store, season, and reward systems must reuse the same inventory, progression, save, and provenance contracts rather than creating parallel ownership models.

## Product thesis

Weatherloom should reward mastery and continued play by letting players build a personal living Terrarium from plants, terrain, structures, weather/environment pieces, and decorations they earn, discover, or later purchase.

The economy is **earned-first**:

- normal progression and core Terrarium customization are earned through play;
- one normal player XP track remains authoritative;
- one seasonal XP track may run alongside it during a season;
- one soft currency, **Dew**, is used for normal shop purchases;
- the shop sells cosmetic/collection variety, never puzzle power;
- `Masterwork` and `Heirloom` rewards are permanently earned-only;
- purchased content enters the same `PlayerInventory` as earned content and carries explicit provenance;
- the core puzzle game, Terrarium, shipped season data, progression, discoveries, and save migration remain usable without a backend.

## Non-negotiable invariants

1. Preserve deterministic puzzle behavior. Store, seasons, XP, Dew, and Terrarium rewards cannot alter `SimulationEngine` outcomes.
2. Preserve the existing Weatherloom visual identity and `docs/superpowers/UI_STYLE_GUARDRAILS.md`.
3. Preserve offline-first core play. A network outage must not prevent puzzle play, local Terrarium editing, growth, discoveries, or already-shipped season content.
4. Keep one primary lifetime XP track. Seasonal XP is scoped to a season and never replaces lifetime XP.
5. Keep one soft currency. Do not add gems, tickets, keys, energy, stamina, or multiple premium currencies.
6. Never sell gameplay power, extra moves, puzzle retries, level skips, deterministic-simulation advantages, or mastery ratings.
7. `Masterwork` and `Heirloom` provenance is earned-only and must never appear in paid or Dew shop offers.
8. No randomized paid loot boxes.
9. No mandatory paid Terrarium capacity, placement slots, or land expansion.
10. A duplicate grant must be idempotent or follow an explicit deterministic conversion rule. Silent duplicate ownership inflation is forbidden.
11. All durable reward mutations use the existing serialized save boundary and stable idempotency keys.
12. UI is presentation only. Compose, animation, Figma, and store screens cannot become game-state authority.
13. Animation presents logical state and honors Reduced Motion.
14. Every save schema change is migration-driven and tested before merge.
15. One or two completed features maximum may accumulate on `develop` before a green `develop -> main` checkpoint.

## Existing repository capabilities to reuse

The current repository already has the important foundations:

- `PlayerXpProgression.kt` with deterministic non-farmable rating XP;
- `PuzzleRewardBridge.kt` translating authored puzzle rewards into durable Terrarium inventory ownership;
- `PlayerInventory`, `TerrariumCatalog`, `TerrariumLayout`, `TerrariumPlacementService`, and `TerrariumSaveService`;
- `GrowthPulseService` and separate persistent `GrowthState`;
- deterministic `WeatherEchoSnapshot` / Terrarium environment flow;
- `ReactionEngine` with recomputable visitor presence and durable discovery events;
- `TerrariumReactionSaveService` as the shared serialized persistence boundary for growth, reactions, and discoveries;
- save migration through schema 6;
- the approved Terrarium cloche home visual language;
- no required Weatherloom backend today.

New economy work must extend these systems rather than duplicating them.

## Acquisition and provenance model

Every owned Terrarium item has two independent concepts:

1. **Content identity** — what the item is.
2. **Acquisition provenance** — how this player obtained it.

Recommended provenance contract:

```text
itemId
variantId
sourceType
sourceId
sourceLabelKey
acquiredSequence
```

`sourceType` is a stable enum-like string set:

```text
level_reward
xp_milestone
chapter_milestone
flourish_mastery
discovery
season_reward
dew_shop
real_money_entitlement
legacy_migration
```

`sourceId` is the stable idempotency/provenance key, for example:

```text
level:chapter2-rain-song
xp:milestone:10
flourish:weavers-trial
season:spring-bloom-2027:tier:12
shop:dew:fern-mossy:v1
purchase:googleplay:sku_terrarium_lantern_01:<verified-entitlement-id>
```

The presentation layer may display friendly provenance such as:

`Rainbell Flower · Treasured · Earned: Chapter 2 Flourish`

or

`Mossy Lantern · Notable · Purchased with Dew`

Provenance must survive save/load and migration. It must never affect deterministic puzzle simulation.

## Reward classes

Player-facing reward rarity/provenance vocabulary:

- `Garden`
- `Notable`
- `Treasured`
- `Seasonal`
- `Masterwork`
- `Heirloom`

Rarity/class and acquisition source are separate. A `Seasonal` item may later return through an archive or rerun while still showing its original acquisition source.

### Earned-only prestige

`Masterwork` and `Heirloom` are reserved for meaningful mastery or long-term accomplishment. They are never available through Dew or real-money purchase.

Examples:

- first Flourish on an authored capstone level;
- Flourish on every level in a chapter;
- a major lifetime XP milestone;
- a difficult discovery chain;
- a season completion mastery reward where the season can later return.

## Unified reward authority

The existing reward path must evolve toward one deterministic reward authority rather than adding one service per source.

Conceptual input:

```text
RewardTrigger
  sourceType
  sourceId
  playerStateSnapshot
  optional level/rating/discovery/season context
```

Conceptual output:

```text
RewardGrantResult
  updatedSave
  grantedItems
  xpGranted
  seasonalXpGranted
  dewGranted
  newlyUnlockedMilestones
  presentationEvents
```

The same stable `sourceId` cannot grant twice.

Examples:

- first authored level completion -> lifetime XP + optional item reward;
- first rating improvement -> only the XP delta already defined by `PlayerXpProgression`;
- first Flourish on a designated level -> mastery item exactly once;
- lifetime XP crossing a milestone -> milestone item exactly once;
- chapter completion -> chapter reward exactly once;
- first discovery -> optional discovery reward exactly once;
- seasonal milestone -> season reward exactly once.

### Perfect-completion rule

Weatherloom must not invent a second rating system called “Perfect.” For reward purposes, a “perfect level completion” means **first achieving the existing `Flourish` rating** on an authored level designated for a mastery reward, or satisfying another explicitly authored mastery condition. The player-facing rating remains Seedling / Bloom / Flourish.

## Lifetime XP

`PlayerProgression` remains the primary lifetime progression model.

Rules:

- existing deterministic cumulative rating XP stays intact;
- replaying the same or lower rating cannot farm XP;
- milestone thresholds are deterministic authored data;
- milestone reward claims are idempotent;
- lifetime XP never resets when a season changes;
- no purchase can directly grant lifetime XP.

## Seasonal progression

A season is shipped data, not a hard-coded UI branch.

Recommended season definition:

```text
seasonId
nameKey
themeKey
startPolicy
endPolicy
archivePolicy
xpRules
rewardTrack
environmentModifiers
catalogOfferTags
```

Initial candidates:

- Spring Bloom
- Summer Meadow
- Autumn Woodland
- Winter Frost

### Seasonal XP

Seasonal XP is separate from lifetime XP but may be earned from the same deterministic completion event.

Rules:

- one active seasonal XP total at a time;
- a solve can award both lifetime XP and seasonal XP according to authored rules;
- season XP cannot alter puzzle difficulty or simulation;
- duplicate/replayed events cannot farm season XP;
- season rewards use stable tier idempotency keys;
- missed seasonal content has a documented archive, rerun, or return path;
- the app must not use aggressive countdown pressure, energy systems, or punitive login streak requirements.

## Dew soft currency

Dew is the only normal soft currency.

Sources may include:

- authored level milestones;
- XP milestones;
- season milestones;
- first-time discoveries;
- later carefully bounded daily/weekly objectives if they remain non-punitive.

Rules:

- all Dew ledger changes have stable reason/source IDs;
- grants and spends are atomic with the same save mutation boundary;
- balance may never go negative;
- duplicate spend requests are rejected/idempotent;
- no random wagering/gambling mechanics;
- Dew cannot purchase `Masterwork` or `Heirloom` items;
- Dew cannot purchase puzzle advantages.

## Terrarium shop

The shop is a catalog over the existing Terrarium ownership model.

Offer examples:

- plants and fungi;
- stones, soil patches, moss, logs, pots;
- pond/reed/water features;
- miniature structures and ornaments;
- decorative weather/environment variants;
- seasonal cosmetic variants;
- non-prestige visual families.

An offer has stable data such as:

```text
offerId
itemId
variantId
priceDew
availabilityPolicy
seasonTags
rotationGroup
purchaseLimit
```

### Rotating offers

Rotating offers are allowed only as a presentation/catalog policy. They must not create inaccessible core content or mastery pressure.

Rules:

- a permanent catalog remains available for baseline customization;
- rotating offers are deterministic from shipped or server-supplied signed catalog data;
- rotation cannot require the backend for already-owned content;
- expired offers cannot remove ownership;
- seasonal items can return through archive/rerun policies.

## Real-money commerce lane — deferred until the Terrarium is mature

Real-money purchase is deliberately later than customizable Terrarium, content scale, unified rewards, seasons, Dew, and the Dew shop.

When implemented:

- Google Play Billing handles the Android purchase UX;
- purchase verification/reconciliation is authoritative outside the client;
- verified purchases produce stable entitlement IDs;
- entitlements grant into the same `PlayerInventory` and provenance model;
- the client never trusts an unverified local purchase callback as permanent ownership;
- purchase restoration/reconciliation is required;
- refunds/revocations remove only the specific paid entitlement where appropriate and never damage unrelated earned ownership;
- if the same item is both earned and purchased, the item remains owned when any valid ownership source remains;
- backend failure cannot break core offline play.

Railway and/or Cloudflare may host future authoritative catalog, entitlement reconciliation, season configuration, telemetry, or webhook handling. No backend is required for the next Terrarium completion checkpoints.

## Ownership-source semantics

Because one item may have multiple acquisition routes, ownership must not be represented as a single boolean if commerce is added.

Conceptually:

```text
OwnedItem
  itemId
  variantId
  ownershipSources: stable ordered set<OwnershipSource>
```

Removing one revoked purchase source cannot remove an item if another earned source still owns it.

This multi-source rule may be introduced before real-money commerce if doing so simplifies migration, but must remain YAGNI-safe and not complicate the immediate local-only reward work unnecessarily.

## Home Terrarium customization

The home cloche remains the hero view. Player customization extends the existing Terrarium layout system.

Placeable content may include:

- plants;
- terrain patches / ground treatments;
- water/environment features;
- stones/logs/pots;
- miniature structures;
- decorative weather/environment objects where logically appropriate.

Actual transient weather such as current Rain/Snow/Wind remains EnvironmentState/Weather Echo driven, not a manually placeable fake simulation state. Cosmetic weather artifacts may exist only when they are clearly decorative and cannot spoof logical conditions.

## UI and Figma contract

No redesign of Weatherloom is approved.

New Almanac, season, reward, Dew, and store surfaces must extend:

- glass-cloche hero presentation;
- felt/wool/fiber miniature art;
- warm cream/parchment sheets;
- moss/forest text;
- coral primary actions;
- Nunito typography;
- rounded tactile cards/chips/sheets;
- quiet bottom navigation;
- scenic world dominance over chrome.

Figma is used to validate placement, accessibility, adaptive layout, season reward-track integration, shop sheets/cards, and purchase-state flows while preserving the existing visual system.

## Backend boundary

Core local domain:

```text
PuzzleSimulation
 -> PuzzleOutcome
 -> Reward authority
 -> Lifetime XP / Season XP / Dew / Inventory / Weather Echo
 -> Terrarium Layout / Growth / Reactions / Discoveries
```

Optional future network domain:

```text
RemoteCatalog
EntitlementVerifier
SeasonConfigSync
TelemetrySink
```

The local domain consumes validated immutable snapshots from the network boundary. It does not call network services from deterministic simulation or reaction code.

## Security and abuse boundaries

For local-only rewards, idempotency and save consistency are the primary requirements.

Before real-money launch, require:

- server-side purchase verification;
- entitlement reconciliation and revocation/refund handling;
- replay-resistant entitlement IDs;
- no client-authoritative price or purchased ownership;
- catalog versioning;
- telemetry for grant/spend/reconciliation failures;
- privacy-minimized event payloads;
- operational alerts for abnormal purchase/entitlement failures.

## Program order

The existing Living Terrarium roadmap remains first:

1. Feature 13 — Almanac Species / Weather / Discoveries expansion.
2. Feature 14 — state-driven Terrarium animation layer.
3. Feature 15 — expand to MVP content/reaction set.
4. Feature 16 — Terrarium accessibility/adaptive-layout hardening.

Then add earned-first economy work:

5. Feature 17 — reward source/provenance contracts and unified idempotency registry.
6. Feature 18 — lifetime XP/chapter/Flourish/discovery Terrarium reward grants.
7. Feature 19 — data-driven season model and seasonal XP.
8. Feature 20 — seasonal reward track and archive/rerun policy.
9. Feature 21 — Dew ledger and deterministic grant/spend contracts.
10. Feature 22 — Dew Terrarium shop catalog and purchase flow.
11. Feature 23 — Weatherloom-style season/shop presentation and accessibility polish.
12. Feature 24 — first production seasonal content pack with economy balancing telemetry hooks.

Deferred commerce lane after engagement/content validation:

13. Feature 25 — platform-neutral entitlement boundary + multi-source ownership migration.
14. Feature 26 — Google Play Billing integration and verified purchase lifecycle.
15. Feature 27 — Railway/Cloudflare authoritative catalog + entitlement reconciliation + webhook path.
16. Feature 28 — production commerce observability, refund/revocation tests, ops alerts, and release gate.

Every 1–2 completed features must checkpoint `develop -> main` with full independent CI.

## Explicit exclusions

This program does not approve:

- pay-to-win or pay-to-progress;
- paid hints/solutions that bypass puzzle reasoning;
- extra move purchases;
- stamina/energy;
- loot boxes;
- real-world weather APIs as core Terrarium state;
- gameplay-changing premium weather;
- multiple soft currencies;
- purchased Masterwork/Heirloom items;
- destructive seasonal FOMO;
- backend-required core play;
- generic Material redesign.
