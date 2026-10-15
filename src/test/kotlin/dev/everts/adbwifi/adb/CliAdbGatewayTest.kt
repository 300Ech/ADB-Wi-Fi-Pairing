package dev.everts.adbwifi.adb

import dev.everts.adbwifi.domain.AdbEndpoint
import dev.everts.adbwifi.domain.AdbFailureReason
import dev.everts.adbwifi.domain.ConnectResult
import dev.everts.adbwifi.domain.DeviceState
import dev.everts.adbwifi.domain.MdnsServiceType
import dev.everts.adbwifi.domain.MdnsStatus
import dev.everts.adbwifi.domain.PairingCode
import dev.everts.adbwifi.domain.PairingResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.readLines
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** [CliAdbGateway] contra `fake-adb/adb.sh`, que reproduce las salidas reales guardadas como fixtures. */
@DisabledOnOs(OS.WINDOWS)
class CliAdbGatewayTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var adb: Path
    private lateinit var argsFile: Path

    @BeforeEach
    fun installFakeAdb() {
        adb = dir.resolve("adb")
        Files.copy(requireNotNull(javaClass.getResourceAsStream("/fake-adb/adb.sh")), adb)
        adb.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
        argsFile = dir.resolve("args.txt")
    }

    /** Gateway cuyo adb falso responde con los casos indicados, uno por llamada. */
    private fun gateway(
        vararg cases: String,
        exitCode: Int = 0,
        sleepSeconds: Int? = null,
        timeouts: AdbTimeouts = AdbTimeouts(),
    ): CliAdbGateway {
        val stdout = cases.mapIndexed { index, case -> copy(AdbFixtures.stdout(case), "$index.out") }
        val stderr = cases.mapIndexed { index, case -> copy(AdbFixtures.stderr(case), "$index.err") }
        val environment = buildMap {
            put("FAKE_ADB_ARGS_FILE", argsFile.toString())
            put("FAKE_ADB_STDOUT", stdout.joinToString(" "))
            put("FAKE_ADB_STDERR", stderr.joinToString(" "))
            put("FAKE_ADB_EXIT", exitCode.toString())
            sleepSeconds?.let { put("FAKE_ADB_SLEEP", it.toString()) }
        }
        return CliAdbGateway(adb, timeouts, environment, mdnsPollInterval = 10.milliseconds)
    }

    private fun copy(text: String, name: String): Path = dir.resolve(name).also { it.writeText(text) }

    private fun calls(): List<String> = argsFile.readLines()

    private val code = requireNotNull(PairingCode.parse("156220"))

    @Test
    fun `pair passes endpoint and code and parses success`() = runBlocking {
        val result = gateway("pair-ok").pair(AdbEndpoint("127.0.0.1", 7791), code)

        assertEquals(PairingResult.Paired(AdbEndpoint("127.0.0.1", 7791), "adb-EMULATOR36X3X10X0-hk2X6D"), result)
        assertEquals(listOf("pair 127.0.0.1:7791 156220"), calls())
    }

    @Test
    fun `pair failure on stderr with exit code 1`() = runBlocking {
        val result = gateway("pair-wrong-code", exitCode = 1).pair(AdbEndpoint("127.0.0.1", 7791), code)

        assertEquals(AdbFailureReason.PROTOCOL_FAULT, (result as PairingResult.Failed).reason)
    }

    @Test
    fun `pair timeout is a failed result that hides the code`() = runBlocking {
        val gateway = gateway("pair-ok", sleepSeconds = 5, timeouts = AdbTimeouts(pair = 300.milliseconds))

        val result: PairingResult
        val elapsed = measureTime { result = gateway.pair(AdbEndpoint("127.0.0.1", 7791), code) }

        val failed = result as PairingResult.Failed
        assertEquals(AdbFailureReason.TIMED_OUT, failed.reason)
        assertFalse("156220" in failed.message, failed.message)
        assertTrue(elapsed < 3.seconds, "took $elapsed")
    }

    @Test
    fun `connect success and already connected`() = runBlocking {
        val gateway = gateway("connect-ok", "connect-already")
        val endpoint = AdbEndpoint("127.0.0.1", 7790)

        assertEquals(ConnectResult.Connected(endpoint, alreadyConnected = false), gateway.connect(endpoint))
        assertEquals(ConnectResult.Connected(endpoint, alreadyConnected = true), gateway.connect(endpoint))
        assertEquals(listOf("connect 127.0.0.1:7790", "connect 127.0.0.1:7790"), calls())
    }

    @Test
    fun `connect failure reported with exit code 0`() = runBlocking {
        val result = gateway("connect-refused").connect(AdbEndpoint("127.0.0.1", 5))

        assertEquals(
            ConnectResult.Failed(
                AdbFailureReason.CONNECTION_REFUSED,
                "failed to connect to '127.0.0.1:5': Connection refused",
            ),
            result,
        )
    }

    @Test
    fun `connect timeout`() = runBlocking {
        val gateway = gateway("connect-ok", sleepSeconds = 5, timeouts = AdbTimeouts(connect = 300.milliseconds))

        val result = gateway.connect(AdbEndpoint("192.168.0.253", 41234))

        assertEquals(AdbFailureReason.TIMED_OUT, (result as ConnectResult.Failed).reason)
    }

    @Test
    fun `disconnect known and unknown device`() = runBlocking {
        assertTrue(gateway("disconnect-ok").disconnect(AdbEndpoint("127.0.0.1", 7790)))
        assertFalse(gateway("disconnect-unknown", exitCode = 1).disconnect(AdbEndpoint("127.0.0.1", 5)))
        assertEquals(listOf("disconnect 127.0.0.1:7790", "disconnect 127.0.0.1:5"), calls())
    }

    @Test
    fun `devices uses long format`() = runBlocking {
        val devices = gateway("devices-mixed").devices()

        assertEquals(listOf(DeviceState.DEVICE, DeviceState.OFFLINE), devices.map { it.state })
        assertEquals(listOf("devices -l"), calls())
    }

    @Test
    fun `devices with non zero exit throws with adb message`() {
        val error = assertThrows<AdbCommandException> {
            runBlocking { gateway("tcpip-no-device", exitCode = 1).devices() }
        }
        assertEquals(1, error.exitCode)
        assertEquals("error: device 'nonexistent' not found", error.output)
    }

    @Test
    fun `mdns check and services`() = runBlocking {
        val gateway = gateway("mdns-check", "mdns-services")

        assertEquals(MdnsStatus.Available("adb discovery 0.0.0"), gateway.mdnsCheck())
        assertEquals(listOf(MdnsServiceType.TLS_CONNECT), gateway.mdnsServices().map { it.type })
        assertEquals(listOf("mdns check", "mdns services"), calls())
    }

    @Test
    fun `track mdns services emits only changes`() = runBlocking {
        val gateway = gateway("devices-empty", "devices-empty", "mdns-services", "mdns-services", "devices-empty")

        val snapshots = gateway.trackMdnsServices().take(3).toList()

        assertEquals(listOf(0, 1, 0), snapshots.map { it.size })
        assertEquals(5, calls().size)
    }

    @Test
    fun `tcpip passes serial and port`() = runBlocking {
        gateway("tcpip").tcpip("emulator-5580", 5556)

        assertEquals(listOf("-s emulator-5580 tcpip 5556"), calls())
    }

    @Test
    fun `tcpip error throws`() {
        assertThrows<AdbCommandException> {
            runBlocking { gateway("tcpip-no-device", exitCode = 1).tcpip("nonexistent", 5555) }
        }
    }

    @Test
    fun `missing executable throws launch exception`() {
        val gateway = CliAdbGateway(dir.resolve("does-not-exist"))

        assertThrows<AdbLaunchException> { runBlocking { gateway.devices() } }
    }

    @Test
    fun `cancellation stops waiting for adb`() = runBlocking {
        val gateway = gateway("devices-empty", sleepSeconds = 10)

        val elapsed = measureTime {
            val call = async(start = CoroutineStart.UNDISPATCHED) { gateway.devices() }
            delay(300.milliseconds)
            call.cancelAndJoin()
            assertTrue(call.isCancelled)
        }

        assertTrue(elapsed < 5.seconds, "took $elapsed")
    }
}
