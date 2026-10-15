package dev.everts.adbwifi.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PairingCodeTest {

    @ParameterizedTest
    @ValueSource(strings = ["123456", "123 456", "123-456", " 1 2 3 4 5 6 "])
    fun `accepts six digits with separators`(text: String) {
        assertEquals("123456", PairingCode.parse(text)?.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "12345", "1234567", "12a456", "１２３４５６"])
    fun `rejects anything that is not six ascii digits`(text: String) {
        assertNull(PairingCode.parse(text))
    }

    @Test
    fun `does not leak the code in toString`() {
        assertFalse(PairingCode.parse("123456").toString().contains("123456"))
    }
}
