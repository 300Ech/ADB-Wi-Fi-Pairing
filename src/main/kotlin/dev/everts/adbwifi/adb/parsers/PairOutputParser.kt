package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.PairingResult

/**
 * Parser de `adb pair`. El éxito es `Successfully paired to HOST:PUERTO [guid=...]`; los fallos
 * llegan por stdout (`Failed: ...`, exit 0) o por stderr (`error: protocol fault ...`, exit 1),
 * así que se decide solo por el texto.
 */
object PairOutputParser {

    private val SUCCESS = Regex("""Successfully paired to (\S+)(?: \[guid=([^\]]*)])?""")

    fun parse(stdout: String, stderr: String, requested: AdbEndpoint): PairingResult {
        val success = meaningfulLines(stdout, stderr).firstNotNullOfOrNull { SUCCESS.find(it) }
        return if (success != null) {
            val endpoint = AdbEndpoint.parse(success.groupValues[1]) ?: requested
            PairingResult.Paired(endpoint, success.groupValues[2].ifEmpty { null })
        } else {
            val message = adbMessage(stdout, stderr)
            PairingResult.Failed(FailureClassifier.classify(message), message)
        }
    }
}
