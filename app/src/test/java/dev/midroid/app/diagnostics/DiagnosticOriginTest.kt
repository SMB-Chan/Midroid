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
}
