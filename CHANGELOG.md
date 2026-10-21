# Changelog

What changed in each AdCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/AdCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.8.1 (2026-10-21)

### Changed

- **Settings are in the same order as in the other Ctrl apps**: Appearance, then Privacy and security, then the Quick Settings tile
- **README**: the App lock section now uses the same wording as the other Ctrl apps

### Security

- **The lock screen and the PIN screens are always kept out of screenshots, screen recordings and the recent apps view**, even with **Hide in screenshots and recent apps** turned off

## v0.8.0 (2026-10-21)

The app lock: the same one as in every other Ctrl app, with its own PIN, optional fingerprint unlock and your server passwords sealed while AdCtrl is locked.

### New

- **App lock** (**Account › Settings › Privacy and security › App lock**, off until you turn it on). AdCtrl asks for its own app PIN when you open it. The PIN needs at least 6 digits and is separate from your phone's; very obvious ones like 111111 or 123456 are refused. You choose it twice when turning the lock on
- **Unlock with fingerprint** (optional): only strong biometrics such as your fingerprint, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is still needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Opening a link in your browser from the Account page doesn't count as leaving, unless you stay away longer than 5 minutes
- **Confirm changes** (optional, with the app lock on): AdCtrl asks for your fingerprint or app PIN before anything that changes a server, such as pausing protection, blocking or unblocking a domain, editing rules, lists, blocked services, rewrites or clients. One confirmation covers the next minute. Cancelling sends nothing
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too
- **Forgot PIN?** on the lock screen removes your servers and their passwords, and the statistics kept on the phone, so you can add them again. Nothing changes on your AdGuard Home servers

### Changed

- **Settings › Privacy** is now **Privacy and security**, with the app lock rows first and **Hide content in screenshots & recents** renamed to **Hide in screenshots and recent apps** (still on by default), the same as in the other Ctrl apps
- **Quick Settings tile with the app lock on:** while AdCtrl is locked, the tile shows "AdCtrl is locked · tap to open" and a tap opens AdCtrl's lock screen instead of pausing protection, so nobody can pause it without your PIN. With **Confirm changes** on, a tap always opens AdCtrl, because the tile itself can't ask for your fingerprint or PIN. With the lock unlocked (for example with "Lock again" after 15 minutes), the tile works as before
- The message when a change isn't confirmed now says it wasn't confirmed with your fingerprint or app PIN, and that nothing was sent
- **README**: new App lock section; Features list only what's in the app now, with the widget, app shortcuts and notifications under Planned; install through Obtainium or the CtrlF-Droid repo; MemoCtrl added to the Ctrl series table. **PRIVACY.md**: no notification permission (AdCtrl has no notifications yet), and how the app lock protects your passwords

### Security

- **Your server passwords are sealed while AdCtrl is locked.** With the app lock on, every saved password is also encrypted with a key that only exists in memory while AdCtrl is unlocked, and is wiped the moment it locks, together with any decrypted password. A locked AdCtrl, or a copy of its files, can't sign in to your servers, and nothing is sent to them until you unlock
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying AdCtrl's files to a computer isn't enough to try PINs there
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN
- With the app lock on, the recent apps view always shows a blank card, even with **Hide in screenshots and recent apps** off

## v0.7.1 (2026-10-21)

### Fixed

- **AdCtrl crashed when opening the Clients tab** as soon as some clients had queries in the chosen range and others didn't. Sorting the list mixed two kinds of number; it now sorts busiest first as intended
- A client sent by the server in an unexpected shape can no longer crash the Clients tab: it's still listed by its name and addresses, and other problems reading the clients show as an error with Retry instead
- The query log and the clients list can no longer crash if the server sends the same entry twice

## v0.7.0 (2026-10-21)

The Clients tab: every device on your network, what it's been doing, and its own settings. All four tabs are now complete.

### New

- **Clients list**: the clients you added and the devices AdGuard Home found by itself (from DHCP, ARP, reverse DNS, the hosts file or whois), busiest first, with their addresses, tags and number of queries in the range chosen on the Overview. Search by name, address or tag, and filter by All, Added, Found or Blocked. A device whose address belongs to a client you added is shown under that client
- **Client page**: name, addresses, tags, whois details, and whether it's blocked (with the access-list rule that blocks it)
- **Activity**: its queries in the Overview's range, how many of its newest 500 queries were blocked, its most queried and most blocked domains recently, and **Recent queries** to open its queries in the query log
- **Settings** for clients you added: use the network-wide settings or its own filtering, safe browsing, parental control and safe search; the network-wide blocked services or its own (pick from the full list); its own upstream DNS servers; and keeping it out of the query log or statistics. Only the setting you change is sent: everything else about the client, including settings AdCtrl doesn't show, stays as it was
- **Add a client** with a name, addresses (IP, CIDR range, MAC address or ClientID) and tags, from the **+** on the Clients tab or with **Add as a client** on a device AdGuard Home found. **Change name, addresses and tags** or **Remove** a client later
- **Block this client** stops AdGuard Home answering all of a device's DNS queries, through the access list, after a confirmation that names exactly what's added. **Unblock** lets it back. If your access list only allows listed clients, AdCtrl takes the device off the allowed list instead, and says so. MAC addresses can't be blocked this way, and the button explains why
- **Overview top clients** now open the client's page

### Privacy

- A client's recent queries are only read while its page is open, and never stored on the phone

## v0.6.0 (2026-10-21)

The Filters tab: lists, custom rules, blocked services, DNS rewrites and safe browsing, parental control and safe search, each in its own section at the top of the tab.

### New

- **Lists**: every blocklist and allowlist with its name, address, number of rules and when it last updated. A warning icon marks lists that never downloaded or have no rules. Switch each one on or off, tap one to rename it, change its address or remove it (after a confirmation), and **Update lists now** downloads them all again and says how many changed
- **Add a list** from your own address, or pick one of 45 well-known lists that come with AdCtrl (general, security, and trackers for OPPO & Realme, Samsung, Xiaomi, Smart TVs, consoles and more), taken from AdGuard Home's own vetted list. Nothing is downloaded to show them, and lists you already have are marked
- **Filtering on or off** for all lists and custom rules (turning it off asks first), and how often lists update: never, every hour, 12 hours, day, 3 days or week
- **Custom rules editor** with line numbers and colours for comments, block rules, allow rules (`@@`), hosts-file lines, anchors (`||`, `^`) and modifiers (`$…`). Long lines scroll sideways so line numbers always match
- **Search** the rules, with the number of matches and previous/next
- **Possible mistakes** are flagged with the line number: addresses with `http://`, paths, `*.example.com`, a missing `^`, browser element-hiding rules, spaces, empty rules and duplicates, with a one-tap fix where there's an obvious one
- **Save with a look first**: before saving, AdCtrl shows exactly which lines are added and removed. Unsaved edits are kept while you switch tabs, **Discard changes** puts the saved rules back, and going back with unsaved edits asks first
- **Blocked services**: every service AdGuard Home can block, grouped by kind, with search and a **Blocked** view. One tap blocks or unblocks a service; changes made elsewhere in the meantime are kept
- **Pause schedule** for blocked services: per day, the hours when they aren't blocked (for example games in the evening), in the server's time zone
- **DNS rewrites**: list, search, add, change and delete rewrites (domain → IP address, a domain, or keep the real A / AAAA answer), with wildcard domains like `*.example.com`. Each rewrite and all rewrites at once can be switched off without deleting them
- **Protection**: safe browsing, parental control and safe search for the whole network, and which search engines safe search applies to
- In **read-only mode** every section shows its settings without any way to change them

## v0.5.1 (2026-10-21)

### Changed

- **Block and Unblock keep your custom rules tidy.** Unblocking a domain that your own custom rule blocks now removes that line instead of adding an allow rule on top, and blocking a domain your own allow rule lets through removes that allow rule. The dialog shows exactly which line will be removed before you confirm
- A new rule is only added when it's still needed: when unblocking, if a blocklist, blocked service, safe browsing or parental control also blocks the domain once your rule is gone (then the allow rule is added, with the scope and client you chose); when blocking, if nothing blocks the domain once your allow rule is gone
- Every copy of a removed line goes, and nothing else in your custom rules changes. With **On all servers**, each server is checked and tidied on its own
- If the domain is already the way you asked, nothing is changed and AdCtrl says so
- **Check host** now offers **Block** for domains allowed by a rule too, so you can undo an unblock from there
- **Profile picture**: shows the first letter of the server's name only when the name starts with a letter. Names like `192.168.1.2` now show AdCtrl's shield instead of "1". Name a server (for example "Primary") under **Account › Servers › Change address or sign-in** to see its letter

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
