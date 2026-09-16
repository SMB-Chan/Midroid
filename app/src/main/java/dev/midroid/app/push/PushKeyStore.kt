package dev.midroid.app.push

data class StoredKeys(
    val accountId: String,
    val publicUncompressed: ByteArray,
    val privateScalar: ByteArray,
    val authSecret: ByteArray,
) {
    init {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        require(authSecret.size == PushKeys.AUTH_SECRET_BYTES) {
            "Auth secret must be 16 bytes."
        }
    }

    fun toPair(): ReceiverKeyPair = ReceiverKeyPair(publicUncompressed, privateScalar)

    override fun toString(): String = "StoredKeys(accountId=$accountId)"
}

interface PushKeyStore {
    fun create(accountId: String): StoredKeys
    fun load(accountId: String): StoredKeys?
    fun delete(accountId: String)
}

class InMemoryPushKeyStore : PushKeyStore {
    private val entries = mutableMapOf<String, StoredKeys>()

    override fun create(accountId: String): StoredKeys {
        require(accountId.isNotBlank()) { "Account id must not be blank." }
        val pair = PushKeys.generateKeyPair()
        val stored = StoredKeys(
            accountId = accountId,
            publicUncompressed = pair.publicUncompressed,
            privateScalar = pair.privateScalar,
            authSecret = PushKeys.generateAuthSecret(),
        )
        entries[accountId] = stored
        return stored
    }

    override fun load(accountId: String): StoredKeys? = entries[accountId]

    override fun delete(accountId: String) {
        entries.remove(accountId)
    }
}
