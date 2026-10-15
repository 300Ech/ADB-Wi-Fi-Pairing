package dev.everts.adbwifi.adb.parsers

/** Parser de `adb disconnect HOST:PUERTO`. */
object DisconnectOutputParser {

    /**
     * `true` si adb cerró la conexión (`disconnected X`), `false` si no existía
     * (`error: no such device 'X'`) y `null` ante cualquier otra salida.
     */
    fun parse(stdout: String, stderr: String): Boolean? {
        val lines = meaningfulLines(stdout, stderr)
        return when {
            lines.any { it.startsWith("disconnected ") } -> true
            lines.any { "no such device" in it } -> false
            else -> null
        }
    }
}
