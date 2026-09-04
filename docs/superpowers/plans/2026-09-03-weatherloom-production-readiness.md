# Weatherloom Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Weatherloom production-ready on Android with reproducible artifacts, secure signing, accessibility/adaptive UI, explicit persistence/localization, device-level tests, performance profiling, and protected release gates.

**Architecture:** Implement dependency-ordered vertical slices. Preserve the deterministic offline simulation as the stable core; add release, accessibility, persistence, test, and performance capabilities around it. Platform ports remain out of scope until Android v1 contracts are stable.

**Tech Stack:** Kotlin 2.3.10, Jetpack Compose/Material 3, Android SDK 36, Gradle 8.14.1/AGP 8.13.2, Kotlin Serialization, SharedPreferences, GitHub Actions, Python 3, Compose UI Test, Accessibility Test Framework, Macrobenchmark/Baseline Profile.

**Spec:** `docs/superpowers/specs/2026-09-03-weatherloom-production-readiness-design.md`

## Global Constraints
- Preserve `develop@c4ef7dca3f3ac1a49b57b1392f38795ce6bbe3ce` as the repaired baseline.
- Implement on `feature/release-readiness` until reviewed.
- Keep `targetSdk=36` and `compileSdk=36` unless a later validated requirement changes them.
- Keep the app offline-first; do not reintroduce `INTERNET` permission without a separately approved feature.
- Do not change `applicationId = "com.rork.weatherloom"` without explicit owner approval before first Play publication.
- Never commit production signing secrets or keystore bytes.
- Third-party GitHub Actions references must use immutable full commit SHAs.
- Every task must leave Python content validation, JVM tests, lint, and debug build green unless the task explicitly adds a stronger gate.

---

### Task 1: Retained Debug APK Artifact

**Files:**
- Modify: `.github/workflows/android-ci.yml`

**Interfaces:**
- Consumes: existing `./gradlew testDebugUnitTest lintDebug assembleDebug` job.
- Produces: immutable Actions artifact named `weatherloom-debug-<short SHA>` containing `app-debug.apk` and `app-debug.apk.sha256`.

- [ ] **Step 1: Resolve and verify the current immutable SHA for `actions/upload-artifact`**

Use the owning GitHub repository tag/ref and pin the full commit SHA. Do not use `@v7` directly in committed workflow code.

- [ ] **Step 2: Generate the checksum after the successful build**

Add after the Gradle verification step:

```yaml
      - name: Generate APK checksum
        run: sha256sum app/build/outputs/apk/debug/app-debug.apk > app/build/outputs/apk/debug/app-debug.apk.sha256
```

- [ ] **Step 3: Upload APK and checksum**

```yaml
      - name: Upload debug APK
        uses: actions/upload-artifact@<FULL_SHA> # v7
        with:
          name: weatherloom-debug-${{ github.sha }}
          path: |
            android/app/build/outputs/apk/debug/app-debug.apk
            android/app/build/outputs/apk/debug/app-debug.apk.sha256
          if-no-files-found: error
          retention-days: 14
```

Because the job default working directory is `android`, verify whether action path resolution needs repository-root paths; if so use `${{ github.workspace }}/android/...`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: retain verified debug APK artifacts"
```

- [ ] **Step 5: Verify CI**

Expected: content validation succeeds, unit/lint/build succeeds, artifact upload succeeds, and the run exposes one downloadable artifact containing the APK and checksum.

---

### Task 2: Production Release Workflow Skeleton

**Files:**
- Create: `.github/workflows/android-release.yml`
- Create: `docs/release/ANDROID_SIGNING.md`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Consumes: four GitHub secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) once configured.
- Produces: signed `app-release.aab`, checksum, verified signer identity, and release metadata.

- [ ] **Step 1: Add Gradle release signing configuration that reads only environment variables**
- [ ] **Step 2: Fail release build if signing variables are absent instead of silently using debug signing**
- [ ] **Step 3: Add manual/tag-triggered release workflow using pinned actions**
- [ ] **Step 4: Decode keystore into runner temp storage and delete it in an `always()` cleanup step**
- [ ] **Step 5: Run `bundleRelease` and verify AAB signer/certificate with Android build tools**
- [ ] **Step 6: Generate SHA-256 checksum and upload signed AAB artifact**
- [ ] **Step 7: Document secret names, local signing verification, and Play App Signing/upload-key responsibilities**
- [ ] **Step 8: Commit and verify using a non-secret dry-run validation path; do not fabricate signing secrets**

---

### Task 3: Release Identity and Version Gate

**Files:**
- Create: `docs/release/RELEASE_IDENTITY.md`
- Create: `tools/check_release_identity.py`
- Modify: `.github/workflows/android-release.yml`
- Test: `tools/test_check_release_identity.py`

**Interfaces:**
- Consumes: `android/app/build.gradle.kts`, release tag.
- Produces: validation that `applicationId`, versionName, versionCode, and tag follow the release contract.

- [ ] **Step 1: Write tests for parsing current application ID/version fields and rejecting mismatched tags**
- [ ] **Step 2: Implement parser/validator without changing the application ID**
- [ ] **Step 3: Add workflow check before signing**
- [ ] **Step 4: Document that the application-ID choice must be explicitly approved before first Play publish**
- [ ] **Step 5: Commit**

---

### Task 4: Artifact Provenance and Release Checksums

**Files:**
- Modify: `.github/workflows/android-release.yml`

**Interfaces:**
- Consumes: signed AAB.
- Produces: GitHub artifact attestation and checksum.

- [ ] **Step 1: Resolve immutable SHA for GitHub attestation action**
- [ ] **Step 2: Add minimal workflow permissions: `contents: read`, `id-token: write`, `attestations: write`**
- [ ] **Step 3: Attest the signed AAB subject path**
- [ ] **Step 4: Verify workflow succeeds and attestation is visible for a real signed release run**
- [ ] **Step 5: Commit**

---

### Task 5: Save Migration Engine

**Files:**
- Create: `android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt`
- Modify: `android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt`
- Test: `android/app/src/test/java/com/rork/weatherloom/data/SaveMigrationTest.kt`

**Interfaces:**
- Consumes: raw stored JSON string or null.
- Produces: `SaveData` at `CURRENT_SAVE_SCHEMA` with deterministic fallback on corrupt data.

Define:

```kotlin
const val CURRENT_SAVE_SCHEMA = 2

object SaveMigration {
    fun decode(raw: String?, json: Json): SaveData
}
```

- [ ] **Step 1: Write tests for null, schema-1 payload, current payload, unknown fields, and corrupt JSON**
- [ ] **Step 2: Implement explicit schema dispatch and idempotent migration**
- [ ] **Step 3: Route `GameRepository.read()` through `SaveMigration.decode`**
- [ ] **Step 4: Run JVM tests and existing deterministic tests**
- [ ] **Step 5: Commit**

---

### Task 6: Explicit Backup Policy

**Files:**
- Create: `android/app/src/main/res/xml/backup_rules.xml`
- Create: `android/app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `docs/release/BACKUP_POLICY.md`

**Interfaces:**
- Consumes: SharedPreferences file `weatherloom.xml`.
- Produces: explicit cloud/device-transfer backup behavior.

- [ ] **Step 1: Add modern Android 12+ data extraction rules that include only Weatherloom SharedPreferences**
- [ ] **Step 2: Add legacy backup rules for API 30 and lower**
- [ ] **Step 3: Reference both rule files from the application manifest**
- [ ] **Step 4: Document restore expectations and test steps with `bmgr`/device transfer where supported**
- [ ] **Step 5: Lint/build and commit**

---

### Task 7: Resource-Backed UI Copy Foundation

**Files:**
- Create/expand: `android/app/src/main/res/values/strings.xml`
- Modify: `ui/navigation/AppNavigation.kt`
- Modify: `ui/screens/AlmanacScreen.kt`
- Modify: remaining screens/components containing player-facing literals

**Interfaces:**
- Consumes: Android resource IDs.
- Produces: complete default English resource set with no user-facing Compose literals in targeted screens.

- [ ] **Step 1: Inventory hard-coded player-facing strings**
- [ ] **Step 2: Move navigation/settings/common actions to `strings.xml` first**
- [ ] **Step 3: Convert Compose call sites to `stringResource` and plural resources where counts vary**
- [ ] **Step 4: Continue screen-by-screen until inventory is exhausted**
- [ ] **Step 5: Run lint with hard-coded text checks and build**
- [ ] **Step 6: Commit in screen-sized batches**

---

### Task 8: Pseudolocale and Layout Safety Gate

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: instrumentation tests under `android/app/src/androidTest/...`

**Interfaces:**
- Produces: automated smoke coverage under expanded/pseudolocalized text.

- [ ] **Step 1: Enable pseudo-locales for debug/test builds**
- [ ] **Step 2: Add test dependencies and Compose test runner**
- [ ] **Step 3: Add navigation/settings smoke test under pseudolocale**
- [ ] **Step 4: Add CI emulator job after fast JVM/build gates**
- [ ] **Step 5: Commit**

---

### Task 9: High Contrast Theme

**Files:**
- Modify: `ui/theme/Color.kt`
- Modify: `ui/theme/Theme.kt`
- Modify: `ui/navigation/AppNavigation.kt`
- Modify: `ui/screens/AlmanacScreen.kt`
- Test: theme/accessibility instrumentation tests

**Interfaces:**
- Consumes: existing `SaveData.highContrast` and `GameRepository.setHighContrast`.
- Produces: high-contrast theme selection and Comfort toggle.

- [ ] **Step 1: Define high-contrast semantic palette without changing thread identity motifs**
- [ ] **Step 2: Pass `highContrast` into `AppTheme` and centralize palette selection**
- [ ] **Step 3: Add Comfort switch wired to `repo::setHighContrast`**
- [ ] **Step 4: Add automated contrast/accessibility checks for representative screens**
- [ ] **Step 5: Commit**

---

### Task 10: Reduced Motion Completion

**Files:**
- Modify: `ui/puzzle/PuzzleScreen.kt`
- Modify: screens/components using animated visibility/transitions
- Test: instrumentation tests for reduced-motion state

**Interfaces:**
- Consumes: `SaveData.reducedMotion`.
- Produces: no decorative continuous/entrance motion when enabled; simulation timing/state remains unchanged.

- [ ] **Step 1: Inventory Compose animation APIs not gated by reduced motion**
- [ ] **Step 2: Gate or replace transitions with static visibility under reduced motion**
- [ ] **Step 3: Add test assertions for key reduced-motion UI states**
- [ ] **Step 4: Commit**

---

### Task 11: Accessible Puzzle Board Contract

**Files:**
- Modify: `ui/board/DioramaBoard.kt`
- Create: `ui/board/BoardAccessibility.kt`
- Modify: `ui/puzzle/PuzzleScreen.kt`
- Test: `androidTest` accessibility/semantics tests

**Interfaces:**
- Produces semantic representation of cells, armed weather thread, selected path, and available actions.

- [ ] **Step 1: Define semantic labels for terrain/weather state using localized resources**
- [ ] **Step 2: Add accessibility actions that do not require drag-only interaction**
- [ ] **Step 3: Preserve gesture drawing for pointer users**
- [ ] **Step 4: Add TalkBack-order and action tests**
- [ ] **Step 5: Manual TalkBack smoke on representative puzzle**
- [ ] **Step 6: Commit**

---

### Task 12: Minimum Touch Targets and Traversal Audit

**Files:**
- Modify: custom controls across `ui/`
- Test: accessibility checks

- [ ] **Step 1: Run automated accessibility checks on Terrarium, Levels, Daily, Almanac, puzzle draw, playback, and results**
- [ ] **Step 2: Fix custom controls below 48dp effective target**
- [ ] **Step 3: Fix missing labels/state descriptions/traversal order**
- [ ] **Step 4: Commit per screen group**

---

### Task 13: Adaptive Window Architecture

**Files:**
- Create: `ui/adaptive/WindowLayout.kt`
- Modify: `MainActivity.kt`
- Modify: `ui/navigation/AppNavigation.kt`
- Modify: `ui/puzzle/PuzzleRoute.kt` and relevant screens
- Test: compact/medium/expanded instrumentation tests

**Interfaces:**
- Produces a single app-level `WeatherloomWindowClass`/layout policy passed to screens.

- [ ] **Step 1: Add adaptive layout dependency/version through version catalog**
- [ ] **Step 2: Centralize window classification in one composable boundary**
- [ ] **Step 3: Keep compact bottom navigation; use wider navigation/pane composition on expanded widths**
- [ ] **Step 4: Adapt puzzle and Almanac first, then remaining screens**
- [ ] **Step 5: Test 360dp, 600dp+, and 840dp+ representative widths**
- [ ] **Step 6: Commit**

---

### Task 14: Reset and Progress Management

**Files:**
- Modify: `GameRepository.kt`
- Modify: `AlmanacScreen.kt` or add dedicated settings screen
- Test: JVM + instrumentation tests

- [ ] **Step 1: Split reset into explicit progress/settings operations if UX requires it**
- [ ] **Step 2: Add destructive confirmation dialog with clear consequences**
- [ ] **Step 3: Verify reset survives process restart**
- [ ] **Step 4: Commit**

---

### Task 15: Tap-to-Inspect Weather Panel

**Files:**
- Modify: `DioramaBoard.kt`
- Create: `ui/puzzle/CellInspector.kt`
- Modify: `PuzzleRoute.kt`, `PuzzleScreen.kt`, `PlaybackScreen.kt`
- Test: unit formatting + instrumentation interaction tests

**Interfaces:**
- Consumes: `level`, current `SimState`, selected cell index.
- Produces localized human-readable terrain/elevation/temp/moisture/cloud/water/snow/fog/wind summary.

- [ ] **Step 1: Write formatter tests for representative cell states**
- [ ] **Step 2: Wire existing `inspectCell` state to taps in draw/playback phases**
- [ ] **Step 3: Add compact bottom sheet and expanded supporting-pane layouts**
- [ ] **Step 4: Make inspector fully screen-reader accessible**
- [ ] **Step 5: Commit**

---

### Task 16: Guided Tutorial

**Files:**
- Create: `ui/tutorial/TutorialState.kt`
- Create: `ui/tutorial/TutorialOverlay.kt`
- Modify: `PuzzleRoute.kt` / first-level flow
- Test: JVM state-machine + instrumentation tests

- [ ] **Step 1: Define finite tutorial steps with skip/replay semantics**
- [ ] **Step 2: Unit-test progression and persistence**
- [ ] **Step 3: Implement overlay that respects reduced motion and TalkBack**
- [ ] **Step 4: Add replay entry from Almanac/Help**
- [ ] **Step 5: Commit**

---

### Task 17: Malformed Content Failure UX

**Files:**
- Modify: `LevelLibrary.kt`
- Create: `ui/screens/ContentErrorScreen.kt`
- Modify navigation initialization
- Test: JVM parser failures + instrumentation error screen

- [ ] **Step 1: Represent load failure explicitly instead of silently returning empty/partial content**
- [ ] **Step 2: Add safe local recovery screen with retry/restart instructions**
- [ ] **Step 3: Ensure production build never exposes stack traces or file paths**
- [ ] **Step 4: Commit**

---

### Task 18: Screenshot Regression Suite

**Files:**
- Add screenshot test configuration/module per current Android-supported Compose screenshot tooling
- Add golden/reference assets generated by deterministic test fixtures

- [ ] **Step 1: Add one golden for each core screen in default theme**
- [ ] **Step 2: Add high-contrast and expanded-layout goldens for changed screens**
- [ ] **Step 3: Keep animation phase frozen and use deterministic data fixtures**
- [ ] **Step 4: Add CI job and artifact diff output on failure**
- [ ] **Step 5: Commit**

---

### Task 19: Macrobenchmark and Baseline Profile

**Files:**
- Create: `android/benchmark/` module
- Modify: `android/settings.gradle.kts`, version catalog, app build config
- Add benchmark and baseline-profile tests

**Interfaces:**
- Produces baseline profile and benchmark JSON/trace artifacts.

- [ ] **Step 1: Add Macrobenchmark/Baseline Profile module**
- [ ] **Step 2: Implement cold startup benchmark**
- [ ] **Step 3: Implement critical user journeys: Levels → puzzle → playback and Almanac scroll**
- [ ] **Step 4: Generate Baseline Profile from journeys**
- [ ] **Step 5: Compare startup/runtime with and without profile**
- [ ] **Step 6: Add non-blocking CI benchmark reporting first; only add stable thresholds later**
- [ ] **Step 7: Commit**

---

### Task 20: Dependency and Branch Governance

**Files:**
- Create: `.github/dependabot.yml`
- Create/modify: dependency-review workflow if repository support permits
- Repository settings: branch protection/rulesets for `develop` and `main`

- [ ] **Step 1: Configure Dependabot for Gradle and GitHub Actions**
- [ ] **Step 2: Add dependency review check for pull requests if supported**
- [ ] **Step 3: Require unique Weatherloom CI checks on `main`; require PR before merge**
- [ ] **Step 4: Protect `develop` with required CI once feature flow is stable**
- [ ] **Step 5: Block force pushes/deletion on protected release branches**
- [ ] **Step 6: Commit repository files and document non-file repository settings**

---

### Task 21: Production Optimization Gate

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: ProGuard/R8 rules only as required by release build evidence

- [ ] **Step 1: Enable code minification and resource shrinking in release candidate branch**
- [ ] **Step 2: Run full instrumentation/accessibility/screenshot suite against release-like build**
- [ ] **Step 3: Add only evidence-driven keep rules**
- [ ] **Step 4: Compare artifact size and critical flows**
- [ ] **Step 5: Commit when green**

---

### Task 22: Release Checklist and Candidate Gate

**Files:**
- Create: `docs/release/RELEASE_CHECKLIST.md`
- Modify: README release section

- [ ] **Step 1: Record application-ID decision**
- [ ] **Step 2: Verify signed AAB signer and Play upload-key relationship**
- [ ] **Step 3: Run content/JVM/lint/build/instrumented/accessibility/screenshot gates**
- [ ] **Step 4: Run backup/restore and pseudolocale smoke**
- [ ] **Step 5: Run compact/large-screen smoke**
- [ ] **Step 6: Generate checksum and attestation**
- [ ] **Step 7: Verify Play target/API/policy requirements current on release date**
- [ ] **Step 8: Publish only after all boxes are checked**

---

### Task 23: Post-v1 Port Feasibility (Separate Future Plan)

**Files:** none in this increment.

- [ ] **Step 1: After Android v1 contracts stabilize, inventory pure Kotlin simulation dependencies**
- [ ] **Step 2: Decide whether Kotlin Multiplatform, a portable spec/reference implementation, or independent ports minimize risk**
- [ ] **Step 3: Create a separate design/spec/plan before touching iOS/Desktop/Web targets**

## Plan Self-Review
- Spec coverage: every workstream in the design spec maps to at least one task.
- No application-ID change is scheduled without explicit owner approval.
- Online services/analytics/monetization remain out of scope.
- Save migration, backup, localization, accessibility, adaptive layouts, instrumentation, screenshots, performance, release signing, provenance, and governance all have explicit gates.
- Port work is deliberately deferred to a separate future plan.
