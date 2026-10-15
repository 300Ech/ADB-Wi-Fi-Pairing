package dev.everts.adbwifi.domain

/** Resultado de `adb pair HOST:PUERTO CODIGO`. */
sealed interface PairingResult {

    /** Emparejado. [guid] identifica la clave del dispositivo (`adb-<serial>-<sufijo>`) si adb la informa. */
    data class Paired(val endpoint: AdbEndpoint, val guid: String?) : PairingResult

    /** Fallo; [message] es el texto original de adb para mostrarlo tal cual en el detalle. */
    data class Failed(val reason: AdbFailureReason, val message: String) : PairingResult
}
