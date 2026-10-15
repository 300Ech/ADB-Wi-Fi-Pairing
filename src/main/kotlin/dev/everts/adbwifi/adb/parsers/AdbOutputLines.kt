package dev.everts.adbwifi.adb.parsers

/**
 * Líneas con contenido de una salida de adb, sin espacios finales ni los avisos de arranque del
 * servidor (`* daemon not running; starting now...`), que adb mezcla con la salida del comando.
 */
internal fun meaningfulLines(vararg outputs: String): List<String> =
    outputs.asSequence()
        .flatMap { it.lineSequence() }
        .map { it.trimEnd() }
        .filter { it.isNotBlank() && !it.startsWith("* ") }
        .toList()

/** Mensaje de adb para mostrar en el detalle de un error: todas sus líneas útiles, stdout primero. */
internal fun adbMessage(stdout: String, stderr: String): String =
    meaningfulLines(stdout, stderr).joinToString("\n")
