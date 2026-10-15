package dev.everts.adbwifi.ui

import com.intellij.openapi.util.text.StringUtil
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.application.PairOutcome
import dev.everts.adbwifi.application.PostPairing
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.domain.PairingResult
import org.jetbrains.annotations.Nls

/** Textos para los resultados de adb: qué pasó y qué hacer, sin perder el mensaje original de adb. */
object ResultMessages {

    @Nls
    fun advice(reason: AdbFailureReason): String = AdbWifiBundle.message("failure.${reason.name}")

    /** Explicación accionable seguida del mensaje literal de adb, para poder buscarlo o reportarlo. */
    @Nls
    fun failure(reason: AdbFailureReason, adbMessage: String): String =
        if (adbMessage.isBlank()) {
            advice(reason)
        } else {
            AdbWifiBundle.message("failure.withAdbMessage", advice(reason), StringUtil.escapeXmlEntities(adbMessage))
        }

    @Nls
    fun connect(result: ConnectResult): String = when (result) {
        is ConnectResult.Connected -> AdbWifiBundle.message("result.connected", result.endpoint.toString())
        is ConnectResult.Failed -> failure(result.reason, result.message)
    }

    @Nls
    fun pair(outcome: PairOutcome): String = when (val pairing = outcome.pairing) {
        is PairingResult.Failed -> failure(pairing.reason, pairing.message)
        is PairingResult.Paired -> when (val connection = outcome.connection) {
            is PostPairing.Connected, null -> AdbWifiBundle.message("result.paired.connected")
            is PostPairing.Failed -> AdbWifiBundle.message(
                "result.paired.connectFailed",
                failure(connection.result.reason, connection.result.message),
            )
            PostPairing.NotFound -> AdbWifiBundle.message("result.paired.notFound")
        }
    }

    @Nls
    fun status(status: EntryStatus): String = AdbWifiBundle.message("status.${status.name}")
}
