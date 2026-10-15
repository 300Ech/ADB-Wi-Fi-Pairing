package dev.everts.adbwifi.ui.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.everts.adbwifi.domain.QrPairingRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class QrPairingTest {

    @Test
    fun `payload has the format Android expects`() {
        val request = QrPairingRequest.generate(Random(42))

        assertTrue(Regex("""WIFI:T:ADB;S:studio-[A-Za-z0-9]{10};P:[A-Za-z0-9]{12};;""").matches(request.payload))
        assertTrue(request.serviceName in request.payload)
        assertTrue(request.secret.value in request.payload)
        assertTrue(request.secret.value !in request.toString())
    }

    @Test
    fun `every request is different`() {
        assertNotEquals(QrPairingRequest.generate().payload, QrPairingRequest.generate().payload)
    }

    @Test
    fun `rendered image decodes back to the payload`() {
        val payload = QrPairingRequest.generate().payload
        val image = QrCodeImage.render(payload, 220)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))

        assertEquals(payload, QRCodeReader().decode(bitmap).text)
    }
}
