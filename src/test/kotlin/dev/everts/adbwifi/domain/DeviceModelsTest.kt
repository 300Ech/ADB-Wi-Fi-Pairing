package dev.everts.adbwifi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DeviceModelsTest {

    @ParameterizedTest
    @CsvSource(
        "device, DEVICE",
        "offline, OFFLINE",
        "unauthorized, UNAUTHORIZED",
        "'no permissions (missing udev rules?); see [http://x]', NO_PERMISSIONS",
        "something-new, UNKNOWN",
    )
    fun `device state from adb text`(text: String, state: DeviceState) {
        assertEquals(state, DeviceState.fromAdb(text))
    }

    @Test
    fun `only device state is online`() {
        assertEquals(listOf(DeviceState.DEVICE), DeviceState.entries.filter { it.isOnline })
    }

    @Test
    fun `wireless detection and endpoint`() {
        val manual = Device("192.168.0.16:40073", DeviceState.DEVICE)
        val mdns = Device("adb-58181FDCR0065F-XeVEcV._adb-tls-connect._tcp", DeviceState.DEVICE)
        val usb = Device("58181FDCR0065F", DeviceState.DEVICE)

        assertEquals(AdbEndpoint("192.168.0.16", 40073), manual.endpoint)
        assertTrue(manual.isWireless)
        assertNull(mdns.endpoint)
        assertTrue(mdns.isWireless)
        assertFalse(usb.isWireless)
    }

    @Test
    fun `mdns service type accepts trailing dot`() {
        assertEquals(MdnsServiceType.TLS_CONNECT, MdnsServiceType.fromDnsName("_adb-tls-connect._tcp."))
        assertNull(MdnsServiceType.fromDnsName("_ipp._tcp"))
    }

    @ParameterizedTest
    @CsvSource(
        "adb-58181FDCR0065F-XeVEcV, 58181FDCR0065F",
        "adb-EMULATOR36X3X10X0-hk2X6D, EMULATOR36X3X10X0",
        "adb-serial-with-dashes-a1B2c3, serial-with-dashes",
        "studio-Xy12Ab, ",
    )
    fun `device serial from instance name`(instance: String, serial: String?) {
        val service = MdnsService(instance, MdnsServiceType.TLS_CONNECT, AdbEndpoint("192.168.0.16", 40073))
        assertEquals(serial, service.deviceSerial)
    }
}
