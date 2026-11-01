# Unrot

A free, open-source Android app that counts the reels and shorts you scroll, in real time.
It shows a brain that visibly degrades as the count climbs, blocks reels when you reach your
daily limit, asks for a short focus challenge to earn more, and lets you compete with friends
for the lowest count.

> **Status:** early development. The app currently opens to a placeholder screen; it does not
> count anything yet. Progress is tracked in [`docs/PROGRESS.md`](docs/PROGRESS.md).

## Tracked apps (v1)

| App | Surface |
|---|---|
| Instagram | Reels |
| YouTube | Shorts |
| TikTok | For You / Following |
| Facebook | Reels |
| Snapchat | Spotlight |

## Install a test build

Every push builds a debug APK in GitHub Actions:

1. Open the repo's **Actions** tab and pick the latest green **CI** run.
2. Download the **app-debug** artifact (a zip), unzip it and install `app-debug.apk` on an
   Android 10+ phone.

## Build locally

Requires JDK 17+ and the Android SDK (API 37).

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew :detection:test      # pure-JVM detection tests
./gradlew spotlessApply        # format Kotlin code
```

## Project docs

- [`docs/HOW_TO_USE.md`](docs/HOW_TO_USE.md): how the app is built, step by step
- [`docs/SPEC.md`](docs/SPEC.md): full specification
- [`docs/ADR.md`](docs/ADR.md): architecture decisions
- [`rules/schema.md`](rules/schema.md): detection rules format

## Known limitations

To be documented as features land (SPEC §7.10).

## Privacy

See [`PRIVACY.md`](PRIVACY.md).

## License

[GPL-3.0-or-later](LICENSE). Third-party skill files in `.claude/skills/` keep their own
licenses; see [`SOURCES.md`](SOURCES.md).
