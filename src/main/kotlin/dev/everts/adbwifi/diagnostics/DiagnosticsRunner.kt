package dev.everts.adbwifi.diagnostics

import dev.everts.adbwifi.adb.AdbException
import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.adb.AdbNotFoundException
import dev.everts.adbwifi.adb.bonjour.BonjourAssistedGateway
import dev.everts.adbwifi.application.AdbWifiState
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsStatus
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ejecuta los chequeos del panel de diagnóstico (adb, mDNS, permiso Red local, red y puerto) y
 * devuelve un resultado por hallazgo. No toca la configuración de nada: las reparaciones las lanza el
 * usuario desde la UI.
 *
 * @param bonjourServices servicios que ve Bonjour (macOS), para detectar que el descubrimiento de adb se
 *   quedó ciego; `null` si no hay Bonjour.
 * @param isMac el permiso "Red local" solo existe en macOS; en otros sistemas `No route to host` es de red.
 * @param windowsNetwork tipo de red y Firewall de Windows; `null` fuera de Windows.
 */
class DiagnosticsRunner(
    private val gatewayProvider: () -> AdbGateway,
    private val bonjourServices: (suspend () -> List<MdnsService>)? = null,
    private val network: NetworkProbe,
    private val rejectedSerials: () -> Set<String>,
    private val isMac: Boolean,
    private val windowsNetwork: (suspend () -> WindowsNetworkStatus?)? = null,
    private val probeTimeout: Duration = 3.seconds,
) {

    suspend fun run(state: AdbWifiState, target: AdbEndpoint?): List<CheckResult> = buildList {
        val services = checkAdb(this)
        checkDevices(this, state, services)
        windowsNetwork?.let { checkWindows(this, it) }
        val probeTarget = target ?: defaultTarget(state)
        val studioBlocked = isMac && network.localNetworkBlocked(probeTimeout) == true
        if (!studioBlocked && state.restartRequired) {
            add(CheckResult(Finding.STUDIO_RESTART_REQUIRED, Severity.ERROR, fixes = listOf(Fix.RESTART_STUDIO)))
        }
        if (studioBlocked) {
            // Las pruebas de red salen de Android Studio: con el permiso denegado no dicen nada del teléfono.
            val fixes = listOf(Fix.OPEN_LOCAL_NETWORK_SETTINGS)
            add(CheckResult(Finding.STUDIO_NO_LOCAL_NETWORK, Severity.ERROR, fixes = fixes))
        } else if (probeTarget == null) {
            add(CheckResult(Finding.NO_TARGET, Severity.INFO))
        } else {
            checkNetwork(this, probeTarget)
        }
    }

    /** adb, versión y mDNS. Devuelve los servicios anunciados o `null` si no se pudieron leer. */
    private suspend fun checkAdb(results: MutableList<CheckResult>): List<MdnsService>? {
        val gateway = try {
            gatewayProvider().also { results += versionCheck(it.version()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdbException) {
            results += adbFailure(e)
            null
        } ?: return null
        return try {
            when (val mdns = gateway.mdnsCheck()) {
                is MdnsStatus.Available -> {
                    results += CheckResult(Finding.MDNS_OK, Severity.OK, listOf(mdns.daemonVersion))
                    announced(results, gateway.mdnsServices())
                }
                is MdnsStatus.Unavailable -> {
                    results += CheckResult(
                        Finding.MDNS_UNAVAILABLE,
                        Severity.WARNING,
                        listOf(mdns.message),
                        listOf(Fix.RESTART_ADB_WITH_MDNS),
                    )
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdbException) {
            results += adbFailure(e)
            null
        }
    }

    /**
     * Compara lo que anuncia la red según adb y según Bonjour. adb 37 puede dejar de ver dispositivos
     * tras horas de servidor arrancado; un servidor nuevo vuelve a verlos.
     */
    private suspend fun announced(results: MutableList<CheckResult>, fromAdb: List<MdnsService>): List<MdnsService> {
        val fromBonjour = bonjourServices?.let { query ->
            try {
                query()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
                emptyList()
            }
        }.orEmpty()
        val missed = fromBonjour.filter { b -> fromAdb.none { it.type == b.type && it.instanceName == b.instanceName } }
        if (missed.isNotEmpty()) {
            results += CheckResult(
                Finding.ADB_DISCOVERY_BLIND,
                Severity.WARNING,
                listOf(missed.size.toString()),
                listOf(Fix.RESTART_ADB),
            )
        }
        val all = BonjourAssistedGateway.merge(fromAdb, fromBonjour)
        results += if (all.isEmpty()) {
            CheckResult(Finding.MDNS_NOTHING_ANNOUNCED, Severity.INFO)
        } else {
            CheckResult(Finding.MDNS_ANNOUNCING, Severity.OK, listOf(all.size.toString()))
        }
        return all
    }

    private fun adbFailure(e: AdbException): CheckResult =
        if (e is AdbNotFoundException) {
            CheckResult(Finding.ADB_MISSING, Severity.ERROR, fixes = listOf(Fix.OPEN_SDK_MANAGER))
        } else {
            CheckResult(Finding.ADB_FAILED, Severity.ERROR, listOf(e.message.orEmpty()), listOf(Fix.RESTART_ADB))
        }

    private fun versionCheck(version: String): CheckResult {
        val major = version.substringBefore('.').toIntOrNull()
        return if (major != null && major < MIN_WIRELESS_MAJOR) {
            CheckResult(Finding.ADB_TOO_OLD, Severity.ERROR, listOf(version), listOf(Fix.OPEN_SDK_MANAGER))
        } else {
            CheckResult(Finding.ADB_OK, Severity.OK, listOf(version))
        }
    }

    /** Dispositivos emparejados que no anuncian nada o que rechazan la clave de esta máquina. */
    private fun checkDevices(results: MutableList<CheckResult>, state: AdbWifiState, services: List<MdnsService>?) {
        val rejected = runCatching(rejectedSerials).getOrDefault(emptySet()) +
            state.failures.filterValues { it == AdbFailureReason.AUTHENTICATION_FAILED }.keys
        state.entries
            .filter { it.key in rejected && !it.isConnected }
            .mapTo(results) {
                CheckResult(Finding.CERTIFICATE_REJECTED, Severity.ERROR, listOf(it.name), listOf(Fix.PAIR_AGAIN))
            }
        if (services != null) {
            val silent = state.entries.filter { it.paired != null && it.status == EntryStatus.NOT_SEEN }
            if (silent.isNotEmpty()) {
                val names = silent.joinToString { it.name }
                results += CheckResult(Finding.WIRELESS_DEBUGGING_OFF, Severity.INFO, listOf(names))
            }
        }
    }

    /** Tipo de red y Firewall de Windows ([WindowsNetworkStatus.checks]); nada si no se pudo consultar. */
    private suspend fun checkWindows(results: MutableList<CheckResult>, query: suspend () -> WindowsNetworkStatus?) {
        val status = try {
            query()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            null
        }
        status?.let { results += it.checks() }
    }

    private suspend fun checkNetwork(results: MutableList<CheckResult>, target: AdbEndpoint) {
        val networks = network.localNetworks()
        if (networks.isEmpty()) {
            results += CheckResult(Finding.NO_LOCAL_NETWORK, Severity.ERROR)
            return
        }
        networks.firstOrNull { it.looksLikeVpn }?.let {
            results += CheckResult(Finding.VPN_ACTIVE, Severity.INFO, listOf(it.interfaceName))
        }
        val sameNetwork = networks.firstOrNull { it.contains(target.host) }
        if (LocalNetwork.ipv4ToInt(target.host) != null) {
            results += if (sameNetwork != null) {
                CheckResult(Finding.SAME_SUBNET, Severity.OK, listOf(target.host, describe(sameNetwork)))
            } else {
                CheckResult(
                    Finding.DIFFERENT_SUBNET,
                    Severity.WARNING,
                    listOf(target.host, networks.filterNot { it.looksLikeVpn }.joinToString { describe(it) }),
                )
            }
        }
        results += when (network.tcpConnect(target, probeTimeout)) {
            ProbeResult.OPEN -> CheckResult(Finding.PORT_OPEN, Severity.OK, listOf(target.toString()))
            ProbeResult.REFUSED -> CheckResult(Finding.PORT_REFUSED, Severity.WARNING, listOf(target.toString()))
            ProbeResult.TIMEOUT -> CheckResult(Finding.PORT_TIMEOUT, Severity.WARNING, listOf(target.toString()))
            ProbeResult.HOST_DOWN, ProbeResult.UNKNOWN_HOST ->
                CheckResult(Finding.HOST_DOWN, Severity.WARNING, listOf(target.host))
            ProbeResult.NO_ROUTE -> if (isMac && sameNetwork != null) {
                CheckResult(
                    Finding.LOCAL_NETWORK_BLOCKED,
                    Severity.ERROR,
                    listOf(target.toString()),
                    listOf(Fix.OPEN_LOCAL_NETWORK_SETTINGS),
                )
            } else {
                CheckResult(Finding.HOST_DOWN, Severity.WARNING, listOf(target.host))
            }
        }
    }

    private fun describe(network: LocalNetwork) =
        "${network.address}/${network.prefixLength} (${network.interfaceName})"

    /** Sin dirección del usuario se prueba el primer dispositivo con dirección conocida y sin conectar. */
    private fun defaultTarget(state: AdbWifiState): AdbEndpoint? =
        state.entries.firstOrNull { !it.isConnected && it.endpoint != null && it.status != EntryStatus.USB }?.endpoint
            ?: state.entries.firstOrNull { it.status == EntryStatus.CONNECTED }?.endpoint

    private companion object {
        /** El emparejamiento inalámbrico llegó con platform-tools 30. */
        const val MIN_WIRELESS_MAJOR = 30
    }
}
