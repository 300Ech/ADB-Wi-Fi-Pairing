package dev.everts.adbwifi.adb

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * `AdbLocator.locate` dentro de la IDE de test: la llamada al plugin Android enlaza y, sin SDK
 * configurado, cae a las rutas de respaldo. En CI sin SDK el resultado esperado es [AdbNotFoundException].
 */
@TestApplication
class AdbLocatorIdeTest {

    private val project = projectFixture()

    @Test
    fun `locate with and without project returns an executable or reports the searched paths`() {
        for (candidate in listOf(project.get(), null)) {
            try {
                val adb = AdbLocator.locate(candidate)
                assertTrue(Files.isExecutable(adb), "$adb no es ejecutable")
            } catch (e: AdbNotFoundException) {
                assertTrue(e.searched.isNotEmpty(), e.message)
            }
        }
    }
}
