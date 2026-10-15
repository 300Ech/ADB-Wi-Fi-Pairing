package dev.everts.adbwifi.domain

/** Tipos de servicio mDNS que anuncia adbd (ver `adb_mdns.h`). */
enum class MdnsServiceType(val dnsName: String) {
    /** Puerto de "Vincular con código" o QR; solo vive mientras el diálogo está abierto en el teléfono. */
    PAIRING("_adb-tls-pairing._tcp"),

    /** Puerto de conexión TLS de "Depuración inalámbrica"; cambia en cada sesión. */
    TLS_CONNECT("_adb-tls-connect._tcp"),

    /** adbd clásico tras `adb tcpip`, sin TLS. */
    LEGACY("_adb._tcp"),
    ;

    companion object {
        /** Acepta el nombre con o sin punto final (Bonjour lo añade, openscreen no). */
        fun fromDnsName(text: String): MdnsServiceType? {
            val normalized = text.trim().removeSuffix(".")
            return entries.firstOrNull { it.dnsName == normalized }
        }
    }
}

/** Servicio adb descubierto por mDNS (`adb mdns services`). */
data class MdnsService(
    val instanceName: String,
    val type: MdnsServiceType,
    val endpoint: AdbEndpoint,
) {
    /**
     * Serial del dispositivo, que adbd incluye en el nombre de instancia `adb-<serial>-<sufijo>`.
     * Permite reconocer un dispositivo emparejado aunque cambien su IP y su puerto.
     * Es `null` para nombres con otro formato, como los de emparejamiento por QR (`studio-...`).
     */
    val deviceSerial: String?
        get() = AdbInstanceName.serialOf(instanceName)
}
