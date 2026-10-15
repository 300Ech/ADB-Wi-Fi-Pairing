package dev.everts.adbwifi.domain

/**
 * Dispositivo emparejado con esta máquina que el plugin recuerda entre sesiones.
 *
 * @param id serial del dispositivo (el de `ro.serialno`, que adbd usa en sus nombres mDNS). Si adb no
 *   lo informó al emparejar, es la IP; se corrige en cuanto el dispositivo se conecta.
 * @param name nombre para mostrar (modelo o el que elija el usuario).
 * @param lastEndpoint última dirección de conexión conocida. El puerto cambia en cada sesión de
 *   "Depuración inalámbrica", así que solo sirve como último recurso.
 * @param autoReconnect si el plugin debe reconectarlo cuando reaparece en la red.
 */
data class PairedDevice(
    val id: String,
    val name: String,
    val lastEndpoint: AdbEndpoint? = null,
    val autoReconnect: Boolean = true,
)
