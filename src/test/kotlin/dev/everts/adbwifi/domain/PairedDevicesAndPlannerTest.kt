package dev.everts.adbwifi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PairedDevicesAndPlannerTest {

    private val serial = "58181FDCR0065F"
    private val endpoint = AdbEndpoint("192.168.0.16", 40073)

    @Test
    fun `upsert keeps name and autoReconnect of the existing device`() {
        val existing = PairedDevice(serial, "Work phone", AdbEndpoint("192.168.0.16", 1111), autoReconnect = false)

        val result = PairedDevices.upsert(listOf(existing), PairedDevice(serial, serial, endpoint))

        assertEquals(listOf(existing.copy(lastEndpoint = endpoint)), result)
    }

    @Test
    fun `reconcile replaces ip id with the real serial and takes the model as name`() {
        val paired = listOf(PairedDevice("192.168.0.16", "192.168.0.16"))
        val device = Device("192.168.0.16:40073", DeviceState.DEVICE)
        val identities = mapOf(device.serial to DeviceIdentity(serial, "Pixel 10"))

        val result = PairedDevices.reconcile(paired, listOf(device), identities)

        assertEquals(listOf(PairedDevice(serial, "Pixel 10", endpoint)), result)
    }

    @Test
    fun `reconcile adopts mdns connected devices with the service endpoint`() {
        val device = Device("adb-$serial-XeVEcV._adb-tls-connect._tcp", DeviceState.DEVICE)

        val result = PairedDevices.reconcile(emptyList(), listOf(device), emptyMap(), mapOf(serial to endpoint))

        assertEquals(listOf(PairedDevice(serial, serial, endpoint)), result)
    }

    @Test
    fun `reconcile ignores usb and offline devices and keeps custom names`() {
        val paired = listOf(PairedDevice(serial, "Mine", AdbEndpoint("192.168.0.16", 1)))
        val devices = listOf(
            Device("0A1B2C3D", DeviceState.DEVICE),
            Device("192.168.0.16:40073", DeviceState.OFFLINE),
            Device("192.168.0.16:40074", DeviceState.DEVICE),
        )
        val identities = devices.associate { it.serial to DeviceIdentity(serial, "Pixel 10") }

        val result = PairedDevices.reconcile(paired, devices, identities)

        assertEquals(listOf(PairedDevice(serial, "Mine", AdbEndpoint("192.168.0.16", 40074))), result)
    }

    private fun entry(status: EntryStatus, autoReconnect: Boolean = true) = DeviceEntry(
        key = serial,
        name = "Pixel 10",
        status = status,
        endpoint = endpoint,
        paired = PairedDevice(serial, "Pixel 10", AdbEndpoint("192.168.0.16", 39000), autoReconnect),
        connectService = MdnsService("adb-$serial-XeVEcV", MdnsServiceType.TLS_CONNECT, endpoint),
    )

    /** Disponible desde el segundo 0; las llamadas usan `now` posterior a la gracia salvo que se diga. */
    private val since = mapOf(serial to 0.seconds)

    private fun plan(
        entry: DeviceEntry,
        attempts: Map<String, ReconnectAttempt> = emptyMap(),
        now: Duration = 60.seconds,
    ) = ReconnectPlanner.plan(listOf(entry), attempts, now, since)

    @Test
    fun `planner targets announced port and falls back to the last endpoint`() {
        assertEquals(listOf(ReconnectPlanner.Target(serial, endpoint)), plan(entry(EntryStatus.AVAILABLE)))
        assertEquals(
            listOf(ReconnectPlanner.Target(serial, AdbEndpoint("192.168.0.16", 39000))),
            plan(entry(EntryStatus.NOT_SEEN).copy(connectService = null)),
        )
    }

    @Test
    fun `planner gives adb time to auto connect a newly announced device`() {
        assertTrue(plan(entry(EntryStatus.AVAILABLE), now = 9.seconds).isEmpty())
        assertEquals(1, plan(entry(EntryStatus.AVAILABLE), now = 10.seconds).size)
        val available = listOf(entry(EntryStatus.AVAILABLE))
        val unknownSince = ReconnectPlanner.plan(available, emptyMap(), 60.seconds, emptyMap())
        assertTrue(unknownSince.isEmpty())
    }

    @Test
    fun `planner skips connected, disabled and recently failed devices`() {
        assertTrue(plan(entry(EntryStatus.CONNECTED)).isEmpty())
        assertTrue(plan(entry(EntryStatus.AVAILABLE, false)).isEmpty())

        val failed = mapOf(serial to ReconnectAttempt(endpoint, 20.seconds, 2, AdbFailureReason.CONNECTION_REFUSED))
        assertTrue(plan(entry(EntryStatus.AVAILABLE), failed, 49.seconds).isEmpty())
        assertEquals(1, plan(entry(EntryStatus.AVAILABLE), failed, 50.seconds).size)
    }

    @Test
    fun `planner never retries a rejected certificate on the same port but tries a new one`() {
        val rejected = mapOf(serial to ReconnectAttempt(endpoint, 0.seconds, 1, AdbFailureReason.AUTHENTICATION_FAILED))
        assertTrue(plan(entry(EntryStatus.AVAILABLE), rejected, 60.minutes).isEmpty())

        val newPort = entry(EntryStatus.AVAILABLE).let {
            it.copy(connectService = it.connectService?.copy(endpoint = AdbEndpoint("192.168.0.16", 41000)))
        }
        assertEquals(1, plan(newPort, rejected).size)
    }

    @Test
    fun `backoff doubles up to the maximum`() {
        assertEquals(
            listOf(0, 15, 30, 60, 120, 240, 300, 300),
            (0..7).map { ReconnectPlanner.backoff(it).inWholeSeconds.toInt() },
        )
    }

    @Test
    fun `instance names, guids and transport serials share the serial`() {
        assertEquals(serial, AdbInstanceName.serialOf("adb-$serial-XeVEcV"))
        assertEquals(serial, AdbInstanceName.serialOf("adb-$serial-XeVEcV._adb-tls-connect._tcp"))
        assertEquals(null, AdbInstanceName.serialOf("192.168.0.16:40073"))
    }
}
