package dev.everts.adbwifi.diagnostics

import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.adb.AdbNotFoundException
import dev.everts.adbwifi.application.AdbWifiState
import dev.everts.adbwifi.application.FakeAdbGateway
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.DeviceEntry
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import dev.everts.adbwifi.domain.PairedDevice
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DiagnosticsTest {

    @Test
    fun `server log finds phones that reject the host certificate`() {
        val log = requireNotNull(javaClass.getResource("/fixtures/adb/server-log-certificate-rejected.txt")).readText()

        val rejections = AdbServerLog.rejections(log, 2026)

        assertEquals(setOf("58181FDCR0065F", "RF8R714D8RE"), rejections.keys)
        assertTrue(rejections.values.all { it.isAfter(LocalDateTime.of(2026, 10, 15, 10, 0)) })
    }

    private fun log(name: String) = requireNotNull(javaClass.getResource("/fixtures/adb/$name")).readText()

    @Test
    fun `server log explains a pairing protocol fault`() {
        val log = log("server-log-pairing-no-route.txt")
        val endpoint = AdbEndpoint("192.168.0.16", 38867)

        assertEquals(
            AdbFailureReason.NO_ROUTE_TO_HOST,
            AdbServerLog.pairingFailure(log, endpoint, LocalDateTime.of(2026, 10, 15, 14, 43, 30)),
        )
        assertEquals(null, AdbServerLog.pairingFailure(log, endpoint, LocalDateTime.of(2026, 10, 15, 14, 50)))
        assertEquals(
            null,
            AdbServerLog.pairingFailure(log, AdbEndpoint("192.168.0.99", 1), LocalDateTime.of(2026, 10, 15, 14, 0)),
        )
    }

    @Test
    fun `server log tells a wrong pairing code`() {
        assertEquals(
            AdbFailureReason.WRONG_PAIRING_CODE,
            AdbServerLog.pairingFailure(
                log("server-log-pairing-wrong-code.txt"),
                AdbEndpoint("127.0.0.1", 7791),
                LocalDateTime.of(2026, 10, 15, 10, 12, 43),
            ),
        )
    }

    @Test
    fun `route output gives the router address`() {
        val output = requireNotNull(javaClass.getResource("/fixtures/route/route-get-default.txt")).readText()
        assertEquals("192.168.0.1", RouteParser.gateway(output))
        assertEquals(null, RouteParser.gateway("route: writing to routing socket: not in table\n"))
    }

    private fun probe(result: ProbeResult, gateway: String? = "192.168.0.1") = object : NetworkProbe {
        override fun localNetworks() = listOf(LocalNetwork("en0", "192.168.0.21", 24))

        override suspend fun tcpConnect(endpoint: AdbEndpoint, timeout: Duration) = result

        override fun defaultGateway() = gateway
    }

    @Test
    fun `no route to the router means macOS blocks android studio`() = runBlocking {
        assertEquals(true, probe(ProbeResult.NO_ROUTE).localNetworkBlocked(1.seconds))
        assertEquals(false, probe(ProbeResult.REFUSED).localNetworkBlocked(1.seconds))
        assertEquals(null, probe(ProbeResult.TIMEOUT).localNetworkBlocked(1.seconds))
        assertEquals(null, probe(ProbeResult.NO_ROUTE, gateway = null).localNetworkBlocked(1.seconds))
    }

    @Test
    fun `blocked android studio replaces the network checks with the permission step`() = runBlocking {
        val runner = DiagnosticsRunner(
            gatewayProvider = { gateway },
            network = probe(ProbeResult.NO_ROUTE),
            rejectedSerials = { emptySet() },
            isMac = true,
        )

        val checks = runner.run(AdbWifiState(), endpoint)

        val blocked = checks.single { it.finding == Finding.STUDIO_NO_LOCAL_NETWORK }
        assertEquals(Severity.ERROR, blocked.severity)
        assertEquals(listOf(Fix.OPEN_LOCAL_NETWORK_SETTINGS), blocked.fixes)
        assertTrue(checks.none { it.finding == Finding.LOCAL_NETWORK_BLOCKED || it.finding == Finding.PORT_OPEN })
    }

    @Test
    fun `server log without rejections`() {
        val log = "10-15 10:12:43.954 10310 137121 I adb     : tls_connection.cpp:298 [client]: Handshake succeeded.\n"
        assertTrue(AdbServerLog.rejections(log, 2026).isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "192.168.0.21, 24, 192.168.0.16, true",
        "192.168.0.21, 24, 192.168.1.16, false",
        "10.0.0.5, 8, 10.200.3.4, true",
        "192.168.0.21, 24, fe80::1, false",
    )
    fun `subnet membership`(address: String, prefix: Int, target: String, expected: Boolean) {
        assertEquals(expected, LocalNetwork("en0", address, prefix).contains(target))
    }

    private val pixel = "58181FDCR0065F"
    private val endpoint = AdbEndpoint("192.168.0.16", 40073)
    private val gateway = FakeAdbGateway().apply {
        services = listOf(MdnsService("adb-$pixel-XeVEcV", MdnsServiceType.TLS_CONNECT, endpoint))
    }

    private fun runner(
        probe: ProbeResult,
        rejected: Set<String> = emptySet(),
        provider: () -> AdbGateway = { gateway },
    ) = DiagnosticsRunner(
        gatewayProvider = provider,
        network = object : NetworkProbe {
            override fun localNetworks() =
                listOf(LocalNetwork("en0", "192.168.0.21", 24), LocalNetwork("utun4", "10.8.0.2", 24))

            override suspend fun tcpConnect(endpoint: AdbEndpoint, timeout: Duration) = probe
        },
        rejectedSerials = { rejected },
        isMac = true,
    )

    private val state = AdbWifiState(
        entries = listOf(
            DeviceEntry(pixel, "Pixel 10", EntryStatus.AVAILABLE, endpoint, paired = PairedDevice(pixel, "Pixel 10")),
            DeviceEntry("OLD", "Tablet", EntryStatus.NOT_SEEN, null, paired = PairedDevice("OLD", "Tablet")),
        ),
    )

    @Test
    fun `reports the user's real problem - phone rejects this computer`() = runBlocking {
        val checks = runner(ProbeResult.OPEN, rejected = setOf(pixel)).run(state, null)

        assertEquals(
            listOf(
                Finding.ADB_OK,
                Finding.MDNS_OK,
                Finding.MDNS_ANNOUNCING,
                Finding.CERTIFICATE_REJECTED,
                Finding.WIRELESS_DEBUGGING_OFF,
                Finding.VPN_ACTIVE,
                Finding.SAME_SUBNET,
                Finding.PORT_OPEN,
            ),
            checks.map { it.finding },
        )
        val rejected = checks.single { it.finding == Finding.CERTIFICATE_REJECTED }
        assertEquals(listOf("Pixel 10"), rejected.args)
        assertEquals(listOf(Fix.PAIR_AGAIN), rejected.fixes)
    }

    @Test
    fun `devices seen by bonjour but not by adb mean adb discovery is blind`() = runBlocking {
        val qr = MdnsService("studio-VISAKqPGtD", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.16", 46655))
        val runner = DiagnosticsRunner(
            gatewayProvider = { gateway },
            bonjourServices = { gateway.services + qr },
            network = object : NetworkProbe {
                override fun localNetworks() = listOf(LocalNetwork("en0", "192.168.0.21", 24))

                override suspend fun tcpConnect(endpoint: AdbEndpoint, timeout: Duration) = ProbeResult.OPEN
            },
            rejectedSerials = { emptySet() },
            isMac = true,
        )

        val checks = runner.run(AdbWifiState(), null)

        val blind = checks.single { it.finding == Finding.ADB_DISCOVERY_BLIND }
        assertEquals(listOf("1"), blind.args)
        assertEquals(listOf(Fix.RESTART_ADB), blind.fixes)
        assertEquals(listOf("2"), checks.single { it.finding == Finding.MDNS_ANNOUNCING }.args)
    }

    @Test
    fun `no route to host on the same subnet on macOS is the Local Network permission`() = runBlocking {
        val checks = runner(ProbeResult.NO_ROUTE).run(state, endpoint)

        val blocked = checks.single { it.finding == Finding.LOCAL_NETWORK_BLOCKED }
        assertEquals(Severity.ERROR, blocked.severity)
        assertEquals(listOf(Fix.OPEN_LOCAL_NETWORK_SETTINGS), blocked.fixes)
    }

    @Test
    fun `different subnet and refused port`() = runBlocking {
        val checks = runner(ProbeResult.REFUSED).run(AdbWifiState(), AdbEndpoint("192.168.1.50", 37000))

        assertTrue(checks.any { it.finding == Finding.DIFFERENT_SUBNET })
        assertTrue(checks.any { it.finding == Finding.PORT_REFUSED })
    }

    @Test
    fun `missing adb still runs the network checks`() = runBlocking {
        val runner = runner(ProbeResult.TIMEOUT) { throw AdbNotFoundException(emptyList()) }

        val checks = runner.run(AdbWifiState(), endpoint)

        assertEquals(Finding.ADB_MISSING, checks.first().finding)
        assertTrue(checks.any { it.finding == Finding.PORT_TIMEOUT })
        assertFalse(checks.any { it.finding == Finding.MDNS_OK })
    }

    @Test
    fun `without a target the network checks are skipped`() = runBlocking {
        val checks = runner(ProbeResult.OPEN).run(AdbWifiState(), null)

        assertEquals(Finding.NO_TARGET, checks.last().finding)
    }
}
