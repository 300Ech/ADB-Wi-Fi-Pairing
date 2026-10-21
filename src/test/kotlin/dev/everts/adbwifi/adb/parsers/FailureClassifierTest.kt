package dev.everts.adbwifi.adb.parsers

import dev.everts.adbwifi.domain.AdbFailureReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FailureClassifierTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "failed to connect to '192.168.0.42:37099': Host is down | HOST_DOWN",
            "failed to connect to '192.168.0.42:37099': Connection timed out | TIMED_OUT",
            "failed to connect to [fe80::1]:5555 | AUTHENTICATION_FAILED",
            "failed to connect to '192.168.0.42:37099': Something new | UNKNOWN",
            "failed to connect to '192.168.0.42:37099': Network is unreachable | NO_ROUTE_TO_HOST",
            "cannot connect to 192.168.0.42:37099: Le délai d’attente est dépassé. (10060) | TIMED_OUT",
            "cannot connect to 192.168.0.42:37099: Der Zielhost ist ausgefallen. (10064) | HOST_DOWN",
            "cannot connect to 192.168.0.42:37099: 不明なエラー (10013) | UNKNOWN",
            "error: closed | UNKNOWN",
            "'' | UNKNOWN",
        ],
    )
    fun `classifies messages not covered by fixtures`(message: String, reason: AdbFailureReason) {
        assertEquals(reason, FailureClassifier.classify(message))
    }
}
