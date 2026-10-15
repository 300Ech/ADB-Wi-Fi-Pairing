package dev.everts.adbwifi.adb

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsStatus
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import kotlinx.coroutines.flow.Flow

/**
 * Operaciones de adb que necesita el plugin, independientes de cómo se hable con adb
 * (ejecutable del SDK hoy, adblib más adelante).
 *
 * Los fallos esperables para el usuario (código incorrecto, puerto cerrado...) se devuelven como
 * resultados. Los problemas de infraestructura (adb no encontrado, adb que no responde, salida
 * inesperada) se lanzan como [AdbException].
 */
@Suppress("TooManyFunctions") // una función por comando de adb
interface AdbGateway {

    /** `adb pair HOST:PUERTO CODIGO`. Un timeout se informa como fallo con causa `TIMED_OUT`. */
    suspend fun pair(endpoint: AdbEndpoint, code: PairingCode): PairingResult

    /** `adb connect HOST:PUERTO`. Un timeout se informa como fallo con causa `TIMED_OUT`. */
    suspend fun connect(endpoint: AdbEndpoint): ConnectResult

    /** `adb disconnect HOST:PUERTO`. Devuelve `false` si no había conexión con esa dirección. */
    suspend fun disconnect(endpoint: AdbEndpoint): Boolean

    /** `adb devices -l`. */
    suspend fun devices(): List<Device>

    /** `adb mdns check`: si el servidor adb tiene descubrimiento mDNS y con qué backend. */
    suspend fun mdnsCheck(): MdnsStatus

    /** `adb mdns services`: instantánea de los servicios adb anunciados en la red local. */
    suspend fun mdnsServices(): List<MdnsService>

    /**
     * Servicios mDNS observados en el tiempo. Emite la lista completa al empezar y cada vez que cambia.
     * Un fallo de adb termina el flujo con [AdbException].
     */
    fun trackMdnsServices(): Flow<List<MdnsService>>

    /** `adb -s SERIAL tcpip PUERTO`: reinicia adbd escuchando TCP sin TLS (modo clásico). */
    suspend fun tcpip(serial: String, port: Int)

    /** `adb version`: versión de platform-tools, p. ej. `37.0.1-15733141`. */
    suspend fun version(): String

    /** `adb -s SERIAL shell getprop NOMBRE`. Devuelve `null` si la propiedad no existe. */
    suspend fun getProp(serial: String, property: String): String?

    /** IP del dispositivo en la Wi-Fi según `adb -s SERIAL shell ip route`, o `null` si no tiene. */
    suspend fun wifiAddress(serial: String): String?

    /**
     * `adb kill-server` y `adb start-server`. Cierra todas las conexiones; [environment] se añade al
     * entorno del nuevo servidor (p. ej. `ADB_MDNS=0`).
     */
    suspend fun restartServer(environment: Map<String, String> = emptyMap())
}
