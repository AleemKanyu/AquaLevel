package com.aqualevel.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders a ZXing QR code as a Compose Image.
 *
 * @param content  The string to encode (e.g. "aqualevel://pair?id=AQL-XXXXXXXX")
 * @param size     The width & height of the rendered QR image
 * @param darkColor  Color for the dark modules (default: near-black navy)
 * @param lightColor Color for the light modules (default: white)
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    darkColor: Color = Color(0xFF0A1628),
    lightColor: Color = Color.White
) {
    val bitmap = remember(content) {
        generateQrBitmap(content, 512, darkColor, lightColor)
    }

    Surface(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp)),
        color = lightColor,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR Code for $content",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
        }
    }
}

private fun generateQrBitmap(
    content: String,
    sizePx: Int,
    darkColor: Color,
    lightColor: Color
): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN           to 1,
            EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val darkArgb  = android.graphics.Color.argb(
            (darkColor.alpha  * 255).toInt(),
            (darkColor.red    * 255).toInt(),
            (darkColor.green  * 255).toInt(),
            (darkColor.blue   * 255).toInt()
        )
        val lightArgb = android.graphics.Color.argb(
            (lightColor.alpha * 255).toInt(),
            (lightColor.red   * 255).toInt(),
            (lightColor.green * 255).toInt(),
            (lightColor.blue  * 255).toInt()
        )

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) darkArgb else lightArgb)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
