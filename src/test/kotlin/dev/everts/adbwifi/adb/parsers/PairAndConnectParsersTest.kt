package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.adb.AdbFixtures
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.PairingResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PairAndConnectParsersTest {

    private val requested = AdbEndpoint("127.0.0.1", 7791)

    private fun pair(case: String) =
        PairOutputParser.parse(AdbFixtures.stdout(case), AdbFixtures.stderr(case), requested)

    private fun connect(case: String) =
        ConnectOutputParser.parse(AdbFixtures.stdout(case), AdbFixtures.stderr(case), AdbEndpoint("127.0.0.1", 7790))

    @Test
    fun `pair success with guid`() {
        assertEquals(PairingResult.Paired(requested, "adb-EMULATOR36X3X10X0-hk2X6D"), pair("pair-ok"))
    }

    @Test
    fun `pair success without guid`() {
        assertEquals(
            PairingResult.Paired(AdbEndpoint("192.168.0.20", 37001), null),
            PairOutputParser.parse("Successfully paired to 192.168.0.20:37001\n", "", requested),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "pair-wrong-code, PROTOCOL_FAULT",
        "pair-closed-port, PROTOCOL_FAULT",
        "pair-wrong-password.reconstructed, WRONG_PAIRING_CODE",
        "pair-client-failed.reconstructed, PAIRING_CLIENT_FAILED",
    )
    fun `pair failures`(case: String, reason: AdbFailureReason) {
        val result = pair(case)
        assertEquals(
            PairingResult.Failed(reason, (AdbFixtures.stdout(case) + AdbFixtures.stderr(case)).trim()),
            result,
        )
    }

    @Test
    fun `connect success`() {
        assertEquals(
            ConnectResult.Connected(AdbEndpoint("127.0.0.1", 7790), alreadyConnected = false),
            connect("connect-ok"),
        )
    }

    @Test
    fun `connect when already connected`() {
        assertEquals(
            ConnectResult.Connected(AdbEndpoint("127.0.0.1", 7790), alreadyConnected = true),
            connect("connect-already"),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "connect-refused, CONNECTION_REFUSED",
        "connect-unresolved, HOST_NOT_RESOLVED",
        "connect-timeout, TIMED_OUT",
        "connect-auth-failed, AUTHENTICATION_FAILED",
        "connect-no-route.reconstructed, NO_ROUTE_TO_HOST",
        "connect-authenticate.reconstructed, AUTHENTICATION_FAILED",
    )
    fun `connect failures`(case: String, reason: AdbFailureReason) {
        val result = connect(case)
        assertEquals(ConnectResult.Failed(reason, AdbFixtures.stdout(case).trim()), result)
    }

    @Test
    fun `disconnect results`() {
        assertEquals(true, DisconnectOutputParser.parse(AdbFixtures.stdout("disconnect-ok"), ""))
        assertEquals(false, DisconnectOutputParser.parse("", AdbFixtures.stderr("disconnect-unknown")))
        assertNull(DisconnectOutputParser.parse("", "error: something unexpected"))
    }
}
