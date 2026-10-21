# AdCtrl

**Control your AdGuard Home DNS server from your phone.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/AdCtrl?label=download)](https://github.com/VoidedKN0X/AdCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

AdCtrl is an Android app for [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome): a fast, touch-first alternative to the web UI for what you do from your phone. See what's blocked, unblock a site that broke, pause protection and keep an eye on the devices on your network, from the app, a Quick Settings tile, a home screen widget or app shortcuts, with notifications when protection stays off or a server goes down.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

## Features

- **Overview**: a big protection switch with pause for 30 seconds up to 8 hours (with a countdown), statistics for the last day to 90 days, a queries vs. blocked graph, top domains, clients and upstreams, and an attention list for paused protection, failing filter lists and updates
- **Queries**: the live query log with search and filters, the matched rule and list for every answer, and **one-tap block or unblock** of any domain, for one subdomain, the whole domain or one client
- **Filters**: blocklists and allowlists (with 45 well-known lists to pick from), a custom rules editor with highlighting, search, mistake hints and a look at the changes before saving, blocked services with a pause schedule, DNS rewrites, and safe browsing, parental control and safe search
- **Clients**: every device with its queries, recent activity and its own settings (filtering, blocked services, upstreams, query log and statistics), adding found devices as clients, and blocking a device entirely
- **Quick Settings tile** to pause protection or turn it back on with one tap (long-press opens AdCtrl)
- **Home screen widget** with protection on, paused (with a live countdown) or off, and the last 24 hours' queries and blocked share
- **App shortcuts** (long-press the AdCtrl icon): Pause protection, Query log, Search the query log and Blocked queries
- **Notifications** from AdCtrl's own background checks: protection turned off, paused for longer than you expect, a server that can't be reached, filter lists that stop updating and AdGuard Home updates
- **Several servers**, such as a primary and a secondary, with "apply to all servers" for rule changes and pausing
- **App lock** with its own PIN and optional fingerprint unlock; your server passwords stay encrypted while the app is locked, and **Confirm changes** can ask for the PIN or fingerprint before anything changes on a server
- **Safe by design**: a read-only mode, confirmation before changes, certificate pinning, optional **Hide in screenshots** and **Hide in recent apps**, and passwords encrypted with the Android Keystore

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

Your servers, **Settings** and **About** are on the **You** page: tap your profile picture at the top right of any tab. Add a second server, such as a secondary AdGuard Home, there under **Servers**.

## Recommended AdGuard Home setup

- **Use HTTPS.** AdGuard Home signs you in with a username and password on every request (HTTP Basic auth), so reach it over HTTPS: turn on encryption in AdGuard Home under **Settings › Encryption settings**, or put it behind a reverse proxy such as Nginx Proxy Manager. AdCtrl only allows plain HTTP for addresses on your own network (private IPs and `.lan`, `.local`, `.home.arpa`, `.internal` or single-word names that point to a private IP), and only when you switch it on for that server.
- **Use a dedicated, strong password.** Give the AdGuard Home admin account a long, random password that you don't use anywhere else.
- **Self-signed certificate?** AdCtrl shows its SHA-256 fingerprint the first time it connects. Compare it with your certificate and tap **Trust**; AdCtrl then accepts only that exact certificate for that server.

## App lock

Turn it on: tap your profile picture › **Settings › Privacy and security › App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your server passwords are encrypted with the PIN while AdCtrl is locked.** The key only exists in memory while the app is unlocked and is wiped the moment it locks, and nothing is sent to your servers until you unlock. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a link in your browser from the You page doesn't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your passwords. The lock screen offers to remove your servers and their passwords and the statistics AdCtrl keeps on the phone, so you can add them again; nothing changes on your AdGuard Home servers.
- **Confirm changes** (optional) asks for your fingerprint or app PIN before anything that changes a server, such as pausing protection, blocking a domain or editing rules. One confirmation covers the next minute.
- **The Quick Settings tile** says AdCtrl is locked and opens it instead of pausing protection while AdCtrl is locked, so nobody can pause it without your PIN. With **Confirm changes** on, a tap on the tile always opens AdCtrl.
- **The widget** only says "AdCtrl is locked" while your passwords are sealed, and **app shortcuts** open the lock screen first; **Pause protection** then still needs a duration picked on the phone (and **Confirm changes**, when it's on).
- **Background checks** for notifications have to sign in while AdCtrl is locked. While they're on, your server passwords are only encrypted with the Android Keystore, as without the app lock, and the widget keeps showing your server; turn them off to keep the passwords sealed with your PIN.

**Hide in screenshots** and **Hide in recent apps** (the last two switches in the same section, also without the app lock, both off by default): the first keeps AdCtrl out of screenshots and screen recordings (and blanks its recent-apps preview), the second shows a blank card for AdCtrl in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card. Server setup, the lock screen and the PIN screens are always kept out of screenshots.

## Widget, shortcuts and notifications

- **Widget:** add **AdGuard protection** from your home screen's widget list. It shows the server you have selected in AdCtrl: protection on, paused with a live countdown, or off, and the DNS queries and blocked share of the last 24 hours. Tap it to open AdCtrl. It refreshes when you use the app, with every background check and at least every 30 minutes, and never changes anything itself.
- **App shortcuts:** long-press the AdCtrl icon for **Pause protection** (opens the pause menu, nothing changes until you pick how long), **Query log**, **Search the query log** and **Blocked queries**. With the app lock on, AdCtrl asks for your PIN first.
- **Notifications:** AdGuard Home can't send notifications itself, so AdCtrl checks your servers in the background: tap your profile picture › **Settings › Notifications** and turn on **Check in the background** (off until you do; every 15, 30, 60 or 120 minutes). Each kind can be switched on or off:
  - **Protection turned off** (on): protection or filtering switched off until someone turns it back on
  - **Paused longer than expected** (on): protection paused or off for longer than the time you choose (15 minutes to 8 hours, 1 hour by default)
  - **Server unreachable** (on): two checks in a row couldn't reach AdGuard Home, or its sign-in stopped working
  - **Filter lists not updating** (off): enabled lists with no rules, or that haven't updated for much longer than their update interval
  - **AdGuard Home updates** (off): once for each new version

  The checks only read from your own servers. On the lock screen, notifications only say that something needs a look. On ColorOS and other phones that close background apps, allow AdCtrl to run in the background (the Battery row on the same page opens the right settings).

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

In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support AdCtrl** and **Report a bug** rows that open these pages.

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
