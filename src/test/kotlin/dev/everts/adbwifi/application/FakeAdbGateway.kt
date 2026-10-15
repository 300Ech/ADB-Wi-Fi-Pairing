package dev.everts.adbwifi.application

import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsStatus
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CopyOnWriteArrayList

/** Gateway en memoria para probar los casos de uso; registra cada llamada en [calls]. */
class FakeAdbGateway : AdbGateway {
    var devices: List<Device> = emptyList()
    var services: List<MdnsService> = emptyList()
    var props: Map<Pair<String, String>, String> = emptyMap()
    var pairResult: (AdbEndpoint) -> PairingResult = { PairingResult.Paired(it, null) }
    var connectResult: (AdbEndpoint) -> ConnectResult = { ConnectResult.Connected(it, alreadyConnected = false) }
    var wifiAddress: String? = null
    val calls = CopyOnWriteArrayList<String>()

    override suspend fun pair(endpoint: AdbEndpoint, code: PairingCode): PairingResult {
        calls += "pair $endpoint"
        return pairResult(endpoint)
    }

    override suspend fun connect(endpoint: AdbEndpoint): ConnectResult {
        calls += "connect $endpoint"
        return connectResult(endpoint)
    }

    override suspend fun disconnect(endpoint: AdbEndpoint): Boolean {
        calls += "disconnect $endpoint"
        return true
    }

    override suspend fun devices(): List<Device> = devices

    override suspend fun mdnsCheck(): MdnsStatus = MdnsStatus.Available("adb discovery 0.0.0")

    override suspend fun mdnsServices(): List<MdnsService> = services

    override fun trackMdnsServices(): Flow<List<MdnsService>> = flowOf(services)

    override suspend fun tcpip(serial: String, port: Int) {
        calls += "tcpip $serial $port"
    }

    override suspend fun version(): String = "37.0.1-15733141"

    override suspend fun getProp(serial: String, property: String): String? = props[serial to property]

    override suspend fun wifiAddress(serial: String): String? = wifiAddress

    override suspend fun restartServer(environment: Map<String, String>) {
        calls += "restart $environment"
    }
}
