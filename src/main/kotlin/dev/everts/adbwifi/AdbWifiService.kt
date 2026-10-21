package dev.everts.adbwifi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.adb.AdbLocator
import dev.everts.adbwifi.adb.AdbTimeouts
import dev.everts.adbwifi.adb.CliAdbGateway
import dev.everts.adbwifi.adb.cannotReachLocalNetwork
import dev.everts.adbwifi.adb.bonjour.BonjourAssistedGateway
import dev.everts.adbwifi.adb.bonjour.DnsSdBrowser
import dev.everts.adbwifi.application.AdbWifiController
import dev.everts.adbwifi.application.ControllerProbes
import dev.everts.adbwifi.diagnostics.AdbServerLog
import dev.everts.adbwifi.diagnostics.DiagnosticsRunner
import dev.everts.adbwifi.diagnostics.JvmNetworkProbe
import dev.everts.adbwifi.diagnostics.PowerShellWindowsNetwork
import dev.everts.adbwifi.diagnostics.ProbeResult
import dev.everts.adbwifi.diagnostics.localNetworkBlocked
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.persistence.AdbWifiSettings
import dev.everts.adbwifi.persistence.PairedDevicesState
import dev.everts.adbwifi.ui.notifications.AdbWifiNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Raíz de composición: une gateway, persistencia, ajustes y notificaciones en un [AdbWifiController].
 * Punto de entrada de la UI. Es de aplicación, no de proyecto: el servidor adb y las conexiones son
 * de la máquina, y así hay un solo ciclo de reconexión aunque haya varios proyectos abiertos.
 */
@Service(Service.Level.APP)
class AdbWifiService(private val scope: CoroutineScope) {

    private var cachedGateway: CliAdbGateway? = null
    private var cachedTimeouts: AdbTimeouts? = null

    /** Descubrimiento por Bonjour en macOS; complementa al de adb, que puede dejar de ver dispositivos. */
    private val bonjour: DnsSdBrowser? = DnsSdBrowser.forThisMachine()

    val controller = AdbWifiController(
        scope = scope,
        gatewayProvider = ::assistedGateway,
        store = PairedDevicesState.getInstance(),
        settings = { AdbWifiSettings.getInstance().controllerSettings() },
        notifier = AdbWifiNotifications,
        probes = ControllerProbes(
            rejectedSerials = { AdbServerLog.recentRejections() },
            reachable = { JvmNetworkProbe.tcpConnect(it, REACHABILITY_TIMEOUT) == ProbeResult.OPEN },
            pairingFailure = AdbServerLog::currentPairingFailure,
            localNetworkBlocked = {
                if (SystemInfo.isMac) JvmNetworkProbe.localNetworkBlocked(REACHABILITY_TIMEOUT) else false
            },
            adbNetworkBlocked = {
                val router = if (SystemInfo.isMac) JvmNetworkProbe.defaultGateway() else null
                router?.let { currentGateway().cannotReachLocalNetwork(AdbEndpoint(it, ROUTER_PROBE_PORT)) }
            },
        ),
    )

    val diagnostics = DiagnosticsRunner(
        gatewayProvider = ::currentGateway,
        bonjourServices = bonjour?.let { browser -> { browser.services() } },
        network = JvmNetworkProbe,
        rejectedSerials = { AdbServerLog.recentRejections() },
        isMac = SystemInfo.isMac,
        windowsNetwork = if (SystemInfo.isWindows) {
            { PowerShellWindowsNetwork.status(runCatching { AdbLocator.locate(null) }.getOrNull()) }
        } else {
            null
        },
    )

    /** Lanza trabajo en el scope del servicio (se cancela al descargar el plugin o cerrar la IDE). */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    /** Gateway de adb con los servicios mDNS de Bonjour añadidos (si hay Bonjour). */
    private fun assistedGateway(): AdbGateway {
        val adb = currentGateway()
        val browser = bonjour ?: return adb
        return BonjourAssistedGateway(adb, browser::services)
    }

    /** Busca adb la primera vez y lo vuelve a crear si cambian los timeouts en Settings. */
    @Synchronized
    private fun currentGateway(): AdbGateway {
        val timeouts = AdbWifiSettings.getInstance().timeouts()
        val current = cachedGateway
        if (current != null && timeouts == cachedTimeouts) return current
        return CliAdbGateway(AdbLocator.locate(null), timeouts).also {
            cachedGateway = it
            cachedTimeouts = timeouts
        }
    }

    companion object {
        private val REACHABILITY_TIMEOUT = 1500.milliseconds

        /** Puerto de adb clásico: casi ningún router lo tiene abierto, así que responde "refused" al instante. */
        private const val ROUTER_PROBE_PORT = 5555

        fun getInstance(): AdbWifiService = service()
    }
}
