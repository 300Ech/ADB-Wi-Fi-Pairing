package dev.everts.adbwifi.ui.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.application.AdbWifiEvent
import dev.everts.adbwifi.application.AdbWifiNotifier
import dev.everts.adbwifi.persistence.AdbWifiSettings
import dev.everts.adbwifi.ui.actions.showPairDialog
import dev.everts.adbwifi.ui.pairing.PairDialog
import dev.everts.adbwifi.ui.toolwindow.AdbWifiToolWindowFactory
import org.jetbrains.annotations.Nls

/** Notificaciones de la IDE para los eventos de fondo y los resultados de las acciones. */
object AdbWifiNotifications : AdbWifiNotifier {

    const val GROUP_ID = "ADB Wi-Fi"

    override fun notify(event: AdbWifiEvent) {
        if (!AdbWifiSettings.getInstance().state.notifications) return
        val notification = when (event) {
            is AdbWifiEvent.Reconnected -> create(
                AdbWifiBundle.message("notification.reconnected", event.deviceName, event.endpoint.toString()),
                NotificationType.INFORMATION,
            )
            is AdbWifiEvent.NeedsRepair -> create(
                AdbWifiBundle.message("notification.needsRepair", event.deviceName),
                NotificationType.WARNING,
            ).addAction(openToolWindowAction()).addAction(diagnoseAction())
            is AdbWifiEvent.PairingAvailable -> create(
                AdbWifiBundle.message("notification.pairingAvailable", event.deviceName),
                NotificationType.INFORMATION,
            ).addAction(
                NotificationAction.createSimpleExpiring(AdbWifiBundle.message("notification.action.pair")) {
                    val project = ProjectManager.getInstance().openProjects.firstOrNull()
                    showPairDialog(PairDialog(project, event.endpoint, event.deviceName), project)
                },
            )
            is AdbWifiEvent.Lost -> create(
                AdbWifiBundle.message("notification.lost", event.deviceName),
                NotificationType.WARNING,
            ).addAction(openToolWindowAction())
        }
        notification.notify(null)
    }

    /** Resultado de una acción del usuario: éxito discreto, fallo con explicación. */
    fun result(project: Project?, @Nls message: String, success: Boolean) {
        val type = if (success) NotificationType.INFORMATION else NotificationType.ERROR
        create(message, type).apply { if (!success) addAction(diagnoseAction()) }.notify(project)
    }

    private fun create(@Nls content: String, type: NotificationType): Notification =
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
            .createNotification(AdbWifiBundle.message("notification.title"), content, type)

    private fun diagnoseAction() =
        NotificationAction.createSimpleExpiring(AdbWifiBundle.message("notification.action.diagnose")) {
            ProjectManager.getInstance().openProjects.firstOrNull()?.let(AdbWifiToolWindowFactory::showDiagnostics)
        }

    private fun openToolWindowAction() =
        NotificationAction.createSimpleExpiring(AdbWifiBundle.message("notification.action.open")) {
            val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return@createSimpleExpiring
            ToolWindowManager.getInstance(project).getToolWindow(AdbWifiToolWindowFactory.ID)?.activate(null)
        }
}
