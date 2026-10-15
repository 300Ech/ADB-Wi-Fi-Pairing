package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.MdnsStatus

/**
 * Parser de `adb mdns check`. Con descubrimiento activo imprime `mdns daemon version [<backend>]`;
 * si no, `ERROR: <motivo>` por stdout y con código de salida 0.
 */
object MdnsCheckParser {

    private val VERSION = Regex("""mdns daemon version \[(.*)]""")
    private const val ERROR_PREFIX = "ERROR:"

    fun parse(stdout: String, stderr: String = ""): MdnsStatus {
        val lines = meaningfulLines(stdout, stderr)
        val version = lines.firstNotNullOfOrNull { VERSION.find(it)?.groupValues?.get(1) }
        return if (version != null) {
            MdnsStatus.Available(version.trim())
        } else {
            val message = lines.firstOrNull { it.startsWith(ERROR_PREFIX) } ?: lines.joinToString("\n")
            MdnsStatus.Unavailable(message.removePrefix(ERROR_PREFIX).trim())
        }
    }
}
