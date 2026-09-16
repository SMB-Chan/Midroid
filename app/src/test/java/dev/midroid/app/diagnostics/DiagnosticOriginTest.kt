package dev.midroid.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticOriginTest {
    @Test
    fun stripsPathQueryAndFragment() {
        assertEquals(
            "https://example.social",
            diagnosticOrigin("https://example.social/notes/abc?token=secret#fragment"),
        )
    }

    @Test
    fun keepsNonDefaultPort() {
        assertEquals(
            "https://example.social:8443",
            diagnosticOrigin("https://example.social:8443/path"),
        )
    }

    @Test
    fun removesDefaultHttpsPort() {
        assertEquals(
            "https://example.social",
            diagnosticOrigin("https://example.social:443/path"),
        )
    }

    @Test
    fun invalidInputDoesNotLeakRawValue() {
        assertEquals("invalid", diagnosticOrigin("not a url / private value"))
    }

    @Test
    fun missingInputIsExplicit() {
        assertEquals("none", diagnosticOrigin(null))
    }

    @Test
    fun normalizesUppercaseSchemeAndHost() {
        assertEquals(
            "https://example.social",
            diagnosticOrigin("HTTPS://Example.Social/notes/abc"),
        )
    }

    @Test
    fun stripsDefaultHttpPort() {
        assertEquals(
            "http://example.social",
            diagnosticOrigin("http://example.social:80/path"),
        )
    }

    @Test
    fun neverLeaksUserInfo() {
        assertEquals(
            "https://example.social",
            diagnosticOrigin("https://user:secret@example.social/notes/1?token=x"),
        )
    }
}
