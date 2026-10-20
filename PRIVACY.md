# Privacy

AdCtrl is built so that your DNS data stays between your phone and your own AdGuard Home server.

- **Only your servers.** AdCtrl talks only to the AdGuard Home servers you add. Every other destination is blocked inside the app. There are no third-party SDKs, ads or analytics making network calls, and the app never contacts Google.
- **Telemetry-free.** No accounts, analytics, usage statistics, crash reporting or tracking of any kind.
- **Your query log stays private.** The query log shows what everyone on your network visits. It's only ever sent between your phone and your own server, and AdCtrl never writes it, your passwords or addresses containing your passwords to the system log.
- **Passwords are encrypted.** Server passwords are stored encrypted with a key held in the Android Keystore, which never leaves the phone.
- **Nothing is backed up.** AdCtrl's data is excluded from Android backups and from moving to a new phone.
- **Secure connections.** HTTPS by default. Plain HTTP is only possible for addresses on your own network, per server, after a warning. A self-signed certificate is accepted only after you confirm its SHA-256 fingerprint, and only for that server.
- **Screenshots.** By default, screenshots, screen recordings and the recent apps view show a blank screen. You can change this in Settings.
- **Links.** The Support, Report a bug, Source code and Licence rows open in your browser only when you tap them.

The only permission AdCtrl asks for is network access (plus notifications, if you turn them on).
