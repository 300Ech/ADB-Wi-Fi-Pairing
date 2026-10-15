package dev.everts.adbwifi.adb.bonjour

import dev.everts.adbwifi.adb.AdbGateway
import dev.everts.adbwifi.domain.MdnsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [AdbGateway] cuyos servicios mDNS son la unión de los que ve adb y los que ve Bonjour. El resto de
 * comandos van directos a [delegate].
 */
class BonjourAssistedGateway(
    private val delegate: AdbGateway,
    private val bonjour: suspend () -> List<MdnsService>,
    private val pollInterval: Duration = 2.seconds,
) : AdbGateway by delegate {

    override suspend fun mdnsServices(): List<MdnsService> {
        val fromAdb = delegate.mdnsServices()
        val fromBonjour = try {
            bonjour()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: RuntimeException) {
            // Bonjour es una ayuda: si falla, se sigue con lo que ve adb.
            emptyList()
        }
        return merge(fromAdb, fromBonjour)
    }

    override fun trackMdnsServices(): Flow<List<MdnsService>> =
        flow {
            while (true) {
                emit(mdnsServices())
                delay(pollInterval)
            }
        }.distinctUntilChanged()

    companion object {
        /** Una entrada por (tipo, instancia); si ambas fuentes la ven, manda la de adb. */
        fun merge(fromAdb: List<MdnsService>, fromBonjour: List<MdnsService>): List<MdnsService> =
            (fromAdb + fromBonjour).distinctBy { it.type to it.instanceName }
    }
}
