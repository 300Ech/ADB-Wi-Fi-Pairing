package dev.everts.adbwifi.diagnostics

import dev.everts.adbwifi.domain.AdbEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Red IPv4 de una interfaz local, p. ej. `en0` 192.168.0.21/24. */
data class LocalNetwork(val interfaceName: String, val address: String, val prefixLength: Int) {

    /** `true` si [ip] está en esta red. Solo IPv4. */
    fun contains(ip: String): Boolean {
        val target = ipv4ToInt(ip)
        val own = ipv4ToInt(address)
        val mask = if (prefixLength == 0) 0 else (-1 shl (IPV4_BITS - prefixLength))
        return target != null && own != null && own and mask == target and mask
    }

    /** Interfaces de túnel típicas de VPN en macOS y Linux. */
    val looksLikeVpn: Boolean get() = VPN_PREFIXES.any { interfaceName.startsWith(it) }

    companion object {
        private const val IPV4_BITS = 32
        private const val OCTET_BITS = 8
        private const val OCTETS = 4
        private const val OCTET_MAX = 255
        private val VPN_PREFIXES = listOf("utun", "tun", "ppp", "ipsec", "wg")

        fun ipv4ToInt(ip: String): Int? {
            val parts = ip.split('.').map { it.toIntOrNull() }
            val valid = parts.size == OCTETS && parts.all { it != null && it in 0..OCTET_MAX }
            return if (valid) parts.fold(0) { acc, part -> (acc shl OCTET_BITS) or part!! } else null
        }
    }
}

/** Resultado de abrir una conexión TCP a un puerto del teléfono. */
enum class ProbeResult { OPEN, REFUSED, TIMEOUT, NO_ROUTE, HOST_DOWN, UNKNOWN_HOST }

/** Acceso a la red de esta máquina; interfaz para poder probar los chequeos sin red. */
interface NetworkProbe {
    fun localNetworks(): List<LocalNetwork>

    suspend fun tcpConnect(endpoint: AdbEndpoint, timeout: Duration): ProbeResult

    /** IP del router (puerta de enlace por defecto), o `null` si no se puede saber. */
    fun defaultGateway(): String? = null
}

/** Parser de `route -n get default` (macOS): la línea `gateway: 192.168.0.1`. */
object RouteParser {
    private val GATEWAY = Regex("""^\s*gateway:\s*(\d{1,3}(?:\.\d{1,3}){3})\s*$""", RegexOption.MULTILINE)

    fun gateway(output: String): String? = GATEWAY.find(output)?.groupValues?.get(1)
}

/**
 * Si macOS impide a Android Studio usar la red local. Se prueba una conexión TCP al router: con el
 * permiso "Red local" denegado falla al instante con `No route to host`; con permiso responde o rechaza.
 * Devuelve `null` si no hay router conocido o el resultado no es concluyente.
 */
suspend fun NetworkProbe.localNetworkBlocked(timeout: Duration): Boolean? {
    val gateway = defaultGateway() ?: return null
    return when (tcpConnect(AdbEndpoint(gateway, GATEWAY_PROBE_PORT), timeout)) {
        ProbeResult.NO_ROUTE -> true
        ProbeResult.OPEN, ProbeResult.REFUSED -> false
        else -> null
    }
}

/** Puerto HTTP del router; da igual que esté cerrado: un rechazo también prueba que hay ruta. */
private const val GATEWAY_PROBE_PORT = 80

/**
 * Implementación con `java.net`. La conexión sale del proceso de Android Studio, que es la app a la
 * que macOS concede o niega el permiso "Red local", igual que al adb que lanza.
 */
object JvmNetworkProbe : NetworkProbe {

    private const val ROUTE_TIMEOUT_SECONDS = 2L

    override fun defaultGateway(): String? {
        val route = Path.of("/sbin/route").takeIf { Files.isExecutable(it) } ?: return null
        return try {
            val process = ProcessBuilder(route.toString(), "-n", "get", "default").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(ROUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            RouteParser.gateway(output)
        } catch (_: IOException) {
            null
        }
    }

    override fun localNetworks(): List<LocalNetwork> =
        NetworkInterface.networkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic ->
                nic.interfaceAddresses
                    .filter { it.address is Inet4Address }
                    .map { LocalNetwork(nic.name, it.address.hostAddress, it.networkPrefixLength.toInt()) }
            }

    override suspend fun tcpConnect(endpoint: AdbEndpoint, timeout: Duration): ProbeResult =
        runInterruptible(Dispatchers.IO) {
            try {
                val address = InetSocketAddress(InetAddress.getByName(endpoint.host), endpoint.port)
                Socket().use { it.connect(address, timeout.inWholeMilliseconds.toInt()) }
                ProbeResult.OPEN
            } catch (_: NoRouteToHostException) {
                ProbeResult.NO_ROUTE
            } catch (e: ConnectException) {
                val hostDown = e.message.orEmpty().contains("Host is down", ignoreCase = true)
                if (hostDown) ProbeResult.HOST_DOWN else ProbeResult.REFUSED
            } catch (_: SocketTimeoutException) {
                ProbeResult.TIMEOUT
            } catch (_: java.net.UnknownHostException) {
                ProbeResult.UNKNOWN_HOST
            } catch (e: IOException) {
                val noRoute = e.message.orEmpty().contains("No route", ignoreCase = true)
                if (noRoute) ProbeResult.NO_ROUTE else ProbeResult.TIMEOUT
            }
        }
}
