package dev.everts.adbwifi.ui

import com.intellij.openapi.actionSystem.DataKey
import dev.everts.adbwifi.domain.DeviceEntry

object AdbWifiDataKeys {
    /** Fila seleccionada en la lista de dispositivos del tool window. */
    val SELECTED_ENTRY: DataKey<DeviceEntry> = DataKey.create("dev.everts.adbwifi.selectedEntry")
}
