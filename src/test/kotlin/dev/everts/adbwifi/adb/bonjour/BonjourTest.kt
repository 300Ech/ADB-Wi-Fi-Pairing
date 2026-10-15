package dev.everts.adbwifi.adb.bonjour

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.readLines
import kotlin.io.path.setPosixFilePermissions
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class BonjourTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/fixtures/dnssd/$name")).readText()

    private val pixel =
        MdnsService("adb-58181FDCR0065F-XeVEcV", MdnsServiceType.TLS_CONNECT, AdbEndpoint("192.168.0.16", 34389))
    private val qr = MdnsService("studio-VISAKqPGtD", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.16", 46655))

    @Test
    fun `browse output gives the announced instances`() {
        assertEquals(
            setOf(DnsSdParsers.Instance(MdnsServiceType.PAIRING, "studio-VISAKqPGtD")),
            DnsSdParsers.browse(fixture("browse-pairing.txt")),
        )
        assertEquals(
            setOf(DnsSdParsers.Instance(MdnsServiceType.TLS_CONNECT, "adb-58181FDCR0065F-XeVEcV")),
            DnsSdParsers.browse(fixture("browse-connect.txt")),
        )
    }

    @Test
    fun `removed instances disappear`() {
        val output = fixture("browse-pairing.txt") +
            "14:40:01.000  Rmv        0  14 local.               _adb-tls-pairing._tcp. studio-VISAKqPGtD\n"
        assertTrue(DnsSdParsers.browse(output).isEmpty())
    }

    @Test
    fun `lookup output gives host and port`() {
        assertEquals("Android_84GKWQPE.local." to 34389, DnsSdParsers.lookup(fixture("lookup-connect.txt")))
        assertEquals("Android_84GKWQPE.local." to 46655, DnsSdParsers.lookup(fixture("lookup-pairing.txt")))
        assertNull(DnsSdParsers.lookup("Lookup x._adb-tls-connect._tcp.local.\n"))
    }

    @Test
    fun `merge keeps one entry per instance and prefers adb`() {
        val fromAdb = listOf(pixel.copy(endpoint = AdbEndpoint("192.168.0.16", 1111)))

        val merged = BonjourAssistedGateway.merge(fromAdb, listOf(pixel, qr))

        assertEquals(listOf(fromAdb.single(), qr), merged)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `browser finds and resolves services through dns-sd and caches addresses`(@TempDir dir: Path) = runBlocking {
        val script = dir.resolve("dns-sd")
        Files.copy(requireNotNull(javaClass.getResourceAsStream("/fake-dnssd/dns-sd.sh")), script)
        script.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        val fixtures = Path.of(requireNotNull(javaClass.getResource("/fixtures/dnssd")).toURI())
        val log = dir.resolve("calls.txt")
        val browser = DnsSdBrowser(
            dnsSd = script,
            browseWindow = 300.milliseconds,
            lookupTimeout = 2.seconds,
            resolveHost = { if (it == "Android_84GKWQPE.local.") "192.168.0.16" else null },
            environment = mapOf("FAKE_DNSSD_DIR" to fixtures.toString(), "FAKE_DNSSD_LOG" to log.toString()),
        )

        val elapsed = measureTime { assertEquals(setOf(pixel, qr), browser.services().toSet()) }
        browser.services()

        assertTrue(elapsed < 5.seconds, "dns-sd no se cortó: $elapsed")
        assertEquals(2, log.readLines().count { it.startsWith("-L") }, "las direcciones se cachean")
    }
}
