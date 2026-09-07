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

## Native integrations

Any native action that needs authenticated network state must use the CookieManager associated with the source WebView profile, not the process-global default CookieManager. This currently applies to:

- Android native audio fallback;
- DownloadManager attachment downloads.

Service Worker cache configuration is also applied to the WebView's assigned profile when multi-profile support is available.
