# Midroid account profiles

Midroid supports multiple Misskey logins, including multiple accounts on the same instance and accounts on different instances, by mapping each additional Midroid account to an AndroidX WebKit WebView Profile.

## Isolation boundary

A Midroid `AccountProfile` stores only local routing metadata:

- account id
- Misskey HTTPS origin
- WebView profile name
- local display label
- last same-origin URL

Misskey passwords and API access tokens are not stored by the native account registry. Authentication remains inside the WebView profile. Named WebView profiles isolate cookies, WebStorage, Service Workers, and profile-backed HTTP cache state.

## Migration

The account that existed before multi-account support continues to use the WebView `Default` profile. This preserves its existing cookies and WebStorage and avoids forcing an upgrade-time login. New accounts use names of the form `midroid_<uuid>`.

## Runtime lifecycle

Only one WebView is intentionally alive at a time.

When switching accounts Midroid:

1. stores the current account's last same-origin URL;
2. stops and destroys the current WebView;
3. selects the new `AccountProfile`;
4. creates a new WebView and assigns its named profile before touching WebView settings;
5. restores the account's last same-origin URL, or its instance origin as a safe fallback.

This keeps the multi-account feature aligned with Midroid's battery-focused lifecycle design.

## Multi-instance behavior

The account model treats the instance origin as part of the account identity. Two accounts may therefore target the same Misskey origin or different origins without sharing login state.

## Compatibility

Additional accounts require `WebViewFeature.MULTI_PROFILE`. If the installed Android System WebView does not support it, Midroid keeps the existing/default account usable and refuses to create or open isolated named profiles rather than silently mixing cookies.

## Removal

An account can be removed from the account switcher. Removal is a two-phase cleanup:

1. Best-effort remote cleanup on a background thread, before any local secret is deleted:
   - `sw/unregister` when the account has a stored push subscription. The subscription keys (`auth` and `publickey`, the same values as `sw/register`) prove ownership of the endpoint; the MiAuth token is sent as `i` and scopes the delete to that user on Misskey 2026.10.0. Misskey 2026.9.x ignores those unknown key fields. The call still runs before `i/revoke-token`, while the token is available;
   - `i/revoke-token` (Misskey 2026.9.0+) to revoke the account's own MiAuth access token. A 404 answer means the instance predates the endpoint and is reported to the user as "unsupported"; the token then stays valid on the instance and the user is told to revoke it from Misskey's own settings. Offline or failed revocation never blocks local deletion.
2. Local deletion: the account's MiAuth token, push subscription record and AndroidKeyStore-wrapped push receiver keys are deleted, the account is removed from the registry (with the active account reselected), and the account's WebView login state is wiped — isolated profiles through `ProfileStore.deleteProfile()`, the shared Default profile through a cookie/WebStorage wipe that does not touch other isolated profiles.

When the last account is removed, the legacy single-instance preference is also cleared so the next launch starts at first-run setup instead of resurrecting the old instance.

## Native integrations

Any native action that needs authenticated network state must use the CookieManager associated with the source WebView profile, not the process-global default CookieManager. This currently applies to:

- Android native audio fallback;
- DownloadManager attachment downloads.

Service Worker cache configuration is also applied to the WebView's assigned profile when multi-profile support is available.
