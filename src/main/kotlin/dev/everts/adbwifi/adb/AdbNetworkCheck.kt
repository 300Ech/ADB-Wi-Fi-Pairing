package dev.everts.adbwifi.adb

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult

/**
 * Si el servidor adb llega a la red local, preguntándole por [probe] (el router, en un puerto que no
 * suele estar abierto): `Connection refused` prueba que hay ruta; `No route to host` que macOS bloquea
 * al servidor. Devuelve `null` si el resultado no es concluyente.
 */
suspend fun AdbGateway.cannotReachLocalNetwork(probe: AdbEndpoint): Boolean? =
    when (val result = connect(probe)) {
        is ConnectResult.Connected -> {
            // El router aceptó la conexión (raro): hay red. Se cierra el transporte que adb abrió.
            disconnect(probe)
            false
        }
        is ConnectResult.Failed -> when (result.reason) {
            AdbFailureReason.NO_ROUTE_TO_HOST -> true
            AdbFailureReason.CONNECTION_REFUSED, AdbFailureReason.AUTHENTICATION_FAILED -> false
            else -> null
        }
    }
