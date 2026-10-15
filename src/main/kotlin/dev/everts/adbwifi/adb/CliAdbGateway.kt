package dev.everts.adbwifi.adb

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import dev.everts.adbwifi.adb.parsers.ConnectOutputParser
import dev.everts.adbwifi.adb.parsers.DevicesParser
import dev.everts.adbwifi.adb.parsers.DisconnectOutputParser
import dev.everts.adbwifi.adb.parsers.IpRouteParser
import dev.everts.adbwifi.adb.parsers.MdnsCheckParser
import dev.everts.adbwifi.adb.parsers.MdnsServicesParser
import dev.everts.adbwifi.adb.parsers.PairOutputParser
import dev.everts.adbwifi.adb.parsers.VersionParser
import dev.everts.adbwifi.adb.parsers.adbMessage
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.Device
import dev.everts.adbwifi.domain.MdnsService
import dev.everts.adbwifi.domain.MdnsStatus
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Tiempos máximos de espera por tipo de comando. */
data class AdbTimeouts(
    /** Comandos locales al servidor adb: `devices`, `mdns`, `disconnect`, `tcpip`. */
    val command: Duration = 10.seconds,
    /** `adb connect`. Sin límite, adb tarda 75 s en rendirse con una IP que no responde. */
    val connect: Duration = 20.seconds,
    /** `adb pair`, que incluye el intercambio SPAKE2 con el teléfono. */
    val pair: Duration = 30.seconds,
)

/**
 * [AdbGateway] que ejecuta el adb del SDK como proceso externo, un comando por llamada.
 *
 * Todo corre en [Dispatchers.IO]. Si la coroutine se cancela o se agota el timeout, el proceso
 * cliente de adb se destruye; el servidor adb sigue vivo.
 *
 * @param environment variables añadidas al entorno de adb (p. ej. `ADB_MDNS_OPENSCREEN`).
 * @param mdnsPollInterval cada cuánto [trackMdnsServices] vuelve a preguntar a adb.
 */
@Suppress("TooManyFunctions") // una función por comando de adb
class CliAdbGateway(
    private val adb: Path,
    private val timeouts: AdbTimeouts = AdbTimeouts(),
    private val environment: Map<String, String> = emptyMap(),
    private val mdnsPollInterval: Duration = 2.seconds,
) : AdbGateway {

    override suspend fun pair(endpoint: AdbEndpoint, code: PairingCode): PairingResult =
        try {
            val output = run(listOf("pair", endpoint.toString(), code.value), timeouts.pair, secret = code.value)
            PairOutputParser.parse(output.stdout, output.stderr, endpoint)
        } catch (e: AdbTimeoutException) {
            PairingResult.Failed(AdbFailureReason.TIMED_OUT, e.message.orEmpty())
        }

    override suspend fun connect(endpoint: AdbEndpoint): ConnectResult =
        try {
            val output = run(listOf("connect", endpoint.toString()), timeouts.connect)
            ConnectOutputParser.parse(output.stdout, output.stderr, endpoint)
        } catch (e: AdbTimeoutException) {
            ConnectResult.Failed(AdbFailureReason.TIMED_OUT, e.message.orEmpty())
        }

    override suspend fun disconnect(endpoint: AdbEndpoint): Boolean {
        val output = run(listOf("disconnect", endpoint.toString()), timeouts.command)
        return DisconnectOutputParser.parse(output.stdout, output.stderr) ?: throw output.failure()
    }

    override suspend fun devices(): List<Device> {
        val output = run(listOf("devices", "-l"), timeouts.command)
        if (output.exitCode != 0) throw output.failure()
        return DevicesParser.parse(output.stdout)
    }

    override suspend fun mdnsCheck(): MdnsStatus {
        val output = run(listOf("mdns", "check"), timeouts.command)
        return MdnsCheckParser.parse(output.stdout, output.stderr)
    }

    override suspend fun mdnsServices(): List<MdnsService> {
        val output = run(listOf("mdns", "services"), timeouts.command)
        if (output.exitCode != 0) throw output.failure()
        return MdnsServicesParser.parse(output.stdout)
    }

    /**
     * Sondea `adb mdns services`. `adb mdns track-services` existe pero solo emite formatos protobuf
     * sin delimitador fiable en modo texto; se podrá adoptar con fixtures que tengan varios cambios.
     */
    override fun trackMdnsServices(): Flow<List<MdnsService>> =
        flow {
            while (true) {
                emit(mdnsServices())
                delay(mdnsPollInterval)
            }
        }.distinctUntilChanged()

    override suspend fun tcpip(serial: String, port: Int) {
        require(port in 1..MAX_PORT) { "port out of range: $port" }
        val output = run(listOf("-s", serial, "tcpip", port.toString()), timeouts.command)
        if (output.exitCode != 0) throw output.failure()
    }

    override suspend fun version(): String {
        val output = run(listOf("version"), timeouts.command)
        return VersionParser.parse(output.stdout) ?: throw output.failure()
    }

    override suspend fun getProp(serial: String, property: String): String? {
        val output = run(listOf("-s", serial, "shell", "getprop", property), timeouts.command)
        if (output.exitCode != 0) throw output.failure()
        return output.stdout.trim().ifEmpty { null }
    }

    override suspend fun wifiAddress(serial: String): String? {
        val output = run(listOf("-s", serial, "shell", "ip", "route"), timeouts.command)
        if (output.exitCode != 0) throw output.failure()
        return IpRouteParser.parse(output.stdout)
    }

    override suspend fun restartServer(environment: Map<String, String>) {
        // kill-server sin servidor imprime "cannot connect to daemon" con exit 0: no es un error.
        run(listOf("kill-server"), timeouts.command)
        val output = run(listOf("start-server"), timeouts.command, extraEnvironment = environment)
        if (output.exitCode != 0) throw output.failure()
    }

    /**
     * Ejecuta adb y espera su salida sin bloquear un hilo más allá de los lectores del proceso.
     * [secret] se oculta en los mensajes de error y en el log.
     */
    private suspend fun run(
        args: List<String>,
        timeout: Duration,
        secret: String? = null,
        extraEnvironment: Map<String, String> = emptyMap(),
    ): CommandOutput {
        val command = listOf(adb.toString()) + args
        val displayed = command.map { if (it == secret) "******" else it }
        val commandLine = GeneralCommandLine(command)
            .withCharset(Charsets.UTF_8)
            .withEnvironment(environment + extraEnvironment)
        return withContext(Dispatchers.IO) {
            val handler = try {
                OSProcessHandler(commandLine)
            } catch (e: ExecutionException) {
                throw AdbLaunchException(displayed, e)
            }
            val output = ProcessOutput()
            val exitCode = CompletableDeferred<Int>()
            handler.addProcessListener(CapturingProcessAdapter(output))
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    exitCode.complete(event.exitCode)
                }
            })
            handler.startNotify()
            try {
                val code = withTimeoutOrNull(timeout) { exitCode.await() }
                    ?: throw AdbTimeoutException(displayed, timeout)
                CommandOutput(displayed, code, output.stdout, output.stderr)
            } finally {
                if (!handler.isProcessTerminated) handler.destroyProcess()
            }
        }
    }

    private class CommandOutput(val command: List<String>, val exitCode: Int, val stdout: String, val stderr: String) {
        fun failure() = AdbCommandException(command, exitCode, adbMessage(stdout, stderr))
    }

    companion object {
        private const val MAX_PORT = 65_535

        /** Gateway con el adb que usa Android Studio para [project] (o el de respaldo). */
        fun forProject(project: Project?, timeouts: AdbTimeouts = AdbTimeouts()): CliAdbGateway =
            CliAdbGateway(AdbLocator.locate(project), timeouts)
    }
}
