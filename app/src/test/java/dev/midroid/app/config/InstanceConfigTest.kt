package dev.midroid.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceConfigTest {
    @Test
    fun normalizesMissingSchemeToHttps() {
        val parsed = InstanceConfig.parse("example.com").getOrThrow()
        assertEquals("https://example.com", parsed.origin)
    }

    @Test
    fun stripsPathsAndNormalizesDefaultPort() {
        val parsed = InstanceConfig.parse("https://Example.com:443/notes/123").getOrThrow()
        assertEquals("https://example.com", parsed.origin)
    }

    @Test
    fun preservesNonDefaultHttpsPort() {
        val parsed = InstanceConfig.parse("https://example.com:8443/foo").getOrThrow()
        assertEquals("https://example.com:8443", parsed.origin)
    }

    @Test
    fun rejectsCleartextInstances() {
        assertTrue(InstanceConfig.parse("http://example.com").isFailure)
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        assertTrue(InstanceConfig.parse("https://user:password@example.com").isFailure)
    }

    @Test
    fun matchesOnlySameHttpsOrigin() {
        val instance = InstanceConfig.parse("https://example.com").getOrThrow()
        assertTrue(instance.owns("https://example.com/notes/1"))
        assertFalse(instance.owns("https://cdn.example.com/file"))
        assertFalse(instance.owns("http://example.com/"))
    }
}
