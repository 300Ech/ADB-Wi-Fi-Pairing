package dev.everts.adbwifi.domain

/** Estado de una fila de la lista de dispositivos, de más a menos útil. */
enum class EntryStatus {
    /** Conectado por Wi-Fi y listo. */
    CONNECTED,

    /** Conectado por USB (candidato para el modo `tcpip`). */
    USB,

    /** adb tiene el transporte pero aún no está listo (offline, connecting, authorizing). */
    CONNECTING,

    /** El teléfono no aceptó la clave de esta máquina; hay que aceptar el diálogo o volver a emparejar. */
    UNAUTHORIZED,

    /** Anuncia su puerto de conexión por mDNS pero no está conectado. */
    AVAILABLE,

    /** Tiene abierto el diálogo "Vincular con código" en el teléfono. */
    PAIRING,

    /** Emparejado pero no aparece en la red: depuración inalámbrica apagada u otra red. */
    NOT_SEEN,
}

/**
 * Una fila de la lista: lo que se sabe de un dispositivo uniendo lo emparejado, lo conectado y lo
 * descubierto por mDNS.
 *
 * @param key identidad estable de la fila: serial del dispositivo si se conoce, si no el serial de transporte.
 */
data class DeviceEntry(
    val key: String,
    val name: String,
    val status: EntryStatus,
    val endpoint: AdbEndpoint?,
    val paired: PairedDevice? = null,
    val device: Device? = null,
    val connectService: MdnsService? = null,
    val pairingService: MdnsService? = null,
) {
    val isConnected: Boolean get() = device != null && status != EntryStatus.UNAUTHORIZED
}

/** Datos que el plugin averigua de un transporte conectado con `getprop` (serial real y modelo legible). */
data class DeviceIdentity(val serialNumber: String?, val model: String?)
