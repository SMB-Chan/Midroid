package dev.midroid.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountProfileTest {
    @Test
    fun `legacy account keeps default WebView profile`() {
        val instance = InstanceConfig.parse("https://misskey.example").getOrThrow()
        val account = AccountProfile.legacyDefault(instance)

        assertEquals(AccountProfile.LEGACY_DEFAULT_ID, account.id)
        assertNull(account.profileName)
        assertEquals("misskey.example", account.displayLabel())
    }

    @Test
    fun `isolated account gets unique named WebView profile`() {
        val instance = InstanceConfig.parse("https://misskey.example").getOrThrow()
        val first = AccountProfile.isolated(instance, "one")
        val second = AccountProfile.isolated(instance, "two")

        assertTrue(first.profileName!!.startsWith("midroid_"))
        assertTrue(second.profileName!!.startsWith("midroid_"))
        assertFalse(first.profileName == second.profileName)
    }

    @Test
    fun `restore url is limited to account instance origin`() {
        val instance = InstanceConfig.parse("https://misskey.example").getOrThrow()
        val sameOrigin = AccountProfile(
            id = "test",
            profileName = "midroid_test",
            instanceOrigin = instance.origin,
            label = "test",
            lastUrl = "https://misskey.example/notes/123",
        )
        val foreignOrigin = sameOrigin.copy(lastUrl = "https://other.example/notes/123")

        assertEquals("https://misskey.example/notes/123", sameOrigin.restoreUrl())
        assertEquals("https://misskey.example", foreignOrigin.restoreUrl())
    }
}
