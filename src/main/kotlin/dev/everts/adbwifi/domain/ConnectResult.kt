package dev.everts.adbwifi.domain

/** Resultado de `adb connect HOST:PUERTO`. */
sealed interface ConnectResult {

    /** Conectado. [alreadyConnected] es `true` si adb ya tenía esa conexión abierta. */
    data class Connected(val endpoint: AdbEndpoint, val alreadyConnected: Boolean) : ConnectResult

    /** Fallo; [message] es el texto original de adb para mostrarlo tal cual en el detalle. */
    data class Failed(val reason: AdbFailureReason, val message: String) : ConnectResult
}
