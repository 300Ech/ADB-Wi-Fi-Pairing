package dev.everts.adbwifi.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.ui.diagnostics.DiagnosticsPanel

/** Crea el tool window "ADB Wi-Fi": pestañas de dispositivos y de diagnóstico. */
internal class AdbWifiToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        val devices = DevicesPanel()
        val devicesContent = factory.createContent(devices, AdbWifiBundle.message("toolwindow.tab.devices"), false)
        Disposer.register(devicesContent, devices)
        toolWindow.contentManager.addContent(devicesContent)

        val diagnostics = DiagnosticsPanel(project)
        val diagnosticsContent =
            factory.createContent(diagnostics, AdbWifiBundle.message("toolwindow.tab.diagnostics"), false)
        Disposer.register(diagnosticsContent, diagnostics)
        toolWindow.contentManager.addContent(diagnosticsContent)
    }

    companion object {
        const val ID = "ADB Wi-Fi"

        /** Abre el tool window en la pestaña de diagnóstico y ejecuta los chequeos. */
        fun showDiagnostics(project: Project) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
            toolWindow.activate {
                val content = toolWindow.contentManager.contents.firstOrNull { it.component is DiagnosticsPanel }
                    ?: return@activate
                toolWindow.contentManager.setSelectedContent(content)
                (content.component as DiagnosticsPanel).run()
            }
        }
    }
}
