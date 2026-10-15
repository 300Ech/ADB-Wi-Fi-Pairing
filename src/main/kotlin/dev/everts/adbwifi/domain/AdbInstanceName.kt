package dev.everts.adbwifi.domain

/**
 * Nombres que adbd deriva del serial del dispositivo: la instancia mDNS `adb-<serial>-<sufijo>`, el
 * `guid` que imprime `adb pair` (mismo formato) y el serial de transporte que adb asigna al
 * conectarse solo por mDNS (`adb-<serial>-<sufijo>._adb-tls-connect._tcp`).
 */
object AdbInstanceName {

    private val INSTANCE_NAME = Regex("""adb-(.+)-[A-Za-z0-9]{6}(?:\._adb-tls-connect\._tcp\.?)?""")

    /** Serial del dispositivo contenido en [name], o `null` si [name] no tiene ese formato. */
    fun serialOf(name: String): String? = INSTANCE_NAME.matchEntire(name.trim())?.groupValues?.get(1)
}
