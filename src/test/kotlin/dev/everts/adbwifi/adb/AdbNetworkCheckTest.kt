package dev.everts.adbwifi.adb

import dev.everts.adbwifi.application.FakeAdbGateway
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdbNetworkCheckTest {

    private val router = AdbEndpoint("192.168.0.1", 5555)

    private fun check(result: ConnectResult): Pair<Boolean?, List<String>> = runBlocking {
        val gateway = FakeAdbGateway().apply { connectResult = { result } }
        gateway.cannotReachLocalNetwork(router) to gateway.calls.toList()
    }

    @Test
    fun `no route means the adb server has no network`() {
        assertEquals(true, check(ConnectResult.Failed(AdbFailureReason.NO_ROUTE_TO_HOST, "No route to host")).first)
    }

    @Test
    fun `refused means the router answered`() {
        assertEquals(false, check(ConnectResult.Failed(AdbFailureReason.CONNECTION_REFUSED, "refused")).first)
    }

    @Test
    fun `timeouts are inconclusive`() {
        assertEquals(null, check(ConnectResult.Failed(AdbFailureReason.TIMED_OUT, "")).first)
    }

    @Test
    fun `an accepted connection is closed again`() {
        val (blocked, calls) = check(ConnectResult.Connected(router, alreadyConnected = false))

        assertEquals(false, blocked)
        assertEquals(listOf("connect $router", "disconnect $router"), calls)
    }
}
