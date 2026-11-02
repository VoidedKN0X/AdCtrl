# Security policy

Thank you for helping keep AdCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/AdCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of AdCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your AdGuard Home password, your query log or other data leaking to anyone other than your own AdGuard Home server
- Ways around the app lock (including through the Quick Settings tile, the widget, app shortcuts or notifications), or protected screens showing up in screenshots when they shouldn't
- Ways to pause protection, block or unblock a domain, or change anything else on your server without you asking for it
- Another app on the phone being able to make AdCtrl do something you didn't ask for (for example through push from ntfy)
- Certificate checks being skipped, or a pinned certificate being replaced without you confirming it
- Anything that lets someone run code or read files they shouldn't

Problems in AdGuard Home itself should go to that project.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
6B:5A:64:77:7A:50:72:14:AA:DF:B6:E6:63:03:31:A8:D7:40:A8:40:CE:D4:A6:F9:6D:B6:F4:66:D0:ED:DD:CE
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
