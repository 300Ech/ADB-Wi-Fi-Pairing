package dev.everts.adbwifi.adb.parsers

/** Parser de `adb version`: devuelve `37.0.1-15733141` de la línea `Version 37.0.1-15733141`. */
object VersionParser {

    private val VERSION = Regex("""^Version (\S+)""")

    fun parse(stdout: String): String? =
        meaningfulLines(stdout).firstNotNullOfOrNull { VERSION.find(it)?.groupValues?.get(1) }
}

/** Parser de `adb shell ip route`: la IP con la que el teléfono está en la Wi-Fi (`src` de `wlan*`). */
object IpRouteParser {

    private val ROUTE = Regex("""\bdev (\S+)\b.*\bsrc (\d{1,3}(?:\.\d{1,3}){3})\b""")

    fun parse(stdout: String): String? {
        val routes = meaningfulLines(stdout).mapNotNull { ROUTE.find(it)?.destructured?.let { (dev, ip) -> dev to ip } }
        return (routes.firstOrNull { (dev, _) -> dev.startsWith("wlan") } ?: routes.firstOrNull())?.second
    }
}
