package ca.devmesh.seerrtv.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.MultiFormatWriter
import androidx.core.graphics.createBitmap

fun generateQRCode(content: String, width: Int, height: Int): Bitmap? {
    val result: BitMatrix
    try {
        result = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, null)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (result[x, y]) -0x1000000 else -0x1
        }
    }
    val bitmap = createBitmap(w, h)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    return bitmap
}

