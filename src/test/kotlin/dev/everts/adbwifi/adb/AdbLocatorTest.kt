package dev.everts.adbwifi.adb

import dev.everts.adbwifi.adb.AdbLocator.Os
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AdbLocatorTest {

    private val home = Path.of("/Users/dev")

    @Test
    fun `mac order is ANDROID_HOME, ANDROID_SDK_ROOT, default sdk`() {
        val env = mapOf("ANDROID_HOME" to "/opt/sdk", "ANDROID_SDK_ROOT" to "/opt/other")

        assertEquals(
            listOf(
                Path.of("/opt/sdk/platform-tools/adb"),
                Path.of("/opt/other/platform-tools/adb"),
                Path.of("/Users/dev/Library/Android/sdk/platform-tools/adb"),
            ),
            AdbLocator.fallbackCandidates(env, home, Os.MAC),
        )
    }

    @Test
    fun `blank variables and duplicates are skipped`() {
        val env = mapOf("ANDROID_HOME" to " ", "ANDROID_SDK_ROOT" to "/Users/dev/Library/Android/sdk")

        assertEquals(
            listOf(Path.of("/Users/dev/Library/Android/sdk/platform-tools/adb")),
            AdbLocator.fallbackCandidates(env, home, Os.MAC),
        )
    }

    @Test
    fun `linux default sdk`() {
        assertEquals(
            listOf(Path.of("/Users/dev/Android/Sdk/platform-tools/adb")),
            AdbLocator.fallbackCandidates(emptyMap(), home, Os.LINUX),
        )
    }

    @Test
    fun `windows uses adb exe under LOCALAPPDATA`() {
        val localAppData = home.resolve("AppData").toString()

        assertEquals(
            listOf(Path.of(localAppData, "Android", "Sdk", "platform-tools", "adb.exe")),
            AdbLocator.fallbackCandidates(mapOf("LOCALAPPDATA" to localAppData), home, Os.WINDOWS),
        )
    }
}
