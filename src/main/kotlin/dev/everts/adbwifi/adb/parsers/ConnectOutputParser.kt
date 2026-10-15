package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.ConnectResult

/**
 * Parser de `adb connect`. adb sale con código 0 también cuando falla, así que se decide por el
 * texto: `connected to X`, `already connected to X` o cualquier otro mensaje como fallo.
 */
object ConnectOutputParser {

    private val CONNECTED = Regex("""(already )?connected to (\S+)""")

    fun parse(stdout: String, stderr: String, requested: AdbEndpoint): ConnectResult {
        val connected = meaningfulLines(stdout, stderr).firstNotNullOfOrNull { CONNECTED.matchEntire(it.trim()) }
        return if (connected != null) {
            val endpoint = AdbEndpoint.parse(connected.groupValues[2]) ?: requested
            ConnectResult.Connected(endpoint, alreadyConnected = connected.groupValues[1].isNotEmpty())
        } else {
            val message = adbMessage(stdout, stderr)
            ConnectResult.Failed(FailureClassifier.classify(message), message)
        }
    }
}
