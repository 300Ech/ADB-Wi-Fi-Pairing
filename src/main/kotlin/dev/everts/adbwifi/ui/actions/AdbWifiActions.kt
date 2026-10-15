package dev.everts.adbwifi.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.adb.AdbException
import dev.everts.adbwifi.application.PostPairing
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.DeviceEntry
import dev.everts.adbwifi.domain.EntryStatus
import dev.everts.adbwifi.ui.AdbWifiDataKeys
import dev.everts.adbwifi.ui.ResultMessages
import dev.everts.adbwifi.ui.notifications.AdbWifiNotifications
import dev.everts.adbwifi.ui.pairing.AdbOperationDialog
import dev.everts.adbwifi.ui.pairing.ConnectDialog
import dev.everts.adbwifi.ui.pairing.PairDialog
import dev.everts.adbwifi.ui.qr.QrPairingDialog
import dev.everts.adbwifi.ui.settings.AdbWifiConfigurable
import dev.everts.adbwifi.ui.toolwindow.AdbWifiToolWindowFactory

/** Ids de acciones y grupos registrados en `plugin.xml`. */
object AdbWifiActionIds {
    const val TOOLBAR = "AdbWifi.ToolWindowToolbar"
    const val DEVICE_POPUP = "AdbWifi.DevicePopup"
    const val PAIR_MANUALLY = "AdbWifi.PairManually"
    const val PAIR_WITH_QR = "AdbWifi.PairWithQr"
    const val PAIR_DISCOVERED = "AdbWifi.PairDiscovered"
    const val CONNECT = "AdbWifi.Connect"
}

/** Ejecuta una operación de adb en segundo plano y avisa solo si falla. */
internal fun runAdbOperation(project: Project?, operation: suspend () -> ConnectResult?) {
    AdbWifiService.getInstance().launch {
        try {
            val result = operation()
            if (result is ConnectResult.Failed) {
                AdbWifiNotifications.result(project, ResultMessages.connect(result), success = false)
            }
        } catch (e: AdbException) {
            AdbWifiNotifications.result(project, AdbOperationDialog.adbProblemText(e), false)
        }
    }
}

/** Muestra el diálogo de emparejar y avisa de cómo quedó la conexión posterior. */
internal fun showPairDialog(dialog: PairDialog, project: Project?) {
    if (!dialog.showAndGet()) return
    val outcome = dialog.result ?: return
    AdbWifiNotifications.result(project, ResultMessages.pair(outcome), outcome.connection is PostPairing.Connected)
}

/** Acción que actúa sobre la fila seleccionada; se oculta si no aplica. */
abstract class EntryAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val entry = e.getData(AdbWifiDataKeys.SELECTED_ENTRY)
        e.presentation.isEnabledAndVisible = entry != null && isApplicable(entry)
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(AdbWifiDataKeys.SELECTED_ENTRY)?.let { perform(e.project, it) }
    }

    protected abstract fun isApplicable(entry: DeviceEntry): Boolean

    protected abstract fun perform(project: Project?, entry: DeviceEntry)
}

class PairManuallyAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = showPairDialog(PairDialog(e.project), e.project)
}

class PairWithQrAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val dialog = QrPairingDialog(e.project)
        dialog.show()
        val outcome = dialog.outcome
        if (dialog.switchToCode) {
            showPairDialog(PairDialog(e.project), e.project)
        } else if (outcome != null) {
            val connected = outcome.connection is PostPairing.Connected
            AdbWifiNotifications.result(e.project, ResultMessages.pair(outcome), connected)
        }
    }
}

class ConnectManuallyAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ConnectDialog(e.project).show()
    }
}

class RefreshAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val service = AdbWifiService.getInstance()
        service.launch { service.controller.refresh() }
    }
}

class RestartAdbAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val confirmed = Messages.showYesNoDialog(
            e.project,
            AdbWifiBundle.message("restartAdb.confirm.message"),
            AdbWifiBundle.message("restartAdb.confirm.title"),
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (confirmed) runAdbOperation(e.project) { AdbWifiService.getInstance().controller.restartAdb(); null }
    }
}

class RunDiagnosticsAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        AdbWifiToolWindowFactory.showDiagnostics(e.project ?: return)
    }
}

class OpenSettingsAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, AdbWifiConfigurable::class.java)
    }
}

class ConnectAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) =
        entry.status in setOf(EntryStatus.AVAILABLE, EntryStatus.NOT_SEEN, EntryStatus.UNAUTHORIZED) &&
            (entry.connectService != null || entry.paired?.lastEndpoint != null)

    override fun perform(project: Project?, entry: DeviceEntry) {
        if (entry.connectService == null && entry.status == EntryStatus.NOT_SEEN) {
            // Sin mDNS el puerto guardado suele estar caducado: se ofrece corregirlo.
            ConnectDialog(project, entry.paired?.lastEndpoint).show()
        } else {
            runAdbOperation(project) { AdbWifiService.getInstance().controller.connect(entry) }
        }
    }
}

/**
 * Solo para conexiones `host:puerto`: `adb disconnect` interpreta su argumento como dirección de red y no
 * puede cerrar los transportes que adb abre solo por mDNS (`adb-<serial>-<sufijo>._adb-tls-connect._tcp`).
 */
class DisconnectAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) = entry.isConnected && entry.device?.endpoint != null

    override fun perform(project: Project?, entry: DeviceEntry) =
        runAdbOperation(project) { AdbWifiService.getInstance().controller.disconnect(entry); null }
}

class PairDiscoveredAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) = entry.pairingService != null

    override fun perform(project: Project?, entry: DeviceEntry) =
        showPairDialog(PairDialog(project, entry.pairingService?.endpoint, entry.name), project)
}

/** Para dispositivos cuyo teléfono rechaza la clave de esta máquina (autorización revocada o caducada). */
class PairAgainAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry): Boolean {
        val failures = AdbWifiService.getInstance().controller.state.value.failures
        return !entry.isConnected && failures[entry.key] == AdbFailureReason.AUTHENTICATION_FAILED
    }

    override fun perform(project: Project?, entry: DeviceEntry) =
        showPairDialog(PairDialog(project, entry.pairingService?.endpoint, entry.name), project)
}

class ToggleAutoReconnectAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val entry = e.getData(AdbWifiDataKeys.SELECTED_ENTRY)
        e.presentation.isEnabledAndVisible = entry != null && entry.status != EntryStatus.USB
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        e.getData(AdbWifiDataKeys.SELECTED_ENTRY)?.paired?.autoReconnect ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val entry = e.getData(AdbWifiDataKeys.SELECTED_ENTRY) ?: return
        AdbWifiService.getInstance().controller.setAutoReconnect(entry, state)
    }
}

class RenameAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) = entry.status != EntryStatus.USB

    override fun perform(project: Project?, entry: DeviceEntry) {
        val name = Messages.showInputDialog(
            project,
            AdbWifiBundle.message("rename.message"),
            AdbWifiBundle.message("rename.title"),
            null,
            entry.name,
            null,
        )
        if (!name.isNullOrBlank()) AdbWifiService.getInstance().controller.rename(entry, name)
    }
}

class ForgetAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) = entry.paired != null

    override fun perform(project: Project?, entry: DeviceEntry) {
        val confirmed = Messages.showYesNoDialog(
            project,
            AdbWifiBundle.message("forget.confirm.message", entry.name),
            AdbWifiBundle.message("forget.confirm.title"),
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (confirmed) runAdbOperation(project) { AdbWifiService.getInstance().controller.forget(entry); null }
    }
}

class EnableTcpipAction : EntryAction() {
    override fun isApplicable(entry: DeviceEntry) = entry.status == EntryStatus.USB

    override fun perform(project: Project?, entry: DeviceEntry) = runAdbOperation(project) {
        AdbWifiService.getInstance().controller.enableTcpip(entry)
            ?: ConnectResult.Failed(AdbFailureReason.NO_WIFI_ADDRESS, "")
    }
}
