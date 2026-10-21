package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbFailureReason

/** Deduce la causa de un fallo de `adb pair` o `adb connect` a partir de su mensaje. */
object FailureClassifier {

    /**
     * `failed to connect to HOST:PUERTO` sin causa detrás: la conexión TCP se abrió pero el registro
     * del transporte falló. Capturado con un teléfono que rechazaba el certificado TLS del host.
     */
    private val BARE_CONNECT_FAILURE = Regex("""failed to connect to \S+""")

    /**
     * En Windows adb informa los errores de socket con el texto del sistema, traducido al idioma de
     * Windows, seguido del código Winsock entre paréntesis: `... denegó expresamente dicha conexión. (10061)`.
     * El código es lo único que no depende del idioma.
     */
    private val WINSOCK_CODE = Regex("""\((\d{5})\)""")

    private val WINSOCK_CODES = mapOf(
        "10061" to AdbFailureReason.CONNECTION_REFUSED, // WSAECONNREFUSED
        "10060" to AdbFailureReason.TIMED_OUT, // WSAETIMEDOUT
        "10065" to AdbFailureReason.NO_ROUTE_TO_HOST, // WSAEHOSTUNREACH
        "10051" to AdbFailureReason.NO_ROUTE_TO_HOST, // WSAENETUNREACH
        "10064" to AdbFailureReason.HOST_DOWN, // WSAEHOSTDOWN
    )

    private val PATTERNS = listOf(
        "connection refused" to AdbFailureReason.CONNECTION_REFUSED,
        "no route to host" to AdbFailureReason.NO_ROUTE_TO_HOST,
        "network is unreachable" to AdbFailureReason.NO_ROUTE_TO_HOST,
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
        return WINSOCK_CODE.findAll(message).firstNotNullOfOrNull { WINSOCK_CODES[it.groupValues[1]] }
            ?: PATTERNS.firstOrNull { (pattern, _) -> pattern in lowercase }?.second
            ?: AdbFailureReason.AUTHENTICATION_FAILED.takeIf {
                meaningfulLines(message).any { BARE_CONNECT_FAILURE.matches(it.trim()) }
            }
            ?: AdbFailureReason.UNKNOWN
    }
}
