package dev.everts.adbwifi.application

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.PairedDevice
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** Almacén de dispositivos emparejados. Lo implementa `persistence/` con un `PersistentStateComponent`. */
interface PairedDevicesStore {
    val devices: List<PairedDevice>

    fun update(transform: (List<PairedDevice>) -> List<PairedDevice>)
}

/** Preferencias que usa el controlador; se leen en cada ciclo para aplicar cambios sin reiniciar. */
data class ControllerSettings(
    val autoReconnect: Boolean = true,
    val pollInterval: Duration = 5.seconds,
)

/**
 * Reloj y comprobaciones del entorno que usa el controlador.
 *
 * @param rejectedSerials seriales cuyo teléfono rechazó hace poco la clave de esta máquina (según el
 *   log del servidor adb, que reintenta solo y no avisa).
 * @param pairingFailure causa real de un `adb pair` fallido desde un instante, leída del log del servidor
 *   (el cliente solo informa `protocol fault`).
 * @param reachable comprobación rápida (TCP) antes de reintentar una dirección guardada que no se anuncia
 *   por mDNS; evita esperar el timeout de `adb connect` por cada teléfono apagado.
 */
class ControllerProbes(
    val now: () -> Duration = { System.nanoTime().nanoseconds },
    val rejectedSerials: () -> Set<String> = { emptySet() },
    val reachable: suspend (AdbEndpoint) -> Boolean = { true },
    val pairingFailure: (AdbEndpoint, LocalDateTime) -> AdbFailureReason? = { _, _ -> null },
    val wallClock: () -> LocalDateTime = LocalDateTime::now,
)

/** Hechos que merecen avisar al usuario aunque no esté mirando el tool window. */
sealed interface AdbWifiEvent {
    val deviceName: String

    /** El plugin volvió a conectar un dispositivo emparejado. */
    data class Reconnected(override val deviceName: String, val endpoint: AdbEndpoint) : AdbWifiEvent

    /** El teléfono rechazó la clave de esta máquina: hay que volver a emparejar. */
    data class NeedsRepair(override val deviceName: String, val endpoint: AdbEndpoint) : AdbWifiEvent

    /** Un teléfono abrió "Vincular con código" en esta red y espera el código. */
    data class PairingAvailable(override val deviceName: String, val endpoint: AdbEndpoint) : AdbWifiEvent

    /** Un dispositivo conectado desapareció de adb y de la red (depuración inalámbrica apagada u otra red). */
    data class Lost(override val deviceName: String) : AdbWifiEvent
}

/** Destino de [AdbWifiEvent]; la UI lo implementa con notificaciones de la IDE. */
fun interface AdbWifiNotifier {
    fun notify(event: AdbWifiEvent)
}
