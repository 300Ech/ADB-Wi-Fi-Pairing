package dev.everts.adbwifi.adb.bonjour

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsServiceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Descubre servicios adb con Bonjour (mDNSResponder de macOS) mediante `dns-sd`, sin pasar por adb.
 *
 * Con adb 37.0.1 su descubrimiento propio ("adb discovery") puede dejar de ver dispositivos mientras
 * macOS sí los ve: es la causa de que el diálogo de Studio no encuentre nada. El plugin une ambas fuentes.
 *
 * Cada consulta hace un `dns-sd -B` corto por tipo; la dirección de cada instancia se resuelve una vez
 * (`dns-sd -L` + resolución del host `.local`) y se guarda unos segundos.
 */
class DnsSdBrowser(
    private val dnsSd: Path = DEFAULT_PATH,
    private val browseWindow: Duration = 1200.milliseconds,
    private val lookupTimeout: Duration = 3.seconds,
    private val cacheTtl: Duration = 30.seconds,
    private val resolveHost: (String) -> String? = ::ipv4Of,
    private val now: () -> Duration = { System.nanoTime().nanoseconds },
    private val environment: Map<String, String> = emptyMap(),
) {
    private data class Resolved(val endpoint: AdbEndpoint, val at: Duration)

    private val cache = ConcurrentHashMap<DnsSdParsers.Instance, Resolved>()

    suspend fun services(): List<MdnsService> = coroutineScope {
        val alive = MdnsServiceType.entries.map { type -> async { browse(type) } }.awaitAll().flatten().toSet()
        cache.keys.retainAll(alive)
        alive.map { instance -> async { endpointOf(instance)?.let { MdnsService(instance.name, instance.type, it) } } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun browse(type: MdnsServiceType): Set<DnsSdParsers.Instance> =
        DnsSdParsers.browse(runFor(listOf("-B", type.dnsName, "local."), browseWindow) { false })

    private suspend fun endpointOf(instance: DnsSdParsers.Instance): AdbEndpoint? {
        val cached = cache[instance]?.takeIf { now() - it.at < cacheTtl }?.endpoint
        return cached ?: resolve(instance)?.also { cache[instance] = Resolved(it, now()) }
    }

    private suspend fun resolve(instance: DnsSdParsers.Instance): AdbEndpoint? {
        val output = runFor(listOf("-L", instance.name, instance.type.dnsName, "local."), lookupTimeout) {
            "can be reached at" in it
        }
        val (host, port) = DnsSdParsers.lookup(output) ?: return null
        return withContext(Dispatchers.IO) { resolveHost(host) }?.let { AdbEndpoint(it, port) }
    }

    /**
     * Ejecuta `dns-sd` (que no termina solo) hasta que una línea cumple [stopWhen] o pasa [duration],
     * y devuelve lo que imprimió.
     */
    private suspend fun runFor(args: List<String>, duration: Duration, stopWhen: (String) -> Boolean): String =
        withContext(Dispatchers.IO) {
            val handler = try {
                val commandLine = GeneralCommandLine(listOf(dnsSd.toString()) + args)
                    .withCharset(Charsets.UTF_8)
                    .withEnvironment(environment)
                OSProcessHandler(commandLine)
            } catch (_: ExecutionException) {
                return@withContext ""
            }
            val output = StringBuilder()
            val done = CompletableDeferred<Unit>()
            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType != ProcessOutputTypes.STDOUT) return
                    synchronized(output) { output.append(event.text) }
                    if (stopWhen(event.text)) done.complete(Unit)
                }

                override fun processTerminated(event: ProcessEvent) {
                    done.complete(Unit)
                }
            })
            handler.startNotify()
            try {
                withTimeoutOrNull(duration) { done.await() }
            } finally {
                handler.destroyProcess()
            }
            synchronized(output) { output.toString() }
        }

    companion object {
        val DEFAULT_PATH: Path = Path.of("/usr/bin/dns-sd")

        /** `dns-sd` de macOS, o `null` en otros sistemas. */
        fun forThisMachine(): DnsSdBrowser? = DEFAULT_PATH.takeIf { Files.isExecutable(it) }?.let { DnsSdBrowser(it) }

        /** Los hosts `.local` los resuelve el resolver del sistema a través de mDNSResponder. */
        fun ipv4Of(host: String): String? = try {
            InetAddress.getAllByName(host.removeSuffix(".")).firstOrNull { it is Inet4Address }?.hostAddress
        } catch (_: UnknownHostException) {
            null
        }
    }
}
