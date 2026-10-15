package dev.everts.adbwifi.domain

/** Resultado de `adb mdns check`: si el servidor adb tiene descubrimiento mDNS y con qué backend. */
sealed interface MdnsStatus {

    /**
     * Descubrimiento activo. [daemonVersion] es lo que adb pone entre corchetes, por ejemplo
     * `adb discovery 0.0.0` (backend propio de adb) o `Bonjour 2200.0.8` (mDNSResponder de macOS).
     */
    data class Available(val daemonVersion: String) : MdnsStatus

    /** Descubrimiento desactivado o no disponible; [message] es el texto de adb sin el prefijo `ERROR:`. */
    data class Unavailable(val message: String) : MdnsStatus
}
