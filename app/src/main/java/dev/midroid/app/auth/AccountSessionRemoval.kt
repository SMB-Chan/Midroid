package dev.midroid.app.auth

import android.content.Context
import dev.midroid.app.config.AccountProfile
import dev.midroid.app.push.AndroidKeyStorePushKeyStore
import dev.midroid.app.push.PushSubscriptionStore

/**
 * Server-side cleanup performed when a Midroid account is removed.
 *
 * Every remote call is best effort: an offline device, a proxy that blocks the API, or an
 * instance older than Misskey 2026.9.0 (no `i/revoke-token`) must never block local
 * deletion. The token is still needed for these calls, so remote cleanup runs before
 * [deleteLocalSecrets] and callers must not clear the token store in between.
 *
 * Must be called off the main thread because it performs network and AndroidKeyStore I/O.
 */
class AccountSessionRemoval(context: Context) {
    private val tokenStore = MiAuthTokenStore(context)
    private val pushSubscriptions = PushSubscriptionStore(context)
    private val pushKeys = AndroidKeyStorePushKeyStore(context)

    data class RemoteOutcome(
        val hadToken: Boolean,
        val revokeOutcome: MisskeyApi.RevokeTokenOutcome?,
        val hadPushSubscription: Boolean,
        val pushUnregistered: Boolean,
    ) {
        val needsTokenWarning: Boolean
            get() = revokeOutcome == MisskeyApi.RevokeTokenOutcome.FAILED ||
                revokeOutcome == MisskeyApi.RevokeTokenOutcome.UNSUPPORTED

        val tokenUnsupported: Boolean
            get() = revokeOutcome == MisskeyApi.RevokeTokenOutcome.UNSUPPORTED
    }

    fun revokeRemote(account: AccountProfile): RemoteOutcome {
        val instance = account.instanceConfig()
        val token = tokenStore.load(account.id)
        val subscription = pushSubscriptions.findByAccountId(account.id)
        val hadToken = token != null

        if (instance == null || token == null) {
            return RemoteOutcome(
                hadToken = hadToken,
                revokeOutcome = if (hadToken) MisskeyApi.RevokeTokenOutcome.FAILED else null,
                hadPushSubscription = subscription != null,
                pushUnregistered = false,
            )
        }

        // Unregister push before revoking the token. auth/publickey prove
        // subscription ownership (Misskey 2026.10.0); i scopes the delete to
        // this user. 2026.9.x ignores the unknown key fields.
        val pushUnregistered = if (subscription == null) {
            true
        } else {
            runCatching {
                val response = MisskeyApi.postJson(
                    MisskeyApi.endpointUrl(instance.origin, "/api/sw/unregister"),
                    MisskeyApi.swUnregisterBody(
                        token,
                        subscription.endpoint,
                        subscription.authBase64Url,
                        subscription.p256dhBase64Url,
                    ),
                    timeoutMs = TIMEOUT_MS,
                )
                response.isSuccess()
            }.getOrDefault(false)
        }

        val revokeOutcome = runCatching {
            val response = MisskeyApi.postJson(
                MisskeyApi.endpointUrl(instance.origin, "/api/i/revoke-token"),
                MisskeyApi.revokeTokenBody(token),
                timeoutMs = TIMEOUT_MS,
            )
            MisskeyApi.revokeTokenOutcome(response.status)
        }.getOrDefault(MisskeyApi.RevokeTokenOutcome.FAILED)

        return RemoteOutcome(
            hadToken = hadToken,
            revokeOutcome = revokeOutcome,
            hadPushSubscription = subscription != null,
            pushUnregistered = pushUnregistered,
        )
    }

    fun deleteLocalSecrets(accountId: String) {
        pushSubscriptions.removeByAccountId(accountId)
        tokenStore.delete(accountId)
        pushKeys.delete(accountId)
    }

    companion object {
        private const val TIMEOUT_MS = 8_000
    }
}
