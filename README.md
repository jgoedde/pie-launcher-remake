# Pie Launcher

Android launcher where apps open through a radial pie menu. Successor to [Fly-Pie-Launcher](https://github.com/jgoedde/Fly-Pie-Launcher).

Status: spec complete, implementation next. Start with the [v1 PRD](docs/prd.md).

## Build

Needs JDK 21 and an Android SDK with platform `android-37.2` (`ANDROID_HOME` set). Android Studio is not required.

```sh
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug assembleRelease
```

Local and PR release builds are signed with the committed debug keystore (`app/debug.keystore`). Real releases are built only by `.github/workflows/release.yml` on a `vX.Y.Z` tag.
