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
}

/**
 * Implementación con `java.net`. La conexión sale del proceso de Android Studio, que es la app a la
 * que macOS concede o niega el permiso "Red local", igual que al adb que lanza.
 */
object JvmNetworkProbe : NetworkProbe {

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
