package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType

/**
 * Parser de `adb mdns services`: una cabecera y una línea por servicio con tres columnas
 * separadas por tabulador: nombre de instancia, tipo de servicio y `ip:puerto`.
 * Se ignoran los tipos de servicio que no son de adb y las líneas mal formadas.
 */
object MdnsServicesParser {

    private const val HEADER = "List of discovered mdns services"
    private const val COLUMNS = 3

    fun parse(stdout: String): List<MdnsService> =
        meaningfulLines(stdout)
            .filterNot { it.startsWith(HEADER) }
            .mapNotNull(::parseLine)

    private fun parseLine(line: String): MdnsService? {
        val columns = line.split('\t').map(String::trim).filter(String::isNotEmpty)
        if (columns.size != COLUMNS) return null
        val (instance, typeName, address) = columns
        val type = MdnsServiceType.fromDnsName(typeName)
        val endpoint = parseAddress(address)
        return if (type != null && endpoint != null) MdnsService(instance, type, endpoint) else null
    }

    /** Acepta `ip:puerto`, `[ipv6]:puerto` y también un IPv6 sin corchetes (el puerto va tras el último `:`). */
    private fun parseAddress(address: String): AdbEndpoint? =
        AdbEndpoint.parse(address) ?: run {
            val separator = address.lastIndexOf(':')
            val port = address.substring(separator + 1).toIntOrNull()
            if (separator > 0 && port != null) AdbEndpoint.parse("[${address.substring(0, separator)}]:$port") else null
        }
}
