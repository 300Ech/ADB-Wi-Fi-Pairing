package dev.everts.adbwifi.diagnostics

import dev.everts.adbwifi.adb.parsers.FailureClassifier
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.AdbInstanceName
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Lectura del log del servidor adb (`$TMPDIR/adb.<uid>.log`). Cuando un teléfono ya no reconoce la
 * clave de esta máquina, adb reintenta la conexión automática cada pocos segundos y solo deja rastro
 * aquí: `SSL_peek failed [SSLV3_ALERT_CERTIFICATE_UNKNOWN]` seguido de `kicking transport <serial>`.
 */
object AdbServerLog {

    private val LINE = Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d)\.\d+\s+\d+\s+(\d+) \S\s+adb\s*: (.*)$""")
    private val KICK = Regex("""kicking transport \S+ (\S+)""")
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val REJECTED = "CERTIFICATE_UNKNOWN"
    private const val TAIL_BYTES = 256L * 1024

    /**
     * Último rechazo del certificado del host por serial de dispositivo según [log]. El log no trae
     * año; se usa [year] (el actual).
     */
    fun rejections(log: String, year: Int): Map<String, LocalDateTime> {
        val rejectingThreads = mutableSetOf<String>()
        val result = mutableMapOf<String, LocalDateTime>()
        for (match in log.lineSequence().mapNotNull { LINE.find(it) }) {
            val (time, thread, message) = match.destructured
            if (REJECTED in message) {
                rejectingThreads += thread
            } else if (rejectingThreads.remove(thread)) {
                val serial = KICK.find(message)?.groupValues?.get(1)?.let(AdbInstanceName::serialOf)
                val at = runCatching { LocalDateTime.parse("$year-$time", TIMESTAMP) }.getOrNull()
                if (serial != null && at != null) result[serial] = at
            }
        }
        return result
    }

    /**
     * Causa real de un `adb pair` fallido. El cliente solo dice `protocol fault`, pero el servidor deja
     * escrito el motivo: `failed to connect to 'IP:PUERTO': No route to host` o `Failed to decrypt`
     * (código o contraseña incorrectos). Solo se miran líneas desde [since].
     */
    fun pairingFailure(log: String, endpoint: AdbEndpoint, since: LocalDateTime): AdbFailureReason? {
        var reason: AdbFailureReason? = null
        val target = "failed to connect to '$endpoint'"
        val recent = log.lineSequence()
            .mapNotNull { LINE.find(it)?.destructured }
            .filter { (time, _, _) ->
                val at = runCatching { LocalDateTime.parse("${since.year}-$time", TIMESTAMP) }.getOrNull()
                at != null && !at.isBefore(since.withNano(0))
            }
        for ((_, _, message) in recent) {
            reason = when {
                target in message -> FailureClassifier.classify(message.substringAfter(target))
                "decrypt" in message -> AdbFailureReason.WRONG_PAIRING_CODE
                else -> reason
            }
        }
        return reason
    }

    /** [pairingFailure] sobre el log actual del servidor adb. */
    fun currentPairingFailure(endpoint: AdbEndpoint, since: LocalDateTime): AdbFailureReason? =
        pairingFailure(find()?.let(::readTail).orEmpty(), endpoint, since)

    /** Seriales rechazados en los últimos [window] según el log actual del servidor adb. */
    fun recentRejections(
        window: Duration = Duration.ofMinutes(2),
        now: LocalDateTime = LocalDateTime.now(),
    ): Set<String> {
        val log = find()?.let(::readTail).orEmpty()
        return rejections(log, now.year).filterValues { it.isAfter(now.minus(window)) }.keys
    }

    /** El log más reciente en el directorio temporal, o `null` si no hay. */
    fun find(tempDir: Path = Path.of(System.getProperty("java.io.tmpdir"))): Path? =
        runCatching { tempDir.listDirectoryEntries("adb*.log") }.getOrDefault(emptyList())
            .filter { it.isRegularFile() }
            .maxByOrNull { it.getLastModifiedTime() }

    /** Los últimos 256 KB del log: suficiente para los intentos recientes y barato de leer. */
    fun readTail(path: Path): String {
        if (!Files.isReadable(path)) return ""
        RandomAccessFile(path.toFile(), "r").use { file ->
            val start = (file.length() - TAIL_BYTES).coerceAtLeast(0)
            val bytes = ByteArray((file.length() - start).toInt())
            file.seek(start)
            file.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
