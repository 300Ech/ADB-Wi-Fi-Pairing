package dev.everts.adbwifi.ui.toolwindow

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import dev.everts.adbwifi.ui.diagnostics.DiagnosticsPanel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** Smoke test: el plugin.xml registra el tool window y la fábrica crea su contenido. */
@TestApplication
@RunInEdt(writeIntent = true)
class AdbWifiToolWindowFactoryTest {

    private val project = projectFixture()

    @Test
    fun `tool window is registered in plugin xml`() {
        val extension = ToolWindowEP.EP_NAME.extensionList.firstOrNull { it.id == TOOL_WINDOW_ID }
        assertNotNull(extension, "No se encontró el tool window '$TOOL_WINDOW_ID'")
        assertEquals(AdbWifiToolWindowFactory::class.java.name, extension!!.factoryClass)
    }

    @Test
    fun `factory adds devices and diagnostics tabs`() {
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project.get())
        AdbWifiToolWindowFactory().createToolWindowContent(project.get(), toolWindow)
        assertEquals(
            listOf(DevicesPanel::class.java, DiagnosticsPanel::class.java),
            toolWindow.contentManager.contents.map { it.component.javaClass },
        )
    }

    private companion object {
        const val TOOL_WINDOW_ID = "ADB Wi-Fi"
    }
}
