<p align="center">
  <img src="android/app/src/main/res/drawable-nodpi/terrarium_cloche.png" width="360" alt="Weatherloom terrarium" />
</p>

<h1 align="center">Weatherloom</h1>

<p align="center">
  <strong>Weave the weather. Shape the land.</strong>
</p>

<p align="center">
  A cozy, deterministic weather puzzle game for Android, rendered as a hand-crafted felt diorama.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" />
  <img alt="Minimum Android version" src="https://img.shields.io/badge/Min%20SDK-24-informational" />
</p>

## About

Weatherloom turns weather into a small, readable chain-reaction puzzle. Draw warm fronts, cold fronts, wind bands, and moisture ribbons across an 8 × 12 landscape, then watch the simulation unfold beat by beat.

Fill reservoirs, bloom flowers, spin windmills, clear fog, create mountain snow, and guide runoff—without freezing crops or flooding villages. The simulation is deterministic: the same board and the same threads always produce the same result, making every solution understandable and repeatable.

## Gameplay

1. Inspect the miniature biome and its objectives.
2. Choose from a limited supply of weather threads.
3. Draw each thread across the landscape.
4. Run the 40–60 beat atmospheric simulation.
5. Review the causal events, adjust the weave, and try again.
6. Solve efficiently to earn a **Seedling**, **Bloom**, or **Flourish** rating.

### Weather threads

| Thread | Rule |
| --- | --- |
| **Warm Front** | Warms crossed cells and gradually carries humidity. |
| **Cold Front** | Chills the air; cold, moist air can produce clouds, rain, or snow. |
| **Wind Band** | Pushes clouds and fog one tile per beat and turns windmills. |
| **Moisture Ribbon** | Adds humidity, but cannot create rain by itself. |

Elevation, terrain, temperature, moisture, wind, fog, clouds, snow, runoff, and water storage all interact through deliberately simplified rules. The goal is a visual causal toy—not a realistic weather forecast.

## Features

- **28 handcrafted puzzles** across **9 chapters**, from *First Rain* to *The Weaver’s Trial*
- A deterministic, fixed-step simulation with replayable outcomes
- Data-driven terrain, objectives, weather budgets, hints, and canonical solutions
- Offline daily forecasts generated consistently from the calendar date
- Persistent local progress with best stroke and cell counts
- Three efficiency ratings: Seedling, Bloom, and Flourish
- Nine collectible botanical specimens displayed in a growing terrarium
- Felt-and-wool artwork, atmospheric animation, ambient music, and reactive sound
- Reduced-motion and independent music/sound settings
- No account required; the complete core game and save system work offline

## Tech stack

- **Kotlin 2.3.10**
- **Jetpack Compose** and **Material 3**
- **Navigation Compose**
- **Kotlin Serialization** for level data and saves
- **SharedPreferences + StateFlow** for local-first persistence
- **Android Gradle Plugin 8.13.2** with **Gradle 8.14.1**
- **Python 3** tooling for level generation, solving, and validation

## Project structure

```text
.
├── android/
│   ├── app/src/main/assets/levels.json       # Chapters, levels, goals, and solutions
│   ├── app/src/main/java/com/rork/weatherloom/
│   │   ├── core/level/                       # Level loading and daily forecasts
│   │   ├── core/sim/                         # Headless deterministic simulation
│   │   ├── data/                             # Local progress and settings
│   │   ├── ui/                               # Compose screens, board, and navigation
│   │   └── audio/                            # Music and sound control
│   └── app/src/main/res/                     # Felt artwork, fonts, icons, and audio
└── tools/
    ├── new_levels.py                         # Content-generation helpers
    ├── solve_levels.py                       # Headless solution tooling
    └── validate_levels.py                    # Canonical level validation
```

## Getting started

### Requirements

- A recent version of Android Studio
- Android SDK 36
- JDK 17 or the JDK bundled with Android Studio
- Python 3 (optional, for content validation)

### Clone and build

```bash
git clone https://github.com/adaPlu/weatherloom-111.git
cd weatherloom-111/android
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
git clone https://github.com/adaPlu/weatherloom-111.git
Set-Location weatherloom-111\android
.\gradlew.bat assembleDebug
```

The debug APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

To install it on a connected device or running emulator:

```bash
./gradlew installDebug
```

> [!NOTE]
> The `release` build is intentionally **not** debug-signed. Configure a production upload/release keystore before publishing; never ship a debug-signed release artifact.

## Validate the puzzle library

Run the validator from the repository root:

```bash
python tools/validate_levels.py
```

The validator mirrors the Kotlin simulation headlessly, checks every authored level's canonical solution, and verifies all deterministic daily-forecast variants. When simulation rules change, keep `SimulationEngine.kt` and `tools/validate_levels.py` in sync.

## Design principles

- **Readable causality:** players should be able to explain why every outcome occurred.
- **Deterministic rules:** experimentation should teach the system, not fight randomness.
- **Data-driven content:** new puzzles should rarely require new gameplay code.
- **Cozy presentation:** the felt diorama, soundscape, and terrarium make iteration pleasant.
- **Offline-first play:** puzzles, daily challenges, settings, and progress do not depend on a server.

## Contributing

Issues and focused pull requests are welcome. For gameplay changes:

1. Keep the simulation deterministic.
2. Update the Kotlin engine and Python validator together.
3. Run `python tools/validate_levels.py` before opening a pull request.
4. Confirm that every canonical level solution still passes.

## License

No open-source license has been published for this repository yet. Unless a license is added, all rights remain with the repository owner.

---

<p align="center">
  Built by <a href="https://github.com/adaPlu">Adam Pluguez</a>.
</p>
