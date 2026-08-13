package com.example.mobilese

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Erzeugt die QR-Codes, mit denen eine Crew geteilt wird.
 *
 * Die Erzeugung eines 500x500-Bitmaps lief bisher an zwei Stellen doppelt und
 * jeweils im Main-Thread. Hier steht sie einmal und laeuft auf [Dispatchers.IO].
 */
object QrCodes {

    suspend fun generate(content: String, sizePx: Int = 500): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            } catch (e: Exception) {
                Log.e("QrCodes", "Could not encode '$content': ${e.message}")
                null
            }
        }
}
