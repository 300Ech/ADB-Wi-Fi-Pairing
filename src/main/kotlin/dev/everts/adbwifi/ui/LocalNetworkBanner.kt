package dev.everts.adbwifi.ui

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import java.io.IOException

/**
 * Aviso de que macOS no deja a Android Studio usar la red local. Se muestra antes de que el usuario
 * intente nada, como un paso de configuración único, no como un fallo del plugin: sin ese permiso
 * ningún componente de Android Studio (ni su propio diálogo de emparejamiento) ve los teléfonos.
 */
object LocalNetworkBanner {

    private const val SETTINGS_URL = "x-apple.systempreferences:com.apple.preference.security?Privacy_LocalNetwork"

    /** @param onCheckAgain acción "Comprobar de nuevo"; `null` para no mostrarla. */
    fun create(onCheckAgain: (() -> Unit)? = null): InlineBanner =
        InlineBanner(AdbWifiBundle.message("banner.localNetwork.text"), EditorNotificationPanel.Status.Warning).apply {
            addAction(AdbWifiBundle.message("banner.localNetwork.open")) { openSettings() }
            onCheckAgain?.let { addAction(AdbWifiBundle.message("banner.localNetwork.check"), it) }
        }

    /**
     * El permiso ya está, pero macOS solo se lo da a los procesos que Android Studio lance tras reiniciarse
     * (adb incluido). Es el único paso que el plugin no puede hacer solo; se ofrece con un botón.
     */
    fun createRestart(): InlineBanner =
        InlineBanner(AdbWifiBundle.message("banner.restart.text"), EditorNotificationPanel.Status.Warning).apply {
            addAction(AdbWifiBundle.message("banner.restart.action")) { restartIde() }
        }

    fun restartIde() = ApplicationManager.getApplication().invokeLater { ApplicationManager.getApplication().restart() }

    /** Abre Ajustes del Sistema en Privacidad y seguridad > Red local. */
    fun openSettings() {
        AdbWifiService.getInstance().launch {
            try {
                GeneralCommandLine("open", SETTINGS_URL).createProcess()
            } catch (e: ExecutionException) {
                thisLogger().warn("Could not open Local Network settings", e)
            } catch (e: IOException) {
                thisLogger().warn("Could not open Local Network settings", e)
            }
        }
    }
}
