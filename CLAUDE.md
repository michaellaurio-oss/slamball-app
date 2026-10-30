# CLAUDE.md: Slam Log

Michael's personal Android app for slam ball workouts with a COOSPO H808S heart rate chest strap.
He is the only user. Explain build and install steps plainly; he is not a day-to-day Android developer.

## What matters most

- **Headline metric: time from the last slam of a set until heart rate falls to the recovery target**
  (default 130 bpm, `UserSettings.recoveryTargetHr`). Keep it prominent; don't bury it.
- **Baseline test** (default 10 sets × 20 reps, 20 lb ball, rest until HR ≤ target) is the fair
  yardstick for "is my recovery improving?" while training volume grows. Only finished baseline tests
  matching the current baseline definition are compared (`Progress.baselines`). Training workouts are
  compared only at the same reps per set (`Progress.byReps`).
- Effort rating (1–10) is collected once, after the workout, on the summary screen. Not per set.
- No speech during a set (it interferes with mic slam detection). The only in-set cue is a double
  beep at 3 reps left, plus the optional per-rep click. `UserSettings.callEvery` is unused.
- Age comes from the stored birthday (`UserSettings.birthDate`), never a stored age.

## Architecture

Single-activity Jetpack Compose app, no DI, no database library.

- `MainActivity.kt`: screen enum + navigation, permission requests (Bluetooth scan/connect, mic).
- `workout/WorkoutViewModel.kt`: the workout engine. A 1-second ticker drives phases
  `GET_READY → WORK → REST … → RECOVERY → DONE`. Records HR samples, zones, calories, per-set
  results, 1-minute HR drop, and time-to-target. `startBaseline()` runs the fixed baseline config.
- `workout/Progress.kt`: baseline filtering, the improvement verdict (≥3 s and ≥5% to count as a
  change), by-reps grouping, CSV export.
- `workout/Physiology.kt`: zones by % of max HR; Keytel calorie formula.
- `ble/HeartRateManager.kt`: standard BLE Heart Rate service (0x180D / 0x2A37); saves the strap
  address and auto-reconnects.
- `audio/SlamDetector.kt`: AudioRecord at 16 kHz, 20 ms frames, adaptive noise floor, 700 ms
  refractory period; requires louder hits while `Speaker.isBusy()`.
- `audio/Speaker.kt`: TextToSpeech + ToneGenerator.
- `data/`: `Models.kt` (JSON (de)serialization, so new fields must default safely for old saved
  workouts), `SessionStore.kt` (sessions.json in app storage), `SettingsStore.kt` (SharedPreferences).
- `ui/`: one file per screen, plus `Components.kt`, `SlamEffects.kt` (burst/pop/shake), `Effort.kt`.

When adding a field to `Session`, `SetResult` or `UserSettings`: give it a default, write it in
`toJson`/`SettingsStore`, and read it with a fallback, so existing data on the phone keeps loading.

## Build and install

Portable toolchain on Michael's Windows PC (no Android Studio):
- JDK 17: `D:\Users\micha\Android\jdk-17`
- Android SDK: `D:\Users\micha\Android\sdk` (platform 35, build-tools 35, platform-tools/adb)
- `local.properties` (not committed): `sdk.dir=D\:\\Users\\micha\\Android\\sdk`
- Build: `$env:JAVA_HOME='D:\Users\micha\Android\jdk-17'; .\gradlew.bat assembleDebug`
- Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk` (keeps data). Or double-click
  `install.bat`, which does both.
- Phone: Samsung, adb id `R5CY20N34VZ`. If adb shows `unauthorized`, he needs to tap Allow on the phone.

Versions match his other app (Range Log) so cached dependencies are reused: AGP 8.7.3, Kotlin 2.0.21,
Compose BOM 2024.12.01, core-ktx 1.15.0, Gradle 8.11.1.

Local working copy on his PC: `D:\Users\micha\Claude\slamball-app`.

## Conventions

- Keep the README's user-facing feature list in sync when features change.
- Don't uninstall the app to fix install problems: that deletes his workout history. Use `install -r`.
