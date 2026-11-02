# Clock

A light, Material 3 Expressive alarm clock and timer for Android.

The one thing that pushed me to build it: **an alarm that repeats every 2 weeks** (lectures on alternate weeks). Most clock apps can only repeat weekly or daily, so I made one that can.

![Every 2 weeks alarms](docs/screenshots/01-every-2-weeks.png) ![Repeat settings](docs/screenshots/02-customize-biweekly.png) ![Two-week calendar](docs/screenshots/03-two-week-calendar.png)

*Screenshots are design mockups rendered with the app's colour scheme and layout, not captures of a real device.*

## Features

- **Alarms**
  - Defaults to the nearest full hour: today if still ahead, otherwise tomorrow.
  - One-time by default. Pick a date, or add weekdays with one tap afterwards.
  - Repeat **every 2 weeks** on chosen days.
- **Timers**
  - Real system alarms (`AlarmManager`), so the timer runs in the background and fires on time.
  - Full-screen "Time's up" screen with Snooze and Stop, and +5 min from the notification.
  - Repeated runs of the same timer within an hour show total minutes and run count, e.g. `12 min · 3×`.
- **Design**
  - Material 3 Expressive, fixed palette, predictive back gesture, animated tab icons.

## Privacy

No internet permission, no analytics, no ads, no data collection. Everything stays on the device. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) (PL/EN).

## Build

Requirements: Android Studio or the Android SDK, JDK 17+, Android 8.0 (API 26) or newer on the device.

```bash
./gradlew :app:assembleRelease
```

The release build is minified with R8. It is currently signed with the Android debug key, so for distribution you need your own signing configuration.

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Legal

- License: [MIT](LICENSE)
- Terms of use (PL/EN): [REGULAMIN.md](REGULAMIN.md)
- Privacy policy (PL/EN): [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

## Third-party

Built with Jetpack Compose and Material 3 (Apache License 2.0), Kotlin Coroutines and kotlinx.serialization (Apache License 2.0).

## Contact

Questions and bug reports: [GitHub Issues](https://github.com/Mikhas-Rahulia/pixel-clock/issues).
