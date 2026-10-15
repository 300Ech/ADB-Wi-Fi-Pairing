package dev.everts.adbwifi.persistence

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import dev.everts.adbwifi.application.PairedDevicesStore
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.PairedDevice

/**
 * Dispositivos emparejados, a nivel de aplicación. No se sincroniza entre máquinas
 * ([RoamingType.DISABLED]): las IPs y el emparejamiento son propios de esta máquina.
 */
@Service(Service.Level.APP)
@State(name = "AdbWifiPairedDevices", storages = [Storage(value = "adbWifi.xml", roamingType = RoamingType.DISABLED)])
class PairedDevicesState : PersistentStateComponent<PairedDevicesState.Xml>, PairedDevicesStore {

    /** Forma serializable; [PairedDevice] es inmutable y no depende de la plataforma. */
    class Xml {
        @XCollection(style = XCollection.Style.v2)
        var devices: MutableList<StoredDevice> = mutableListOf()
    }

    @Tag("device")
    class StoredDevice {
        var id: String = ""
        var name: String = ""
        var host: String? = null
        var port: Int = 0
        var autoReconnect: Boolean = true
    }

    private var current: List<PairedDevice> = emptyList()

    override val devices: List<PairedDevice>
        @Synchronized get() = current

    @Synchronized
    override fun update(transform: (List<PairedDevice>) -> List<PairedDevice>) {
        current = transform(current).distinctBy { it.id }
    }

    @Synchronized
    override fun getState(): Xml = Xml().apply { devices = current.mapTo(mutableListOf(), ::toXml) }

    @Synchronized
    override fun loadState(state: Xml) {
        current = state.devices.filter { it.id.isNotBlank() }.map(::fromXml)
    }

    private fun toXml(device: PairedDevice) = StoredDevice().apply {
        id = device.id
        name = device.name
        host = device.lastEndpoint?.host
        port = device.lastEndpoint?.port ?: 0
        autoReconnect = device.autoReconnect
    }

    private fun fromXml(stored: StoredDevice) = PairedDevice(
        id = stored.id,
        name = stored.name.ifBlank { stored.id },
        lastEndpoint = stored.host
            ?.takeIf { it.isNotBlank() && stored.port in VALID_PORTS }
            ?.let { AdbEndpoint(it, stored.port) },
        autoReconnect = stored.autoReconnect,
    )

    companion object {
        private val VALID_PORTS = 1..65_535

        fun getInstance(): PairedDevicesState = service()
    }
}
