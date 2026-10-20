# Changelog

What changed in each AdCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/AdCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.5.0 (2026-10-21)

The Queries tab: your live query log, with one-tap block and unblock.

### New

- **Live query log**, newest first. New queries appear at the top every few seconds while the tab is open (pause and resume live updates with the button at the top), and older ones load as you scroll down, all the way back to what AdGuard Home keeps. Pull down to refresh
- Each query shows the domain, the time, what happened to it (blocked, blocked service, malware or phishing, adult website, safe search, allowed by rule, rewritten, processed, from cache) and which list blocked or allowed it, plus the query type, the client (by name when AdGuard Home knows it), the response time and the upstream DNS server
- **Search** domains or clients, and **filter** by All, Blocked, Allowed, Processed, Rewritten, Malware and phishing, or Adult websites
- **Query details**: tap a query for its answer records (and the upstream's original answer when it was changed), the rule that matched and the list it came from, the response code, time taken, upstream, cache and DNSSEC, and the client's name, IP address, ClientID, protocol and whois
- **Block or Unblock in one tap** from a query's details. Choose only this host, this host and its subdomains, or the whole domain, and optionally only for this client. AdCtrl shows the exact rule (like `||example.com^` or `@@||example.com^$important`) before adding it to your custom rules. Unblocking wins over every blocklist, blocked service, safe browsing and parental control. With more than one server, add the rule **on all servers** at once
- **This client's queries**: from a query's details, show only that client's queries; the search then narrows its domains. Tap the client chip to go back to everyone
- **Check host**: type a domain (optionally a client and query type) to see whether AdGuard Home would block it, and by which rule and list, with Block or Unblock right there. Open it from the button at the top of Queries or from a query's details
- **Overview top lists are tappable**: a top queried domain opens Queries searching for it, a top blocked domain opens the blocked queries for it, and a top client opens that client's queries

### Privacy

- The query log is only held in memory while you use AdCtrl: it's never written to the phone's storage, and it's cleared when you switch servers

## v0.4.1 (2026-10-21)

### Fixed

- **Pausing and resuming protection failed with error 415** ("only content-type application/json is allowed"), from the Overview and from the Quick Settings tile. AdGuard Home only accepts changes whose content type is exactly `application/json`, and AdCtrl added `; charset=utf-8` to it. Every change AdCtrl sends now uses exactly `application/json`
- **"Update available" never showed**, for the same reason: the update check failed quietly. It now works, on the Overview, in Server info and with **Check for updates**

## v0.4.0 (2026-10-21)

### New

- **Quick Settings tile.** Add the **AdGuard** tile from your phone's Quick Settings edit screen. It's lit while protection is on and dimmed while it's paused or off, with the state underneath: "On · tap to pause 5 minutes", "Paused · 4:32 left" (counting down while the panel is open), or "Off"
- **Tap the tile** to pause protection for your default duration, or to turn it back on when it's paused or off. **Long-press** it to open AdCtrl
- **Settings › Quick Settings tile**: choose how long a tap pauses for (30 seconds up to 8 hours, or until you turn it back on; 5 minutes by default), and whether the tile pauses **all servers** that aren't read-only or only the active one
- **5 minutes** is now also in the pause menu on the Overview
- With more than one server, the tile is named after the active server

### Security

- **No pausing from the lock screen**: tapping the tile on a locked phone asks you to unlock it first
- On a read-only server the tile only shows the state; tapping it opens AdCtrl instead of changing anything

## v0.3.0 (2026-10-20)

The Overview is here: protection, statistics and server info for your AdGuard Home.

### New

- **Protection switch** at the top of the Overview, showing whether AdGuard Home is blocking for your network. Switching it off opens the **pause menu** (nothing changes until you pick a duration): 30 seconds, 1 minute, 10 minutes, 1 hour, 8 hours, or until you turn it back on
- **Live countdown** while protection is paused, with **Resume now** and **Change pause**. When the pause runs out, the Overview checks the server and shows protection back on
- **All servers**: with more than one server, the pause menu can pause (or resume) every server that isn't read-only at once, for a primary and secondary pair. If one server can't be changed, AdCtrl tells you which
- **Statistics** for the last 24 hours, 7 days, 30 days or 90 days. Only ranges your server keeps statistics for are offered: total DNS queries, queries blocked by filters (with the percentage), malware and phishing blocked, adult websites blocked, safe search enforced, and the average processing time
- **Queries over time**: a graph of queries and blocked queries per hour or per day. Touch and drag across it to see the numbers at any point
- **Top lists**: top queried domains, top blocked domains, top clients and top upstream DNS servers, with each upstream's number of answers and average response time
- **Needs attention** list: protection off or paused, filtering switched off, filter lists that aren't updating (no rules, never downloaded, or not updated for over twice the update interval), a new AdGuard Home version, and statistics switched off
- **Server info**: AdGuard Home version, DNS addresses and port, upstream DNS servers, DNS cache and DHCP server on or off, and whether an update is available
- **Clear DNS cache** and **Update to …** (when AdGuard Home can update itself), each after a confirmation, plus **Check for updates**
- **Works offline**: when the server can't be reached, the Overview keeps showing the last known state, marked with when it was from and why it couldn't refresh, with **Retry**
- Pull down to refresh. The Overview refreshes every 30 seconds while you look at it, less often while the server is unreachable, and not at all in the background

### Changed

- **Read-only mode** now hides the protection switch, pause menu, clear cache and update actions; the state is still shown

### Privacy

- To show the last known Overview when the server is unreachable, AdCtrl keeps the latest statistics (including top domains and clients) in its private storage on the phone. It's never backed up or sent anywhere, and is deleted when you remove the server

## v0.2.0 (2026-10-20)

AdCtrl now connects to your AdGuard Home. The tabs are still placeholders; the Overview with the protection switch comes next.

### New

- **Add your AdGuard Home server.** On first start, AdCtrl asks for the address you open AdGuard Home's web interface on, plus your AdGuard Home username and password. Without `http://`, the address uses HTTPS; a port you type is kept. AdCtrl checks that the address really is AdGuard Home, that the sign-in works and which version it runs, then shows the version, the protection state and the DNS addresses before you tap **Continue**
- **Only the address you enter.** AdCtrl only ever connects to the exact address saved for each server. There's no second address and no automatic switching to another one
- **AdGuard Home v0.107.37 or newer.** Older versions are refused with a message saying which version is needed; after updating AdGuard Home, tap **Retry**
- **Several servers**, such as a primary and a secondary: **Account › Servers** lists them, with switching, changing the address or sign-in, read-only mode and removing a server. The profile picture shows the active server's first letter
- **Read-only mode** per server, during setup and under **Account**. In read-only mode AdCtrl refuses to send any change to that server, whatever the screen
- **Connection problems** show as a card on every tab, with the one action that fixes it: **Retry**, **Sign in again** for a wrong password, **Check again** for a changed certificate, or **Change address or sign-in**
- Clear messages for an unreachable server, a wrong username or password (including AdGuard Home's temporary block after several wrong passwords), an untrusted or changed certificate, an address that isn't AdGuard Home, a redirect, and a version that's too old

### Security

- **Your servers only.** Every request is checked before it's sent: anything other than your saved servers (or the address you're setting up) is blocked. Redirects are never followed, so your password can't be passed on to another host
- **Self-signed certificates** are shown by their SHA-256 fingerprint the first time you connect. Once you tap **Trust**, AdCtrl accepts only that exact certificate, and only for that server; if it ever changes, AdCtrl stops and asks again. There's never a "trust all" option
- **Plain HTTP only on your own network.** `http://` addresses are only accepted for private IPs (like 192.168.x.x) and local names (`.lan`, `.local`, `.home.arpa`, `.internal` or a single word), and only after you switch on **Allow plain HTTP for this server** and confirm a warning. A local name must also point to a private address, or AdCtrl won't connect
- **Passwords are encrypted** with a key in the Android Keystore that never leaves the phone. A saved password is only reused for the same address and username: changing either needs it typed again
- Server setup is always hidden from screenshots and the recent apps view, because the password is typed there

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

### Fixed

- The first build of 0.1.0 didn't compile because the bundled Manrope variable font's weights need an extra compiler setting; it's added, so 0.1.0 now builds and is published
- The Google-free library check stopped the second build of 0.1.0 because it ran after the release key had already been cleaned up, and the build refuses to set up a release without the key. The check now runs while the key is still available, and shows the reason on the build page if it ever fails again
