package dev.everts.adbwifi.adb

import dev.everts.adbwifi.adb.bonjour.DnsSdBrowser
import dev.everts.adbwifi.domain.MdnsStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Comandos de solo lectura contra el adb instalado. No forma parte de `check`; se activa con
 * `ADB_WIFI_REAL_ADB=1 ./gradlew test --tests '*RealAdbTest'` para validar fixtures nuevos.
 */
@EnabledIfEnvironmentVariable(named = "ADB_WIFI_REAL_ADB", matches = "1")
class CliAdbGatewayRealAdbTest {

    private val gateway = CliAdbGateway(
        AdbLocator.fallbackCandidates(System.getenv(), Path.of(System.getProperty("user.home")))
            .first { Files.isExecutable(it) },
    )

    @Test
    fun `read only commands parse real output`() = runBlocking<Unit> {
        val devices = gateway.devices()
        val mdns = gateway.mdnsCheck()
        val services = if (mdns is MdnsStatus.Available) gateway.mdnsServices() else emptyList()

        println("devices=$devices\nmdns=$mdns\nservices=$services")
        assertInstanceOf(MdnsStatus::class.java, mdns)
    }

    @Test
    fun `bonjour sees the same services as macOS dns-sd`() = runBlocking<Unit> {
        val browser = DnsSdBrowser.forThisMachine() ?: return@runBlocking
        println("bonjour=${browser.services()}")
    }
}
