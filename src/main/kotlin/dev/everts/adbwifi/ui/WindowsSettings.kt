package dev.everts.adbwifi.ui

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import dev.everts.adbwifi.AdbWifiService
import java.io.IOException

/** Abre las pantallas de Windows que arreglan el tipo de red y el firewall. Fuera de Windows no hace nada. */
object WindowsSettings {

    /** Configuración > Red e Internet, desde donde se cambia el tipo de red de la conexión a Privada. */
    fun openNetworkSettings() = open("explorer.exe", "ms-settings:network-status")

    /** Panel de control > Firewall de Windows Defender > Permitir una aplicación a través del firewall. */
    fun openFirewallSettings() =
        open("control.exe", "/name", "Microsoft.WindowsFirewall", "/page", "pageConfigureApps")

    private fun open(vararg command: String) {
        if (!SystemInfo.isWindows) return
        AdbWifiService.getInstance().launch {
            try {
                GeneralCommandLine(*command).createProcess()
            } catch (e: ExecutionException) {
                thisLogger().warn("Could not open Windows settings", e)
            } catch (e: IOException) {
                thisLogger().warn("Could not open Windows settings", e)
            }
        }
    }
}
