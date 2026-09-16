package dev.midroid.app.push

object MisskeyPushRegistration {
    const val FIELD_ENDPOINT = "endpoint"
    const val FIELD_PUBLIC_KEY = "publickey"
    const val FIELD_AUTH = "auth"

    val FIELD_NAMES: Set<String> = setOf(FIELD_ENDPOINT, FIELD_PUBLIC_KEY, FIELD_AUTH)

    fun build(
        endpoint: String,
        p256dhBase64Url: String,
        authBase64Url: String,
    ): Map<String, String> {
        require(RelayProtocol.isPlausibleRelayEndpoint(endpoint)) {
            "Registration endpoint must be a versioned relay URL."
        }
        require(p256dhBase64Url.isNotBlank()) { "Receiver public key must not be blank." }
        require(authBase64Url.isNotBlank()) { "Auth secret must not be blank." }
        return mapOf(
            FIELD_ENDPOINT to endpoint,
            FIELD_PUBLIC_KEY to p256dhBase64Url,
            FIELD_AUTH to authBase64Url,
        )
    }
}
