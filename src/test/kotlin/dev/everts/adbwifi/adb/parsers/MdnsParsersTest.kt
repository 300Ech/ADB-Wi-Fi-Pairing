package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.adb.AdbFixtures
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import dev.everts.adbwifi.domain.MdnsStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdnsParsersTest {

    @Test
    fun `services from a real phone`() {
        assertEquals(
            listOf(
                MdnsService(
                    "adb-58181FDCR0065F-XeVEcV",
                    MdnsServiceType.TLS_CONNECT,
                    AdbEndpoint("192.168.0.16", 40073),
                ),
            ),
            MdnsServicesParser.parse(AdbFixtures.stdout("mdns-services")),
        )
    }

    @Test
    fun `services header only`() {
        assertTrue(MdnsServicesParser.parse("List of discovered mdns services\n\n").isEmpty())
    }

    @Test
    fun `services with bonjour trailing dot, ipv6 and foreign types`() {
        val output = """
            List of discovered mdns services
            adb-ABC123-a1b2c3	_adb-tls-pairing._tcp.	192.168.0.20:37001
            adb-ABC123-a1b2c3	_adb._tcp	fe80::1c2a:5555
            printer	_ipp._tcp	192.168.0.30:631
            broken line
        """.trimIndent()

        assertEquals(
            listOf(
                MdnsService("adb-ABC123-a1b2c3", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.20", 37001)),
                MdnsService("adb-ABC123-a1b2c3", MdnsServiceType.LEGACY, AdbEndpoint("fe80::1c2a", 5555)),
            ),
            MdnsServicesParser.parse(output),
        )
    }

    @Test
    fun `check with default backend`() {
        assertEquals(
            MdnsStatus.Available("adb discovery 0.0.0"),
            MdnsCheckParser.parse(AdbFixtures.stdout("mdns-check")),
        )
    }

    @Test
    fun `check with bonjour backend`() {
        assertEquals(
            MdnsStatus.Available("Bonjour 2200.0.8"),
            MdnsCheckParser.parse("mdns daemon version [Bonjour 2200.0.8]\n"),
        )
    }

    @Test
    fun `check with discovery disabled`() {
        assertEquals(
            MdnsStatus.Unavailable("mdns discovery disabled"),
            MdnsCheckParser.parse(AdbFixtures.stdout("mdns-check-disabled")),
        )
    }

    @Test
    fun `check with unexpected output keeps the text`() {
        assertEquals(MdnsStatus.Unavailable("something else"), MdnsCheckParser.parse("", "something else\n"))
    }
}
