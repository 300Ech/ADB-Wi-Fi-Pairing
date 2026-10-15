package dev.everts.adbwifi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceListBuilderTest {

    private val pixel = "58181FDCR0065F"
    private val connectService = MdnsService(
        "adb-$pixel-XeVEcV",
        MdnsServiceType.TLS_CONNECT,
        AdbEndpoint("192.168.0.16", 40073),
    )

    @Test
    fun `paired device not on the network`() {
        val paired = PairedDevice(pixel, "Pixel 10", AdbEndpoint("192.168.0.16", 39000))

        val entry = DeviceListBuilder.build(listOf(paired), emptyList(), emptyList()).single()

        assertEquals(EntryStatus.NOT_SEEN, entry.status)
        assertEquals(AdbEndpoint("192.168.0.16", 39000), entry.endpoint)
        assertEquals("Pixel 10", entry.name)
    }

    @Test
    fun `paired device announcing a new port is available at that port`() {
        val paired = PairedDevice(pixel, "Pixel 10", AdbEndpoint("192.168.0.16", 39000))

        val entry = DeviceListBuilder.build(listOf(paired), emptyList(), listOf(connectService)).single()

        assertEquals(EntryStatus.AVAILABLE, entry.status)
        assertEquals(AdbEndpoint("192.168.0.16", 40073), entry.endpoint)
    }

    @Test
    fun `mdns auto connected transport merges with paired and service`() {
        val device = Device("adb-$pixel-XeVEcV._adb-tls-connect._tcp", DeviceState.DEVICE, model = "Pixel_10")

        val entry = DeviceListBuilder.build(emptyList(), listOf(device), listOf(connectService)).single()

        assertEquals(pixel, entry.key)
        assertEquals(EntryStatus.CONNECTED, entry.status)
        assertEquals("Pixel 10", entry.name)
        assertEquals(AdbEndpoint("192.168.0.16", 40073), entry.endpoint)
    }

    @Test
    fun `manual connection uses identity to merge with mdns service`() {
        val device = Device("192.168.0.16:40073", DeviceState.DEVICE)
        val identities = mapOf(device.serial to DeviceIdentity(pixel, "Pixel 10"))

        val entries = DeviceListBuilder.build(emptyList(), listOf(device), listOf(connectService), identities)

        assertEquals(listOf(pixel), entries.map { it.key })
        assertEquals("Pixel 10", entries.single().name)
    }

    @Test
    fun `pairing service shows a pairable row and qr services are ignored`() {
        val pairing = MdnsService("adb-NEWPHONE01-a1b2c3", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.30", 37001))
        val qr = MdnsService("studio-Xy12Ab3c", MdnsServiceType.PAIRING, AdbEndpoint("192.168.0.31", 37002))

        val entry = DeviceListBuilder.build(emptyList(), emptyList(), listOf(pairing, qr)).single()

        assertEquals(EntryStatus.PAIRING, entry.status)
        assertEquals(pairing, entry.pairingService)
    }

    @Test
    fun `usb devices are listed, emulators are not, and order follows status`() {
        val devices = listOf(
            Device("emulator-5554", DeviceState.DEVICE),
            Device("0A1B2C3D", DeviceState.DEVICE, model = "Pixel_8"),
            Device("192.168.0.50:5555", DeviceState.UNAUTHORIZED),
        )
        val paired = listOf(PairedDevice("OLD", "Old phone"))

        val entries = DeviceListBuilder.build(paired, devices, listOf(connectService))

        assertEquals(
            listOf(EntryStatus.USB, EntryStatus.UNAUTHORIZED, EntryStatus.AVAILABLE, EntryStatus.NOT_SEEN),
            entries.map { it.status },
        )
        assertTrue(entries.none { it.key.startsWith("emulator-") })
    }
}
