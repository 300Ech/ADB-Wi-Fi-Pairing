package dev.everts.adbwifi.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.everts.adbwifi.diagnostics.CheckResult
import dev.everts.adbwifi.diagnostics.Finding
import dev.everts.adbwifi.diagnostics.Fix
import dev.everts.adbwifi.diagnostics.LocalNetwork
import dev.everts.adbwifi.diagnostics.Severity
import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.ui.diagnostics.DiagnosticsPanel
import dev.everts.adbwifi.ui.pairing.ConnectDialog
import dev.everts.adbwifi.ui.pairing.PairDialog
import dev.everts.adbwifi.ui.qr.QrPairingDialog
import dev.everts.adbwifi.ui.settings.AdbWifiConfigurable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Los diálogos y la página de ajustes se construyen en la IDE de test. El framework falla el test si se
 * registra cualquier error (p. ej. HTML no válido en un `text()` del UI DSL), que es lo que se busca.
 */
@TestApplication
class UiSmokeTest {

    private val project = projectFixture()

    private fun onEdt(block: () -> Unit) = timeoutRunBlocking { withContext(Dispatchers.EDT) { block() } }

    private fun check(create: () -> DialogWrapper) {
        val dialog = create()
        try {
            assertTrue(dialog.title.isNotBlank())
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    @Test
    fun `pair dialogs build in manual and assisted modes`() = onEdt {
        check { PairDialog(null) }
        check { PairDialog(null, AdbEndpoint("192.168.0.16", 37001), "Pixel 10") }
    }

    @Test
    fun `connect and qr dialogs build`() = onEdt {
        check { ConnectDialog(null, AdbEndpoint("192.168.0.16", 40073)) }
        check { QrPairingDialog(null) }
    }

    @Test
    fun `every diagnostic finding renders with its fixes`() = onEdt {
        val panel = DiagnosticsPanel(project.get())
        try {
            val checks = Finding.entries.map {
                CheckResult(it, Severity.WARNING, listOf("2", "192.168.0.16"), Fix.entries)
            }
            assertNotNull(panel.render(checks))
        } finally {
            Disposer.dispose(panel)
        }
    }

    @Test
    fun `addresses outside this computer networks get the vpn hint`() {
        val networks = listOf(LocalNetwork("en0", "192.168.0.21", 24), LocalNetwork("utun4", "10.2.0.9", 24))

        assertNotNull(ResultMessages.offNetworkHint(AdbEndpoint("10.2.0.2", 46179), networks))
        assertNull(ResultMessages.offNetworkHint(AdbEndpoint("192.168.0.16", 46179), networks))
        assertNull(ResultMessages.offNetworkHint(AdbEndpoint("pixel.local", 46179), networks))
    }

    @Test
    fun `settings page starts unmodified`() = onEdt {
        val configurable = AdbWifiConfigurable()
        assertNotNull(configurable.createComponent())
        assertFalse(configurable.isModified)
        configurable.disposeUIResources()
    }
}
