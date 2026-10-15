package dev.everts.adbwifi.domain

/**
 * Dispositivo que el servidor adb conoce, tal como lo lista `adb devices -l`.
 *
 * El [serial] de un dispositivo por Wi-Fi es `host:puerto` si se conectó con `adb connect`, o el
 * nombre de instancia mDNS (`adb-<serial>-<sufijo>._adb-tls-connect._tcp`) si adb lo conectó solo.
 */
data class Device(
    val serial: String,
    val state: DeviceState,
    val model: String? = null,
    val product: String? = null,
    val deviceName: String? = null,
    val transportId: Long? = null,
) {
    /** Dirección de red si el dispositivo se conectó con `adb connect`; `null` para USB y emuladores. */
    val endpoint: AdbEndpoint? get() = AdbEndpoint.parse(serial)

    /** `true` para conexiones TCP/IP, tanto manuales (`host:puerto`) como automáticas por mDNS. */
    val isWireless: Boolean get() = endpoint != null || serial.endsWith(MDNS_CONNECT_SUFFIX)

    private companion object {
        const val MDNS_CONNECT_SUFFIX = "._adb-tls-connect._tcp"
    }
}
