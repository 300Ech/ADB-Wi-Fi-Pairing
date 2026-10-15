package dev.everts.adbwifi

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.everts.adbwifi.persistence.AdbWifiSettings
import dev.everts.adbwifi.persistence.PairedDevicesState

/**
 * Arranca la reconexión automática al abrir un proyecto si hay dispositivos que la tienen activa,
 * sin esperar a que el usuario abra el tool window.
 */
internal class AdbWifiStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val wanted = AdbWifiSettings.getInstance().state.autoReconnect &&
            PairedDevicesState.getInstance().devices.any { it.autoReconnect }
        if (wanted) AdbWifiService.getInstance().controller.start()
    }
}
