package dev.everts.adbwifi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AdbEndpointTest {

    @Test
    fun `parses ipv4 with port`() {
        assertEquals(AdbEndpoint("192.168.1.20", 37123), AdbEndpoint.parse("192.168.1.20:37123"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(AdbEndpoint("10.0.0.5", 5555), AdbEndpoint.parse("  10.0.0.5:5555 \n"))
    }

    @Test
    fun `parses bracketed ipv6 and prints it back with brackets`() {
        val endpoint = AdbEndpoint.parse("[fe80::1c2a:3bff]:41000")
        assertEquals(AdbEndpoint("fe80::1c2a:3bff", 41000), endpoint)
        assertEquals("[fe80::1c2a:3bff]:41000", endpoint.toString())
    }

    @Test
    fun `prints ipv4 in adb format`() {
        assertEquals("192.168.1.20:37123", AdbEndpoint("192.168.1.20", 37123).toString())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "192.168.1.20",
            ":5555",
            "192.168.1.20:",
            "192.168.1.20:abc",
            "192.168.1.20:0",
            "192.168.1.20:70000",
            "fe80::1:5555",
            "[fe80::1]5555",
            "my host:5555",
        ],
    )
    fun `rejects invalid input`(text: String) {
        assertNull(AdbEndpoint.parse(text))
    }

    @Test
    fun `constructor rejects out of range port`() {
        assertThrows<IllegalArgumentException> { AdbEndpoint("192.168.1.20", 0) }
    }
}
