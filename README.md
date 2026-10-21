# AdCtrl

**Control your AdGuard Home DNS server from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/AdCtrl?label=download)](https://github.com/VoidedKN0X/AdCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

AdCtrl is an Android app for [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome): a fast, touch-first alternative to the web UI for what you do from your phone. See what's blocked, unblock a site that broke, pause protection and keep an eye on the devices on your network.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

> **Early development.** AdCtrl is being built one part at a time. All four tabs, the Quick Settings tile and the app lock are done; a widget, app shortcuts and notifications follow. See [CHANGELOG.md](CHANGELOG.md) for what's in each version.

## Features

- **Overview**: a big protection switch with pause for 30 seconds up to 8 hours (with a countdown), statistics for the last day to 90 days, a queries vs. blocked graph, top domains, clients and upstreams, and an attention list for paused protection, failing filter lists and updates
- **Queries**: the live query log with search and filters, the matched rule and list for every answer, and **one-tap block or unblock** of any domain, for one subdomain, the whole domain or one client
- **Filters**: blocklists and allowlists (with 45 well-known lists to pick from), a custom rules editor with highlighting, search, mistake hints and a look at the changes before saving, blocked services with a pause schedule, DNS rewrites, and safe browsing, parental control and safe search
- **Clients**: every device with its queries, recent activity and its own settings (filtering, blocked services, upstreams, query log and statistics), adding found devices as clients, and blocking a device entirely
- **Quick Settings tile** to pause protection or turn it back on with one tap (long-press opens AdCtrl)
- **Several servers**, such as a primary and a secondary, with "apply to all servers" for rule changes and pausing
- **App lock** with its own PIN and optional fingerprint unlock; your server passwords stay encrypted while the app is locked, and **Confirm changes** can ask for the PIN or fingerprint before anything changes on a server
- **Safe by design**: a read-only mode, confirmation before changes, certificate pinning, screenshot protection, and passwords encrypted with the Android Keystore

## Planned

Not in the app yet: a home screen widget, app shortcuts and notifications.

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- AdGuard Home v0.107.37 or newer
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/AdCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds AdCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/AdCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/AdCtrl` in Obtainium by hand.

## Getting started

1. Open AdCtrl and enter the address you open AdGuard Home's web interface on, like `https://adguard.example.com` or `192.168.1.2:3000`. Without `http://`, AdCtrl uses HTTPS. AdCtrl only ever connects to this exact address; there's no second or fallback address.
2. Enter the username and password you sign in to AdGuard Home with.
3. If AdGuard Home uses a self-signed certificate, compare the fingerprint AdCtrl shows with your certificate's (on the server: `openssl x509 -in cert.pem -noout -fingerprint -sha256`), then tap **Trust**.
4. Check the version and protection state AdCtrl shows, choose whether to use **read-only mode**, and tap **Continue**.

Add a second server, such as a secondary AdGuard Home, under **Account › Servers**.

## Recommended AdGuard Home setup

- **Use HTTPS.** AdGuard Home signs you in with a username and password on every request (HTTP Basic auth), so reach it over HTTPS: turn on encryption in AdGuard Home under **Settings › Encryption settings**, or put it behind a reverse proxy such as Nginx Proxy Manager. AdCtrl only allows plain HTTP for addresses on your own network (private IPs and `.lan`, `.local`, `.home.arpa`, `.internal` or single-word names that point to a private IP), and only when you switch it on for that server.
- **Use a dedicated, strong password.** Give the AdGuard Home admin account a long, random password that you don't use anywhere else.
- **Self-signed certificate?** AdCtrl shows its SHA-256 fingerprint the first time it connects. Compare it with your certificate and tap **Trust**; AdCtrl then accepts only that exact certificate for that server.

## App lock

Turn it on under **Account › Settings › Privacy and security › App lock** (it's off until you do).

- **Its own PIN**, at least 6 digits and separate from your phone's; very obvious ones like 111111 or 123456 are refused. The PIN is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware, so copying AdCtrl's files off the phone isn't enough to guess it.
- **Unlock with fingerprint** (optional): only strong biometrics such as your fingerprint, checked by the phone's secure hardware. Adding a new fingerprint turns it off until you turn it on again with your PIN. The PIN is still needed after the phone restarts and at least every 72 hours.
- **Wrong PINs make you wait**: 30 seconds after 5 tries, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a link in your browser from AdCtrl doesn't count as leaving, unless you stay away longer than 5 minutes.
- **Your passwords are sealed while locked.** They're encrypted with a key that only exists in memory while AdCtrl is unlocked and is wiped the moment it locks, so a locked AdCtrl, or a copy of its files, can't sign in to your servers. Nothing is sent to your servers until you unlock.
- **Confirm changes** (optional): asks for your fingerprint or app PIN before anything that changes a server, such as pausing protection, blocking a domain or editing rules. One confirmation covers the next minute.
- **Quick Settings tile:** while AdCtrl is locked, the tile says so and a tap opens AdCtrl, so nobody can pause protection without your PIN. With **Confirm changes** on, a tap always opens AdCtrl.
- **Change app PIN** asks for your current PIN first, and so does turning the lock off.
- **Forgot your PIN?** The lock screen can remove your servers and their passwords so you can add them again. Nothing changes on your AdGuard Home servers.
- **Hide in screenshots and recent apps** (on by default) blanks screenshots, screen recordings and the recent apps view. With the app lock on, the recent apps view is always blank.

## Privacy

**Telemetry-free.** AdCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the AdGuard Home servers you add, and refuses to connect anywhere else. Your query log shows what everyone on your network visits, so it never leaves your phone except to and from your own server. See [PRIVACY.md](PRIVACY.md).

- **Links:** the Support, Report a bug, Source code and Licence rows in About open in your browser; the app itself doesn't contact GitHub.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/AdCtrl.git
   cd AdCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

AdCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/AdCtrl/issues) helps too.

In the app, the **About** section at the bottom of the Account page has **Support AdCtrl** and **Report a bug** rows that open these pages.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| **AdCtrl** (this app) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

AdCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.

AdCtrl is not affiliated with or endorsed by AdGuard. AdGuard is a trademark of its owner.
