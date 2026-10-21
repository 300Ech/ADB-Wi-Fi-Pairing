package dev.everts.adbwifi.ui.diagnostics

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import dev.everts.adbwifi.AdbWifiBundle
import dev.everts.adbwifi.AdbWifiService
import dev.everts.adbwifi.diagnostics.CheckResult
import dev.everts.adbwifi.diagnostics.Fix
import dev.everts.adbwifi.diagnostics.Severity
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.ui.LocalNetworkBanner
import dev.everts.adbwifi.ui.WindowsSettings
import dev.everts.adbwifi.ui.actions.showPairDialog
import dev.everts.adbwifi.ui.pairing.PairDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import javax.swing.Icon

/** Pestaña "Diagnóstico": ejecuta los chequeos y ofrece las reparaciones que correspondan. */
class DiagnosticsPanel(private val project: Project) : BorderLayoutPanel(), Disposable {

    private var targetText = ""
    private val results = BorderLayoutPanel()
    private var job: Job? = null
    private val header: DialogPanel = panel {
        row { text(AdbWifiBundle.message("diagnostics.intro")).align(AlignX.FILL) }
        row(AdbWifiBundle.message("diagnostics.target.label")) {
            textField()
                .bindText(::targetText)
                .columns(TARGET_COLUMNS)
                .comment(AdbWifiBundle.message("diagnostics.target.comment"))
            button(AdbWifiBundle.message("diagnostics.run")) { run() }
        }
    }

    init {
        header.border = JBUI.Borders.empty(PADDING)
        results.border = JBUI.Borders.empty(0, PADDING, PADDING, PADDING)
        addToTop(header)
        addToCenter(ScrollPaneFactory.createScrollPane(results, true))
    }

    /** Ejecuta los chequeos; si ya hay unos en marcha, no hace nada. */
    fun run() {
        if (job?.isActive == true) return
        header.apply()
        val target = targetText.takeIf { it.isNotBlank() }?.let { text ->
            AdbEndpoint.parse(text) ?: AdbEndpoint.parse("$text:$DEFAULT_PROBE_PORT")
        }
        show(panel { row { label(AdbWifiBundle.message("diagnostics.running")) } })
        val service = AdbWifiService.getInstance()
        job = service.launch {
            service.controller.refresh()
            val checks = service.diagnostics.run(service.controller.state.value, target)
            withContext(Dispatchers.EDT) { show(render(checks)) }
        }
    }

    internal fun render(checks: List<CheckResult>): DialogPanel = panel {
        for (check in checks) {
            val key = "diagnostic.${check.finding.name}"
            val args: Array<Any> = check.args.toTypedArray()
            row {
                icon(icon(check.severity))
                @Suppress("SpreadOperator") // los argumentos de un mensaje son pocos
                label(AdbWifiBundle.message("$key.title", *args)).bold()
            }
            row {
                @Suppress("SpreadOperator")
                text(AdbWifiBundle.message("$key.detail", *args)).align(AlignX.FILL)
            }
            if (check.fixes.isNotEmpty()) {
                row {
                    for (fix in check.fixes) {
                        button(AdbWifiBundle.message("diagnostic.fix.${fix.name}")) { apply(fix, check) }
                    }
                }
            }
            separator()
        }
    }

    private fun apply(fix: Fix, check: CheckResult) {
        val service = AdbWifiService.getInstance()
        when (fix) {
            Fix.OPEN_LOCAL_NETWORK_SETTINGS -> LocalNetworkBanner.openSettings()
            Fix.RESTART_STUDIO -> LocalNetworkBanner.restartIde()
            Fix.OPEN_NETWORK_SETTINGS -> WindowsSettings.openNetworkSettings()
            Fix.OPEN_FIREWALL_SETTINGS -> WindowsSettings.openFirewallSettings()
            Fix.RESTART_ADB, Fix.RESTART_ADB_WITH_MDNS -> {
                val confirmed = Messages.showYesNoDialog(
                    project,
                    AdbWifiBundle.message("restartAdb.confirm.message"),
                    AdbWifiBundle.message("restartAdb.confirm.title"),
                    Messages.getWarningIcon(),
                ) == Messages.YES
                if (!confirmed) return
                val environment = if (fix == Fix.RESTART_ADB_WITH_MDNS) mapOf("ADB_MDNS" to "1") else emptyMap()
                service.launch {
                    runCatching { service.controller.restartAdb(environment) }
                    withContext(Dispatchers.EDT) { run() }
                }
            }
            Fix.PAIR_AGAIN -> showPairDialog(PairDialog(project, deviceName = check.args.firstOrNull()), project)
            Fix.OPEN_SDK_MANAGER -> ActionManager.getInstance().getAction(SDK_MANAGER_ACTION)?.let {
                ActionManager.getInstance().tryToExecute(it, null, this, ActionPlaces.TOOLWINDOW_CONTENT, true)
            }
        }
    }

    private fun show(content: DialogPanel) {
        results.removeAll()
        results.addToTop(content)
        results.revalidate()
        results.repaint()
    }

    private fun icon(severity: Severity): Icon = when (severity) {
        Severity.OK -> AllIcons.General.InspectionsOK
        Severity.INFO -> AllIcons.General.Information
        Severity.WARNING -> AllIcons.General.Warning
        Severity.ERROR -> AllIcons.General.Error
    }

    override fun dispose() {
        job?.cancel()
    }

    private companion object {
        const val TARGET_COLUMNS = 18
        const val PADDING = 8
        const val DEFAULT_PROBE_PORT = 5555
        const val SDK_MANAGER_ACTION = "Android.RunAndroidSdkManager"
    }
}
