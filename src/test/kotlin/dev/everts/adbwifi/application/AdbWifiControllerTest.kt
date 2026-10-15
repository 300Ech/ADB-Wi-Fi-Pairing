package dev.everts.adbwifi.application

import dev.everts.adbwifi.adb.AdbNotFoundException
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.DeviceState
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import dev.everts.adbwifi.domain.PairedDevice
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import dev.everts.adbwifi.domain.QrPairingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AdbWifiControllerTest {

    private val serial = "58181FDCR0065F"
    private val connectEndpoint = AdbEndpoint("192.168.0.16", 40073)
    private val service = MdnsService("adb-$serial-XeVEcV", MdnsServiceType.TLS_CONNECT, connectEndpoint)
    private val code = requireNotNull(PairingCode.parse("123456"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = FakeAdbGateway()
    private val store = InMemoryStore()
    private val events = mutableListOf<AdbWifiEvent>()
    private var clock: Duration = 0.seconds
    private var adbAvailable = true
    private var phoneReachable = true
    private var loggedPairingFailure: AdbFailureReason? = null
    private var studioBlocked: Boolean? = false
    private var networkChecks = 0

    /** Respuestas sucesivas a "¿adb llega a la red?"; vacía = no se sabe. */
    private val adbBlocked = mutableListOf<Boolean?>()

    private val controller = AdbWifiController(
        scope = scope,
        gatewayProvider = { if (adbAvailable) gateway else throw AdbNotFoundException(emptyList()) },
        store = store,
        settings = { ControllerSettings() },
        notifier = { events += it },
        probes = ControllerProbes(
            now = { clock },
            reachable = { phoneReachable },
            pairingFailure = { _, _ -> loggedPairingFailure },
            localNetworkBlocked = { networkChecks++; studioBlocked },
            adbNetworkBlocked = { adbBlocked.removeFirstOrNull() },
        ),
    )

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `refresh learns identity of manual connections and adopts them as paired`() = runBlocking {
        gateway.devices = listOf(Device("192.168.0.16:40073", DeviceState.DEVICE))
        gateway.props = mapOf(
            ("192.168.0.16:40073" to "ro.serialno") to serial,
            ("192.168.0.16:40073" to "ro.product.model") to "Pixel 10",
        )

        controller.refresh()

        val entry = controller.state.value.entries.single()
        assertEquals(serial, entry.key)
        assertEquals("Pixel 10", entry.name)
        assertEquals(EntryStatus.CONNECTED, entry.status)
        assertEquals(listOf(PairedDevice(serial, "Pixel 10", connectEndpoint)), store.devices)
    }

    @Test
    fun `adb missing is reported in the state`() = runBlocking {
        adbAvailable = false

        controller.refresh()

        assertNotNull(controller.state.value.adbProblem)
        assertTrue(controller.state.value.loaded)
    }

    @Test
    fun `pair stores the device and connects to the given port`() = runBlocking {
        val pairing = AdbEndpoint("192.168.0.16", 37001)
        gateway.pairResult = { PairingResult.Paired(it, "adb-$serial-XeVEcV") }

        val outcome = controller.pair(pairing, code, connectEndpoint)

        assertEquals(PostPairing.Connected(connectEndpoint), outcome.connection)
        assertEquals(listOf("pair $pairing", "connect $connectEndpoint"), gateway.calls)
        assertEquals(serial, store.devices.single().id)
    }

    @Test
    fun `pair connects through the announced mdns port when adb does not`() = runBlocking {
        gateway.pairResult = { PairingResult.Paired(it, "adb-$serial-XeVEcV") }
        gateway.services = listOf(service)

        val outcome = controller.pair(AdbEndpoint("192.168.0.16", 37001), code)

        assertEquals(PostPairing.Connected(connectEndpoint), outcome.connection)
        assertTrue("connect $connectEndpoint" in gateway.calls)
    }

    @Test
    fun `pairing failure takes the real cause from the adb server log`() = runBlocking {
        gateway.pairResult = { PairingResult.Failed(AdbFailureReason.PROTOCOL_FAULT, "error: protocol fault") }
        loggedPairingFailure = AdbFailureReason.WRONG_PAIRING_CODE

        val outcome = controller.pair(AdbEndpoint("192.168.0.16", 37001), code)

        assertEquals(AdbFailureReason.WRONG_PAIRING_CODE, (outcome.pairing as PairingResult.Failed).reason)
        assertTrue(gateway.calls.none { it.startsWith("restart") })
    }

    @Test
    fun `adb server without network is restarted and pairing retried once`() = runBlocking {
        val pairing = AdbEndpoint("192.168.0.16", 37001)
        var attempts = 0
        gateway.pairResult = {
            attempts++
            if (attempts == 1) {
                PairingResult.Failed(AdbFailureReason.PROTOCOL_FAULT, "error: protocol fault")
            } else {
                PairingResult.Paired(it, "adb-$serial-XeVEcV")
            }
        }
        loggedPairingFailure = AdbFailureReason.NO_ROUTE_TO_HOST

        val outcome = controller.pair(pairing, code, connectEndpoint)

        assertInstanceOf(PairingResult.Paired::class.java, outcome.pairing)
        assertEquals(listOf("pair $pairing", "restart {}", "pair $pairing"), gateway.calls.take(3))
    }

    @Test
    fun `no restart when this computer cannot reach the phone either`() = runBlocking {
        gateway.connectResult = { ConnectResult.Failed(AdbFailureReason.NO_ROUTE_TO_HOST, "No route to host") }
        phoneReachable = false

        val result = controller.connect(connectEndpoint)

        assertEquals(AdbFailureReason.NO_ROUTE_TO_HOST, (result as ConnectResult.Failed).reason)
        assertEquals(listOf("connect $connectEndpoint"), gateway.calls)
    }

    @Test
    fun `connect still cut off after restart reports the adb server`() = runBlocking {
        gateway.connectResult = { ConnectResult.Failed(AdbFailureReason.NO_ROUTE_TO_HOST, "No route to host") }

        val result = controller.connect(connectEndpoint)

        assertEquals(AdbFailureReason.ADB_SERVER_NO_NETWORK, (result as ConnectResult.Failed).reason)
        assertEquals(listOf("connect $connectEndpoint", "restart {}", "connect $connectEndpoint"), gateway.calls)
    }

    @Test
    fun `blocked local network is reported and adb restarts once the permission is given`() = runBlocking {
        studioBlocked = true
        controller.checkLocalNetwork()
        controller.checkLocalNetwork()
        assertTrue(controller.state.value.localNetworkBlocked)
        assertEquals(1, networkChecks, "no se repite antes del intervalo")

        studioBlocked = false
        clock += 5.seconds
        controller.checkLocalNetwork()

        assertFalse(controller.state.value.localNetworkBlocked)
        assertEquals(listOf("restart {}"), gateway.calls, "el servidor arrancado sin permiso sigue ciego")
    }

    @Test
    fun `adb still blocked after the permission asks for an android studio restart`() = runBlocking {
        studioBlocked = true
        controller.checkLocalNetwork()

        studioBlocked = false
        adbBlocked += true
        controller.checkLocalNetwork(force = true)

        assertTrue(controller.state.value.restartRequired)
        assertFalse(controller.state.value.localNetworkBlocked)
        assertEquals(listOf("restart {}"), gateway.calls)
    }

    @Test
    fun `adb started without network before this session is restarted once and works`() = runBlocking {
        adbBlocked += listOf(true, false)

        controller.checkLocalNetwork()

        assertFalse(controller.state.value.restartRequired)
        assertEquals(listOf("restart {}"), gateway.calls)
    }

    @Test
    fun `allowed local network is checked once a minute and restarts nothing`() = runBlocking {
        controller.checkLocalNetwork()
        clock += 30.seconds
        controller.checkLocalNetwork()
        controller.checkLocalNetwork(force = true)

        assertEquals(2, networkChecks)
        assertFalse(controller.state.value.localNetworkBlocked)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `failed pairing stores nothing`() = runBlocking {
        gateway.pairResult = { PairingResult.Failed(AdbFailureReason.PROTOCOL_FAULT, "error: protocol fault") }

        val outcome = controller.pair(AdbEndpoint("192.168.0.16", 37001), code)

        assertInstanceOf(PairingResult.Failed::class.java, outcome.pairing)
        assertTrue(store.devices.isEmpty())
    }

    @Test
    fun `reconnect connects a paired device at its new port and notifies`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10", AdbEndpoint("192.168.0.16", 39000))) }
        gateway.services = listOf(service)
        controller.refresh()
        controller.reconnectOnce()
        assertTrue(gateway.calls.isEmpty(), "adb tiene unos segundos para conectar solo")

        clock += 10.seconds
        controller.reconnectOnce()

        assertEquals(listOf("connect $connectEndpoint"), gateway.calls)
        assertEquals(listOf<AdbWifiEvent>(AdbWifiEvent.Reconnected("Pixel 10", connectEndpoint)), events)
    }

    @Test
    fun `rejected certificate notifies once and is not retried`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10")) }
        gateway.services = listOf(service)
        gateway.connectResult = {
            ConnectResult.Failed(AdbFailureReason.AUTHENTICATION_FAILED, "failed to connect to $it")
        }
        controller.refresh()

        clock += 10.seconds
        controller.reconnectOnce()
        clock += 1000.seconds
        controller.reconnectOnce()

        assertEquals(1, gateway.calls.size)
        assertEquals(listOf<AdbWifiEvent>(AdbWifiEvent.NeedsRepair("Pixel 10", connectEndpoint)), events)
    }

    @Test
    fun `manual disconnect suppresses automatic reconnection`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10")) }
        gateway.devices = listOf(Device("192.168.0.16:40073", DeviceState.DEVICE))
        gateway.props = mapOf(("192.168.0.16:40073" to "ro.serialno") to serial)
        gateway.services = listOf(service)
        controller.refresh()

        controller.disconnect(controller.state.value.entries.single())
        gateway.devices = emptyList()
        controller.refresh()
        clock += 60.seconds
        controller.reconnectOnce()

        assertEquals(listOf("disconnect $connectEndpoint"), gateway.calls)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `losing a connected paired device notifies`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10")) }
        gateway.devices = listOf(Device("adb-$serial-XeVEcV._adb-tls-connect._tcp", DeviceState.DEVICE))
        controller.refresh()

        gateway.devices = emptyList()
        controller.refresh()

        assertEquals(listOf<AdbWifiEvent>(AdbWifiEvent.Lost("Pixel 10")), events)
    }

    @Test
    fun `forget removes the device from the store`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10")) }
        controller.refresh()

        controller.forget(controller.state.value.entries.single())

        assertTrue(store.devices.isEmpty())
        assertTrue(controller.state.value.entries.isEmpty())
    }

    @Test
    fun `tcpip mode switches a usb device to its wifi address`() = runBlocking {
        gateway.devices = listOf(Device("0A1B2C3D", DeviceState.DEVICE))
        gateway.wifiAddress = "192.168.0.40"
        controller.refresh()

        val result = controller.enableTcpip(controller.state.value.entries.single())

        assertEquals(ConnectResult.Connected(AdbEndpoint("192.168.0.40", 5555), false), result)
        assertEquals(listOf("tcpip 0A1B2C3D 5555", "connect 192.168.0.40:5555"), gateway.calls)
    }

    @Test
    fun `stale saved address is probed before calling adb`() = runBlocking {
        store.update { listOf(PairedDevice(serial, "Pixel 10", AdbEndpoint("192.168.0.16", 39000))) }
        phoneReachable = false
        controller.refresh()

        controller.reconnectOnce()
        assertTrue(gateway.calls.isEmpty())

        phoneReachable = true
        clock += 1.seconds
        controller.reconnectOnce()
        assertTrue(gateway.calls.isEmpty(), "backoff tras el primer fallo")

        clock += 15.seconds
        controller.reconnectOnce()
        assertEquals(listOf("connect 192.168.0.16:39000"), gateway.calls)
    }

    @Test
    fun `phone opening its pairing dialog notifies once`() = runBlocking {
        val pairing = MdnsService("adb-NEWPHONE01-a1b2c3", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.30", 37001))
        controller.refresh()

        gateway.services = listOf(pairing)
        controller.refresh()
        controller.refresh()

        assertEquals(listOf<AdbWifiEvent>(AdbWifiEvent.PairingAvailable("NEWPHONE01", pairing.endpoint)), events)
    }

    @Test
    fun `no pairing notification while the user is already in a pairing dialog`() = runBlocking {
        val pairing = MdnsService("adb-NEWPHONE01-a1b2c3", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.30", 37001))
        controller.refresh()
        val release = controller.requestFastPolling()

        gateway.services = listOf(pairing)
        controller.refresh()
        release()
        release()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `qr pairing waits for the phone to announce the generated name and pairs with the password`() = runBlocking {
        val request = QrPairingRequest.generate()
        val pairingEndpoint = AdbEndpoint("192.168.0.16", 37555)
        gateway.services = listOf(MdnsService(request.serviceName, MdnsServiceType.PAIRING, pairingEndpoint))
        gateway.pairResult = { PairingResult.Paired(it, "adb-$serial-XeVEcV") }
        var found: AdbEndpoint? = null

        val outcome = controller.pairWithQr(request, timeout = 5.seconds) { found = it }

        assertEquals(pairingEndpoint, found)
        assertInstanceOf(PairingResult.Paired::class.java, outcome?.pairing)
        assertEquals("pair $pairingEndpoint", gateway.calls.first())
        assertEquals(serial, store.devices.single().id)
    }

    @Test
    fun `qr pairing gives up when the phone never appears`() = runBlocking {
        val outcome = controller.pairWithQr(QrPairingRequest.generate(), timeout = 1.seconds)

        assertEquals(null, outcome)
        assertTrue(gateway.calls.isEmpty())
    }

    private class InMemoryStore : PairedDevicesStore {
        override var devices: List<PairedDevice> = emptyList()

        override fun update(transform: (List<PairedDevice>) -> List<PairedDevice>) {
            devices = transform(devices)
        }
    }
}
