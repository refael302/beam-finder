# Beam Finder

Android app that uses the camera to find laser beams and signals proximity with audio/vibration feedback.

- Package: `io.github.refael302.beamfinder`
- Stack: Kotlin 2.x, Jetpack Compose (Material3), CameraX, Preferences DataStore, Navigation Compose, OkHttp, kotlinx.serialization
- minSdk 26, target/compile SDK 36, JDK 17, Gradle version catalog (`gradle/libs.versions.toml`)

## Modules

| Module | Description |
|--------|-------------|
| `:detection` | Pure JVM Kotlin (no Android deps): frame model + band extraction, rotation-aware `CoordinateMapper`, `ColorLaserScorer`, ambient calibration, `IntensityPipeline`, `SensitivityModel`, `BeepStateMachine` |
| `:app` | Android app; product flavors `github` and `play` (dimension `distribution`) |

## Status

- Phase 1: project scaffold, modules, DI, settings SSOT
- Phase 2: Menu / Settings / About / Detect (placeholder) screens, update-check plumbing
- Phase 3: CameraX preview + YUV analysis, zoom, draggable/lockable guide line, AE/AWB/focus lock, pocket mode
- Phase 4: real detection (colour scorer, calibration, peak-hold/EMA/hysteresis pipeline), level meter
- Phase 5: AudioTrack beeps (alarm/media stream, styles, continuous tone) + synced haptics

## Build

Requires JDK 17 and the Android SDK. If `gradlew` is missing, generate it with `gradle wrapper`.

```
./gradlew :detection:test
./gradlew :app:assembleGithubDebug
```

GitHub-flavor APKs are signed with a stable sideload keystore (`app/signing/`) so About →
Check for update can install newer Releases over older ones. Play flavor is separate.

## License

MIT, see [LICENSE](LICENSE).
