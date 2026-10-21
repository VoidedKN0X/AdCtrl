# Privacy

AdCtrl is built so that your DNS data stays between your phone and your own AdGuard Home server.

- **Only your servers.** AdCtrl talks only to the AdGuard Home servers you add. Every other destination is blocked inside the app. There are no third-party SDKs, ads or analytics making network calls, and the app never contacts Google.
- **Telemetry-free.** No accounts, analytics, usage statistics, crash reporting or tracking of any kind.
- **Your query log stays private.** The query log shows what everyone on your network visits. It's only ever sent between your phone and your own server, and AdCtrl never writes it, your passwords or addresses containing your passwords to the system log, and never stores the query log on the phone.
- **Last known Overview.** To show the Overview when your server can't be reached, AdCtrl keeps the latest statistics (including top domains and clients) in its private storage. It's never backed up or sent anywhere, and is deleted when you remove the server.
- **Passwords are encrypted.** Server passwords are stored encrypted with a key held in the Android Keystore, which never leaves the phone. With the app lock on, they're also sealed with a key that only exists while AdCtrl is unlocked.
- **App lock.** The app PIN is never stored, only used to unlock a key; fingerprint unlock uses a key in the phone's secure hardware. While AdCtrl is locked, nothing is sent to your servers.
- **Nothing is backed up.** AdCtrl's data is excluded from Android backups and from moving to a new phone.
- **Secure connections.** HTTPS by default. Plain HTTP is only possible for addresses on your own network, per server, after a warning. A self-signed certificate is accepted only after you confirm its SHA-256 fingerprint, and only for that server.
- **Screenshots.** By default, screenshots, screen recordings and the recent apps view show a blank screen. You can change this in Settings › Privacy and security; with the app lock on, the recent apps view is always blank.
- **Links.** The Support, Report a bug, Source code and Licence rows open in your browser only when you tap them.

The only permission AdCtrl asks for is network access.
