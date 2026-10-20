# AdCtrl

Android app for a self-hosted AdGuard Home DNS server. Part of the VKNXWorks Ctrl series: same look, structure and libraries as ProxCtrl, NaviCtrl and the other Ctrl apps (Kotlin, Compose, Material 3, manual `AppContainer` DI, OkHttp + kotlinx.serialization, haze glass tab bar). Google-free and telemetry-free (no Aptabase either): no Google Play services, Firebase, ML Kit, Play Core, Tink or WebView. It must build for F-Droid, for arm64-v8a only.

## Working on this repo

- Push fixes and updates straight to `main`, with no branches or pull requests.
- Every push needs a detailed `CHANGELOG.md` entry.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Check AdGuard Home endpoints against `openapi/openapi.yaml` and `openapi/CHANGELOG.md` in the AdGuard Home repo for the server's version; never guess field names. Parse API responses leniently (missing or new fields must never crash).
- The only network destinations are the configured AdGuard Home servers. Never log query log contents, credentials or URLs containing credentials.
- Test rule changes with a harmless test domain first. Never disable protection or block a client without the user confirming on the device.
- CI runs a Google-free dependency audit on every build; keep it passing.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "AdCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- CI signs with the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets and the `KEY_ALIAS` variable (`ad`), and checks the APK is signed with that key. Never commit a keystore, its base64 or its passwords.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
