# Weatherloom Production Readiness Design

## Status
Approved direction for the next program increment. Baseline is frozen at `develop@c4ef7dca3f3ac1a49b57b1392f38795ce6bbe3ce`; implementation happens on `feature/release-readiness` until reviewed and merged.

## Goal
Move Weatherloom from a repaired Android prototype into a production-ready, accessible, testable, releasable Android game without weakening its deterministic offline-first architecture.

## Current baseline
- Native Kotlin + Jetpack Compose Android app.
- `compileSdk=36`, `targetSdk=36`, `minSdk=24`.
- 28 validated authored levels, deterministic daily variants, unit tests, lint, and debug build all green.
- Offline-first; no `INTERNET` permission.
- Current application ID and namespace: `com.rork.weatherloom`.
- Release builds are intentionally unsigned until a production signing path exists.
- Save data is a JSON payload in `SharedPreferences`, schema version `1`.
- `highContrast` already exists in save state but is not wired into the theme/UI.
- `android:allowBackup="true"` is currently present without explicit backup extraction rules.

## External constraints confirmed in research
1. Google Play requires new Android mobile apps and updates submitted from August 31, 2026 to target Android 16 / API 36 or higher. Weatherloom already targets API 36.
2. An Android application ID is the Play Store identity. Once the app is published, changing it creates a different Play app. Therefore the final package/application ID is a release gate, not an incidental refactor.
3. Android 16 expects adaptive layouts on large screens. For apps targeting API 36, orientation/resizability/aspect-ratio restrictions are ignored on large displays in many cases, so layouts must tolerate wide/rotated windows.
4. Compose accessibility guidance requires meaningful semantics and recommends at least 48dp touch targets. Compose accessibility checks can flag contrast, target size, and traversal issues.
5. Android localization guidance recommends moving UI copy to default resources and using `stringResource` in Compose; the default resource set must remain complete.
6. Android 12+ backup behavior should be controlled with `android:dataExtractionRules`, while legacy devices need compatible backup rules where applicable.
7. Macrobenchmark and Baseline Profiles are the recommended Android tools for measuring startup and critical Compose user journeys; Baseline Profiles should be generated from real critical user journeys rather than handwritten guesses.
8. GitHub Actions artifacts are immutable archives and expose a SHA-256 artifact digest. Artifact attestations can establish build provenance for public repositories.
9. GitHub branch/ruleset protection can require pull requests and successful status checks before changes reach protected branches. Dependency review can reject newly introduced vulnerable dependencies.

## Architectural approach

### Recommended: release-first vertical slices
Build production readiness as a chain of independently shippable vertical slices. Each slice must leave a green build and must produce a user- or maintainer-visible improvement. Do not mix platform-port work into this increment.

Order:
1. Build distribution
2. Production signing/release
3. Accessibility + adaptive UI
4. Persistence + localization
5. Instrumented quality
6. Performance
7. Product polish
8. Release governance
9. Optional ports only after Android release contract is stable

This order minimizes rework: later UI, persistence, and performance work all run through a trustworthy build/release pipeline.

### Alternative A: UI-first
Accessibility, high contrast, inspect mode, tutorial, and localization first; release engineering last.

Rejected because it delays the ability to distribute reproducible test builds and weakens feedback from real devices.

### Alternative B: multiplatform-first
Extract deterministic core into a shared module and pursue Android/Desktop/Web/iOS in parallel.

Rejected for this increment because the Android product contract, save format, accessibility behavior, and release identity are not yet frozen. Porting now would multiply unstable interfaces.

## Workstreams

### 1. Build distribution
- Extend Android CI so every successful `develop`/feature build retains the debug APK as an immutable GitHub Actions artifact.
- Generate a SHA-256 checksum file next to the APK.
- Use `if-no-files-found: error` so artifact publishing cannot silently succeed without a build.
- Keep retention intentionally short for CI test builds (14 days) to avoid unbounded storage.
- Do not grant write permissions merely to upload ordinary workflow artifacts.

Exit criteria: every green Android CI run exposes an installable APK and checksum.

### 2. Production signing and Play release
- Make an explicit final application-ID decision before first Play publication. Do not auto-rename `com.rork.weatherloom` without owner approval because publication makes the ID effectively permanent.
- Add a dedicated release workflow triggered manually and by version tags.
- Build a release AAB using a production upload key sourced from repository/environment secrets.
- Verify signer/certificate identity before accepting an artifact.
- Generate SHA-256 checksums and GitHub artifact attestations for release binaries.
- Keep debug/test builds completely separate from production signing.
- Add monotonic version-code handling and tag/version consistency checks.

Exit criteria: a tagged commit can produce a verified signed AAB with provenance without exposing key material.

### 3. Accessibility and adaptive UI
- Wire `SaveData.highContrast` into a real high-contrast color scheme and expose the switch in Comfort settings.
- Audit all custom Canvas/gesture surfaces. The puzzle board must expose a usable accessibility model rather than one unlabeled drawing surface.
- Add a non-drag alternative to drawing/selecting where feasible: selectable weather thread + accessible cell/path controls or a simplified stepwise path builder for assistive technologies.
- Ensure all interactive controls have effective touch targets of at least 48dp.
- Add state descriptions/content descriptions/custom actions where Material defaults do not capture gameplay intent.
- Verify traversal order for puzzle controls, timeline, result actions, tabs, and settings.
- Make reduced motion affect Compose entrance/visibility transitions in addition to painter drift/sway.
- Centralize window-size decisions and adapt navigation/content for compact, medium, and expanded widths; avoid scattering width checks through screens.
- Preserve deterministic simulation behavior regardless of layout or accessibility mode.

Exit criteria: TalkBack can navigate core flows; automated accessibility checks are green on representative screens; compact and expanded layouts remain usable.

### 4. Persistence and localization
- Replace implicit `SaveData(schema=1)` decoding with an explicit migration pipeline (`CURRENT_SAVE_SCHEMA`, migration functions, corruption fallback policy).
- Add tests for old-schema migration, unknown fields, corrupt payloads, and reset behavior.
- Decide backup intent explicitly. Default recommendation: back up player progress/settings because the save contains no credentials or sensitive server tokens; add explicit modern and legacy backup rules covering only the Weatherloom SharedPreferences payload.
- Add a destructive reset flow with confirmation and separate controls for progress vs settings if useful.
- Move all player-facing hard-coded strings into `res/values/strings.xml`; Compose reads via `stringResource`/plural resources.
- Keep authored level narrative/content localization separate from simulation JSON identity/metrics. Introduce stable string keys rather than translating enum/metric identifiers.
- Add pseudolocale testing before adding real translations.

Exit criteria: save data survives schema upgrades predictably; backup behavior is explicit; default resources are complete; pseudolocale does not break core layouts.

### 5. Instrumented quality
- Add Android instrumentation test dependencies and a dedicated test module/configuration.
- Cover navigation smoke tests, puzzle open/simulate/result, settings persistence, daily launch, reset flow, and accessibility semantics.
- Enable Compose accessibility checks for representative screens.
- Add screenshot regression tests for compact/high-contrast/reduced-motion/expanded states where deterministic rendering permits stable capture.
- Keep deterministic core unit tests as the fast first gate; instrumentation runs as a separate CI job.

Exit criteria: release-critical user journeys have automated device-level coverage and accessibility regressions fail CI.

### 6. Performance
- Add a Macrobenchmark/Baseline Profile module.
- Define critical user journeys: cold app startup, open Levels, open puzzle, draw/submit a representative thread, start playback, scrub timeline, open terrarium/almanac.
- Generate a Baseline Profile from those journeys.
- Measure startup and representative scroll/animation performance before and after the profile.
- Gate only on stable, explainable metrics; do not introduce flaky hard thresholds initially.

Exit criteria: release builds ship a generated Baseline Profile and benchmark reports are reproducible.

### 7. Product polish
- Wire existing `inspectCell` capability into a true tap-to-inspect weather/terrain information panel.
- Upgrade first-run onboarding from a hint-only experience to a short guided tutorial with skip/replay.
- Add graceful malformed-level/content-load failure UI instead of silent fallback/crash paths.
- Add explicit haptics control if haptic feedback remains part of the interaction model.
- Improve reset/progress management, empty/completed campaign states, and post-campaign goals.
- Balance/difficulty playtest after analytics-free manual telemetry notes or structured playtest sheets; do not add online analytics unless separately approved.

Exit criteria: first-run, failure, completion, and settings journeys feel intentional and recoverable.

### 8. Release governance and supply-chain safety
- Add Dependabot for Gradle and GitHub Actions updates.
- Add dependency review for pull requests where repository plan/support allows it.
- Protect `develop` and `main` with required CI checks; require PRs for `main` at minimum.
- Keep third-party actions pinned to immutable full commit SHAs.
- Add release checklist covering signing, versioning, content validation, instrumentation, accessibility, backup/restore, large-screen smoke, artifact checksum/provenance, and Play policy review.
- Enable minification/resource shrinking only after release instrumentation and smoke coverage can detect regressions.

Exit criteria: protected branches cannot merge a release-breaking change without failing an explicit gate.

### 9. Optional platform ports
Not part of Android production-readiness completion. After Android v1 release contract is stable, evaluate extracting `core/sim` and level schema into a portable Kotlin Multiplatform/shared specification or a separate reference implementation. Choose ports based on product need, not merely build availability.

## Figma/UI design scope
Figma work should focus on UX changes that need visual decisions:
- Accessibility/Comfort settings card including High Contrast and Haptics.
- Puzzle inspect panel and assistive non-drag interaction.
- Compact vs expanded puzzle/Almanac layouts.
- Guided tutorial states.

Do not regenerate existing felt artwork merely for planning. The existing visual identity remains authoritative unless a later design review requests art changes.

## Data contracts
- Simulation enums, metric names, deterministic hashes, and authored level IDs remain locale-independent and stable.
- Player-facing strings become resources or localized content keys.
- Save migrations are one-way, deterministic, and idempotent: loading the same old payload twice yields the same current `SaveData`.
- Production signing secrets never enter source control, workflow logs, downloadable artifacts, or Figma.

## Test strategy
1. Fast: Python content validator + JVM unit tests.
2. Build: lint + assembleDebug.
3. Instrumented: navigation/gameplay/accessibility tests on emulator/device.
4. Visual: deterministic screenshot tests.
5. Performance: Macrobenchmark/Baseline Profile jobs.
6. Release: signed-AAB verification, checksum, attestation, install/internal-track smoke.

## Non-goals for this increment
- Online account/backend services.
- Ads, analytics, social login, cloud gameplay synchronization, or monetization.
- iOS/Desktop/Web production ports.
- Rewriting the deterministic simulation architecture.
- Changing the application ID without explicit owner decision.

## Definition of production-ready Android v1
- Green deterministic content/unit/build CI.
- Installable CI APK artifacts.
- Signed, verified AAB release path with provenance.
- Final application ID explicitly chosen before Play publication.
- Core flows usable with TalkBack and automated accessibility checks green.
- Adaptive compact/expanded layouts validated.
- Explicit save migrations and backup policy.
- UI copy resource-backed and pseudolocale-safe.
- Instrumented critical-user-journey coverage.
- Baseline Profile generated and benchmarked.
- Protected release branches and documented release checklist.
