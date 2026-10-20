# Changelog

What changed in each AdCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/AdCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.0 (2026-10-20)

The first build of AdCtrl: the app's shell, ready for the features that follow. It doesn't connect to an AdGuard Home server yet; adding a server comes in the next version.

### New

- **Four tabs** in a floating glass bar at the bottom: Overview, Queries, Filters and Clients. Each one says what it will show once a server is connected. On tablets, unfolded foldables and in landscape the tabs move to a side rail
- **Account page** opened by tapping the profile picture at the top right of any tab (there is no separate "You" tab), with links to Settings, GitHub Sponsors, bug reports, the source code and the licence
- **Settings** for the theme (follow system, dark, light or pure black) and dynamic colour from your wallpaper
- **App icon** in the Ctrl style: a shield with a check mark on a keycap, in AdCtrl's signal green, with a themed version for Android's themed icons

### Security

- **Screenshot protection on by default**: screenshots, screen recordings and the recent apps view show a blank screen, because the query log shows what everyone on your network visits. You can turn it off under **Settings › Privacy**
- Plain HTTP is switched off for the whole app for now. Later it will only be allowed for addresses on your own network, per server, after a warning
- Nothing the app stores is included in Android backups or moved to a new phone
- The only permission is network access. No downloadable emoji font is fetched at start-up

### Privacy

- **Google-free and telemetry-free from the start**: no Google Play services, Firebase, ML Kit, analytics or crash reporting. Every build now checks the app's libraries and fails if anything from Google Play services, Firebase, ML Kit, Play Core or Tink appears
- Added **PRIVACY.md** explaining what AdCtrl sends where (only to your own servers)
