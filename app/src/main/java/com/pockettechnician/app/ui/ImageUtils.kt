package com.pockettechnician.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

fun compressAndResizeJpeg(bytes: ByteArray, maxSide: Int = 1024, quality: Int = 85): ByteArray {
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return bytes
    val maxDim = maxOf(bitmap.width, bitmap.height)
    if (maxDim > maxSide) {
        val scale = maxSide.toFloat() / maxDim
        bitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    }
    return ByteArrayOutputStream().also { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }.toByteArray()
}
