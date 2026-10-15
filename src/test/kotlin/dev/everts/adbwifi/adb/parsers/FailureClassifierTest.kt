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
            "error: closed | UNKNOWN",
            "'' | UNKNOWN",
        ],
    )
    fun `classifies messages not covered by fixtures`(message: String, reason: AdbFailureReason) {
        assertEquals(reason, FailureClassifier.classify(message))
    }
}
