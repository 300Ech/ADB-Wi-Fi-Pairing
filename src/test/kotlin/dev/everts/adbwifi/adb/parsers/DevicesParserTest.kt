package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.adb.AdbFixtures
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.DeviceState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevicesParserTest {

    @Test
    fun `empty list`() {
        assertTrue(DevicesParser.parse(AdbFixtures.stdout("devices-empty")).isEmpty())
    }

    @Test
    fun `emulator in long format`() {
        assertEquals(
            listOf(
                Device(
                    serial = "emulator-5580",
                    state = DeviceState.DEVICE,
                    model = "sdk_gphone64_arm64",
                    product = "sdk_gphone64_arm64",
                    deviceName = "emu64a",
                    transportId = 8,
                ),
            ),
            DevicesParser.parse(AdbFixtures.stdout("devices-usb-emulator")),
        )
    }

    @Test
    fun `short format has only serial and state`() {
        assertEquals(
            listOf(Device("emulator-5580", DeviceState.DEVICE)),
            DevicesParser.parse(AdbFixtures.stdout("devices-short")),
        )
    }

    @Test
    fun `tcp device and offline emulator`() {
        val devices = DevicesParser.parse(AdbFixtures.stdout("devices-mixed"))

        assertEquals(listOf("127.0.0.1:7790", "emulator-5580"), devices.map { it.serial })
        assertEquals(listOf(DeviceState.DEVICE, DeviceState.OFFLINE), devices.map { it.state })
        assertEquals(listOf(26L, 11L), devices.map { it.transportId })
        assertEquals(listOf(true, false), devices.map { it.isWireless })
    }

    @Test
    fun `real pixel auto connected over mdns`() {
        val device = DevicesParser.parse(AdbFixtures.stdout("devices-wifi-mdns")).single()

        assertEquals("adb-58181FDCR0065F-XeVEcV._adb-tls-connect._tcp", device.serial)
        assertEquals(DeviceState.DEVICE, device.state)
        assertEquals("Pixel_10", device.model)
        assertEquals("frankel", device.product)
        assertTrue(device.isWireless)
    }

    @Test
    fun `wireless states and no permissions explanation`() {
        val devices = DevicesParser.parse(AdbFixtures.stdout("devices-wireless.reconstructed"))

        assertEquals(
            listOf(
                DeviceState.DEVICE,
                DeviceState.DEVICE,
                DeviceState.UNAUTHORIZED,
                DeviceState.NO_PERMISSIONS,
                DeviceState.AUTHORIZING,
            ),
            devices.map { it.state },
        )
        val mdns = devices[0]
        assertEquals("adb-58181FDCR0065F-XeVEcV._adb-tls-connect._tcp", mdns.serial)
        assertEquals("Pixel_10", mdns.model)
        assertTrue(mdns.isWireless)
        val noPermissions = devices[3]
        assertEquals("0A1B2C3D4E60", noPermissions.serial)
        assertEquals(6L, noPermissions.transportId)
    }

    @Test
    fun `ignores daemon startup lines`() {
        val output = "* daemon not running; starting now at tcp:5037\n* daemon started successfully\n" +
            AdbFixtures.stdout("devices-short")
        assertEquals(listOf("emulator-5580"), DevicesParser.parse(output).map { it.serial })
    }
}
