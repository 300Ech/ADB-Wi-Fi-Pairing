package dev.everts.adbwifi.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Último intento de reconexión de un dispositivo; [failures] cuenta los fallos seguidos en [endpoint]. */
data class ReconnectAttempt(
    val endpoint: AdbEndpoint,
    val at: Duration,
    val failures: Int,
    val reason: AdbFailureReason?,
)

/**
 * Decide qué dispositivos emparejados intentar reconectar en este ciclo.
 *
 * - Si el dispositivo anuncia su puerto por mDNS, se usa ese (el puerto cambia en cada sesión).
 * - Si no aparece por mDNS, se prueba la última dirección conocida: sirve cuando se reinició el
 *   servidor adb pero el teléfono mantiene la sesión.
 * - Un dispositivo recién anunciado espera [AUTO_CONNECT_GRACE]: adb conecta solo los dispositivos
 *   conocidos por mDNS y, si el plugin se adelanta, Studio vería el mismo teléfono dos veces.
 * - Tras un fallo se espera con backoff exponencial. Un rechazo del certificado no se reintenta en la
 *   misma dirección: hace falta volver a emparejar.
 */
object ReconnectPlanner {

    val BASE_BACKOFF: Duration = 15.seconds
    val MAX_BACKOFF: Duration = 5.minutes
    val AUTO_CONNECT_GRACE: Duration = 10.seconds

    data class Target(val key: String, val endpoint: AdbEndpoint)

    /**
     * @param availableSince desde cuándo anuncia cada fila su puerto sin estar conectada.
     */
    fun plan(
        entries: List<DeviceEntry>,
        attempts: Map<String, ReconnectAttempt>,
        now: Duration,
        availableSince: Map<String, Duration>,
    ): List<Target> =
        entries.mapNotNull { entry ->
            val endpoint = candidateEndpoint(entry) ?: return@mapNotNull null
            val last = attempts[entry.key]
            val waited = entry.status != EntryStatus.AVAILABLE ||
                availableSince[entry.key]?.let { now - it >= AUTO_CONNECT_GRACE } == true
            val due = last == null || last.endpoint != endpoint || canRetry(last, now)
            Target(entry.key, endpoint).takeIf { waited && due }
        }

    private fun candidateEndpoint(entry: DeviceEntry): AdbEndpoint? {
        if (entry.paired?.autoReconnect != true) return null
        return when (entry.status) {
            EntryStatus.AVAILABLE -> entry.connectService?.endpoint
            EntryStatus.NOT_SEEN -> entry.paired.lastEndpoint
            else -> null
        }
    }

    private fun canRetry(last: ReconnectAttempt, now: Duration): Boolean =
        last.reason != AdbFailureReason.AUTHENTICATION_FAILED && now - last.at >= backoff(last.failures)

    fun backoff(failures: Int): Duration {
        if (failures <= 0) return Duration.ZERO
        val factor = 1L shl (failures - 1).coerceAtMost(MAX_SHIFT)
        return (BASE_BACKOFF * factor.toDouble()).coerceAtMost(MAX_BACKOFF)
    }

    private const val MAX_SHIFT = 10
}
