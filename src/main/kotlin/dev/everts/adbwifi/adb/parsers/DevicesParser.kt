package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.DeviceState

/**
 * Parser de `adb devices` y `adb devices -l`.
 *
 * Formato largo de adb 37: `%-22s %s` (serial y estado) seguido de pares `clave:valor`
 * (`usb:`, `product:`, `model:`, `device:`) y siempre `transport_id:` al final. El estado
 * `no permissions (...)` incluye una explicación con espacios antes de las propiedades.
 */
object DevicesParser {

    private const val HEADER = "List of devices attached"
    private val WHITESPACE = Regex("""\s+""")
    private val PROPERTY_KEYS = setOf("usb", "product", "model", "device", "transport_id")

    fun parse(stdout: String): List<Device> =
        meaningfulLines(stdout)
            .filterNot { it.startsWith(HEADER) }
            .mapNotNull(::parseLine)

    private fun parseLine(line: String): Device? {
        val tokens = line.trim().split(WHITESPACE)
        if (tokens.size < 2) return null
        val state = DeviceState.fromAdb(tokens.drop(1).joinToString(" "))
        val firstProperty = tokens.indices.firstOrNull { it >= 2 && propertyKey(tokens[it]) != null } ?: tokens.size
        val properties = tokens.subList(firstProperty, tokens.size)
            .mapNotNull { token -> propertyKey(token)?.let { key -> key to token.substringAfter(':') } }
            .toMap()
        return Device(
            serial = tokens[0],
            state = state,
            model = properties["model"],
            product = properties["product"],
            deviceName = properties["device"],
            transportId = properties["transport_id"]?.toLongOrNull(),
        )
    }

    private fun propertyKey(token: String): String? =
        token.substringBefore(':', missingDelimiterValue = "").takeIf { it in PROPERTY_KEYS }
}
