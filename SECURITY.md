# Security Policy

Midroid is an experimental Android client that renders user-selected Misskey instances through Android System WebView. Security reports are welcome and should be handled privately when they could put users at risk.

## Reporting a vulnerability

Please do **not** open a public issue for an undisclosed vulnerability.

After this repository becomes public, use GitHub's private vulnerability-reporting / Security Advisory flow when available. Include:

- affected Midroid version or commit;
- Android version and WebView provider/version;
- a minimal reproduction;
- expected and observed behavior;
- impact assessment;
- logs or screenshots with account names, tokens, cookies, note IDs and private URLs removed.

If GitHub's private vulnerability-reporting button is unavailable, do not publish exploit details. Open a public issue containing only the words **"Security contact requested"** and no reproduction, affected URL, logs, screenshots or technical details; the maintainer can then arrange a private disclosure channel before any sensitive information is exchanged.

## Supported versions

Until the first stable release, only the latest released version and the current `main` branch are considered supported. Security fixes may require users to update immediately.

## Security boundaries

Midroid intentionally:

- accepts only HTTPS Misskey instance origins;
- disables cleartext traffic;
- does not bypass TLS/SSL errors;
- keeps non-instance top-level navigation outside the trusted Misskey origin;
- keeps WebView remote debugging limited to debuggable builds;
- does not commit release signing keys or passwords;
- uses a dedicated persistent signing identity for update-compatible release APKs.

A configured Misskey server and the Android System WebView provider remain separate trust domains. Vulnerabilities in those components should also be reported to their respective maintainers.
