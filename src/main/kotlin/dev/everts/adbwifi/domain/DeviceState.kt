package dev.everts.adbwifi.domain

/** Estado de un dispositivo según la segunda columna de `adb devices`. */
enum class DeviceState(private val adbName: String) {
    DEVICE("device"),
    OFFLINE("offline"),
    UNAUTHORIZED("unauthorized"),
    AUTHORIZING("authorizing"),
    CONNECTING("connecting"),
    NO_PERMISSIONS("no permissions"),
    BOOTLOADER("bootloader"),
    RECOVERY("recovery"),
    RESCUE("rescue"),
    SIDELOAD("sideload"),
    HOST("host"),
    DETACHED("detached"),
    UNKNOWN("unknown"),
    ;

    /** `true` solo cuando adb puede ejecutar comandos en el dispositivo. */
    val isOnline: Boolean get() = this == DEVICE

    companion object {
        /**
         * Traduce el texto que imprime adb. `no permissions (...)` lleva una explicación detrás,
         * por eso se compara por prefijo. Lo desconocido se conserva como [UNKNOWN].
         */
        fun fromAdb(text: String): DeviceState {
            val normalized = text.trim()
            return entries.firstOrNull { it != UNKNOWN && normalized.startsWith(it.adbName) } ?: UNKNOWN
        }
    }
}
