package dev.everts.adbwifi.adb

/** Lectura de las salidas reales de adb guardadas en `src/test/resources/fixtures/adb/`. */
object AdbFixtures {

    fun stdout(case: String): String = read("$case.stdout.txt") ?: ""

    fun stderr(case: String): String = read("$case.stderr.txt") ?: ""

    private fun read(fileName: String): String? =
        javaClass.getResourceAsStream("/fixtures/adb/$fileName")?.use { it.readBytes().toString(Charsets.UTF_8) }
}
