package dev.everts.adbwifi.persistence

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.PairedDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PairedDevicesStateTest {

    @Test
    fun `round trips through xml`() {
        val devices = listOf(
            PairedDevice("58181FDCR0065F", "Pixel 10", AdbEndpoint("192.168.0.16", 40073)),
            PairedDevice("OTHER", "Tablet", null, autoReconnect = false),
            PairedDevice("V6", "IPv6 phone", AdbEndpoint("fe80::1", 5555)),
        )
        val original = PairedDevicesState().apply { update { devices } }

        val element = XmlSerializer.serialize(original.state)
        val restored = PairedDevicesState().apply {
            loadState(XmlSerializer.deserialize(element, PairedDevicesState.Xml::class.java))
        }

        assertEquals(devices, restored.devices, JDOMUtil.write(element))
    }

    @Test
    fun `update drops duplicate ids`() {
        val state = PairedDevicesState()

        state.update { listOf(PairedDevice("A", "first"), PairedDevice("A", "second")) }

        assertEquals(listOf(PairedDevice("A", "first")), state.devices)
    }
}
