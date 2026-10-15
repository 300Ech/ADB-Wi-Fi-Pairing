package dev.everts.adbwifi

import dev.everts.adbwifi.diagnostics.Finding
import dev.everts.adbwifi.diagnostics.Fix
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.EntryStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.reader

/** Los bundles en inglés y español tienen las mismas claves y cubren todos los enums que se muestran. */
class AdbWifiBundleTest {

    // Se leen del código fuente: el jar del plugin lo carga la IDE de test, no el classpath del test.
    private fun load(name: String) = Properties().apply {
        Path.of("src/main/resources/messages/$name.properties").reader(Charsets.UTF_8).use(::load)
    }

    private val english = load("AdbWifiBundle")
    private val spanish = load("AdbWifiBundle_es")

    @Test
    fun `both languages have the same keys`() {
        assertEquals(english.stringPropertyNames().sorted(), spanish.stringPropertyNames().sorted())
    }

    @Test
    fun `no ampersands`() {
        // `&` es marca de mnemónico en el bundle y otra vez en las etiquetas del UI DSL: desaparece o se
        // descoloca ("IP address  port"). Se escribe "and"/"y" y ">" en vez de entidades HTML.
        val offending = (english.entries + spanish.entries).filter { '&' in it.value.toString() }
        assertTrue(offending.isEmpty(), "Sin & en: ${offending.map { it.key }}")
    }

    @Test
    fun `every enum shown in the ui has a text`() {
        val required = AdbFailureReason.entries.map { "failure.${it.name}" } +
            EntryStatus.entries.map { "status.${it.name}" } +
            Finding.entries.flatMap { listOf("diagnostic.${it.name}.title", "diagnostic.${it.name}.detail") } +
            Fix.entries.map { "diagnostic.fix.${it.name}" }
        val missing = required.filterNot(english::containsKey)
        assertTrue(missing.isEmpty(), "Faltan: $missing")
    }
}
