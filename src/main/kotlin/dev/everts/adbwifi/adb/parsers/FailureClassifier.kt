package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbFailureReason

/** Deduce la causa de un fallo de `adb pair` o `adb connect` a partir de su mensaje. */
object FailureClassifier {

    /**
     * `failed to connect to HOST:PUERTO` sin causa detrás: la conexión TCP se abrió pero el registro
     * del transporte falló. Capturado con un teléfono que rechazaba el certificado TLS del host.
     */
    private val BARE_CONNECT_FAILURE = Regex("""failed to connect to \S+""")

    private val PATTERNS = listOf(
        "connection refused" to AdbFailureReason.CONNECTION_REFUSED,
        "no route to host" to AdbFailureReason.NO_ROUTE_TO_HOST,
        "host is down" to AdbFailureReason.HOST_DOWN,
        "timed out" to AdbFailureReason.TIMED_OUT,
        "failed to resolve host" to AdbFailureReason.HOST_NOT_RESOLVED,
        "protocol fault" to AdbFailureReason.PROTOCOL_FAULT,
        "wrong password" to AdbFailureReason.WRONG_PAIRING_CODE,
        "pairing client" to AdbFailureReason.PAIRING_CLIENT_FAILED,
        "failed to authenticate" to AdbFailureReason.AUTHENTICATION_FAILED,
    )

    fun classify(message: String): AdbFailureReason {
        val lowercase = message.lowercase()
        return PATTERNS.firstOrNull { (pattern, _) -> pattern in lowercase }?.second
            ?: AdbFailureReason.AUTHENTICATION_FAILED.takeIf {
                meaningfulLines(message).any { BARE_CONNECT_FAILURE.matches(it.trim()) }
            }
            ?: AdbFailureReason.UNKNOWN
    }
}
