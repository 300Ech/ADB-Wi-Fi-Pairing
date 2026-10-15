package dev.everts.adbwifi.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Color
import java.awt.image.BufferedImage

/** Dibuja un QR en negro sobre blanco, también con tema oscuro: los lectores necesitan ese contraste. */
object QrCodeImage {

    private const val QUIET_ZONE_MODULES = 2

    fun render(text: String, sizePx: Int): BufferedImage {
        val hints = mapOf(
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                image.setRGB(x, y, if (matrix[x, y]) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return image
    }
}
