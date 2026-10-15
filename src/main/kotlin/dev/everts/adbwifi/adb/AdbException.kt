package dev.everts.adbwifi.adb

import java.nio.file.Path
import kotlin.time.Duration

/**
 * Problema al ejecutar adb que no es un resultado normal del comando.
 * El mensaje es técnico (para el log); la UI elige su propio texto según el subtipo.
 */
open class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** No se encontró un ejecutable de adb en ninguna de las rutas probadas. */
class AdbNotFoundException(val searched: List<Path>) :
    AdbException("adb executable not found; searched: ${searched.joinToString()}")

/** adb no se pudo lanzar (permisos, binario corrupto...). */
class AdbLaunchException(val command: List<String>, cause: Throwable) :
    AdbException("could not start `${command.joinToString(" ")}`: ${cause.message}", cause)

/** adb no terminó dentro del tiempo permitido; el proceso cliente se mató. */
class AdbTimeoutException(val command: List<String>, val timeout: Duration) :
    AdbException("`${command.joinToString(" ")}` did not finish within $timeout")

/** adb terminó con una salida que no corresponde a ningún resultado conocido del comando. */
class AdbCommandException(val command: List<String>, val exitCode: Int, val output: String) :
    AdbException("`${command.joinToString(" ")}` failed with exit code $exitCode: $output")
