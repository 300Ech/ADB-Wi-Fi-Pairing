package dev.everts.adbwifi.domain

/**
 * Dirección `host:puerto` de un servicio adb en la red (pairing o connect).
 *
 * Se usa en el emparejamiento manual, cuando el usuario escribe lo que muestra
 * el teléfono en "Depuración inalámbrica" sin depender de mDNS.
 */
data class AdbEndpoint(val host: String, val port: Int) {

    init {
        require(host.isNotBlank()) { "host vacío" }
        require(port in PORT_RANGE) { "puerto fuera de rango: $port" }
    }

    /** Formato que esperan `adb pair` y `adb connect`. IPv6 va entre corchetes. */
    override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"

    companion object {
        private val PORT_RANGE = 1..65_535

        /**
         * Interpreta `192.168.1.20:37123` o `[fe80::1]:37123`, tolerando espacios alrededor.
         * Devuelve `null` si el texto no es una dirección válida.
         */
        fun parse(text: String): AdbEndpoint? {
            val (host, portText) = splitHostAndPort(text.trim()) ?: return null
            val port = portText.toIntOrNull()?.takeIf { it in PORT_RANGE }
            val validHost = host.isNotBlank() && host.none(Char::isWhitespace)
            return if (port != null && validHost) AdbEndpoint(host, port) else null
        }

        /** Separa `host` y `puerto`. Un IPv6 sin corchetes es ambiguo y se rechaza. */
        private fun splitHostAndPort(text: String): Pair<String, String>? =
            if (text.startsWith('[')) {
                val close = text.indexOf("]:")
                if (close < 0) null else text.substring(1, close) to text.substring(close + 2)
            } else {
                val separator = text.lastIndexOf(':')
                val singleColon = separator > 0 && text.indexOf(':') == separator
                if (singleColon) text.substring(0, separator) to text.substring(separator + 1) else null
            }
    }
}
