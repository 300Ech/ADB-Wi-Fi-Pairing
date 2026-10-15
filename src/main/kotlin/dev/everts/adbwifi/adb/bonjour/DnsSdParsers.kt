package dev.everts.adbwifi.adb.bonjour

import dev.everts.adbwifi.domain.MdnsServiceType

/** Parsers de la salida de `dns-sd` (macOS), el cliente de Bonjour/mDNSResponder. */
object DnsSdParsers {

    /** `14:32:40.206  Add        2  14 local.               _adb-tls-pairing._tcp. studio-VISAKqPGtD` */
    private val BROWSE = Regex("""^\S+\s+(Add|Rmv)\s+\d+\s+\d+\s+\S+\s+(\S+)\s+(.+)$""")

    /** `… can be reached at Android_84GKWQPE.local.:34389 (interface 14)` */
    private val LOOKUP = Regex("""can be reached at (\S+):(\d+)""")

    data class Instance(val type: MdnsServiceType, val name: String)

    /** Instancias vivas al final de una salida de `dns-sd -B`: las añadidas menos las retiradas. */
    fun browse(output: String): Set<Instance> {
        val alive = linkedSetOf<Instance>()
        for (match in output.lineSequence().mapNotNull { BROWSE.find(it.trimEnd()) }) {
            val (action, typeName, name) = match.destructured
            val type = MdnsServiceType.fromDnsName(typeName) ?: continue
            val instance = Instance(type, name.trim())
            if (action == "Add") alive += instance else alive -= instance
        }
        return alive
    }

    /** Host y puerto de una salida de `dns-sd -L`, o `null` si aún no se resolvió. */
    fun lookup(output: String): Pair<String, Int>? =
        LOOKUP.find(output)?.destructured?.let { (host, port) -> port.toIntOrNull()?.let { host to it } }
}
