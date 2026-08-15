package com.example.mobilese

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Schlanker Bildlader fuer Netz- und Dateibilder.
 *
 * Ersetzt die externe Bibliothek Coil: die App braucht nur Laden, Skalieren,
 * Zwischenspeichern und einen runden Zuschnitt, und das laesst sich mit den
 * Bordmitteln des SDK (HttpURLConnection, BitmapFactory, LruCache) abbilden.
 *
 * Drei Punkte sind dabei wichtiger als die eingesparte Abhaengigkeit:
 *
 * 1. Bilder werden per inSampleSize verkleinert dekodiert. Vorher wurden
 *    lokale Dateien mit BitmapFactory.decodeFile in voller Aufloesung geladen -
 *    ein 12-Megapixel-Foto belegt so rund 48 MB Heap und sprengt in Listen
 *    schnell das Speicherlimit.
 * 2. Dekodieren und Herunterladen laufen auf Dispatchers.IO, nie im Main-Thread.
 * 3. Jede Anfrage markiert die ImageView per Tag. Trifft eine Antwort ein,
 *    nachdem die View fuer ein anderes Bild wiederverwendet wurde, wird sie
 *    verworfen statt das falsche Bild zu zeigen.
 */
object ImageLoader {

    /** Kantenlaenge, auf die Bilder beim Dekodieren heruntergerechnet werden. */
    private const val MAX_SIZE_PX = 512

    private const val CACHE_DIR_NAME = "image_cache"
    private const val MAX_DISK_CACHE_BYTES = 20L * 1024 * 1024

    /**
     * Ein sprechender User-Agent ist Pflicht bei den Kachelservern von
     * OpenStreetMap; anonyme Abrufe werden dort abgewiesen. Fuer die uebrigen
     * Bilder schadet er nicht.
     */
    private const val USER_AGENT = "CrewFit/1.0 (Mobile Software Engineering student project)"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Ein Achtel des verfuegbaren Heaps, gemessen in Kilobyte. */
    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @Volatile
    private var diskCacheTrimmed = false

    /** Eine Sperre je URL, damit dasselbe Bild nur einmal geladen wird. */
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Laedt [source] in [target]. [source] darf eine http(s)-URL oder ein
     * lokaler Dateipfad sein; ist sie leer, bleibt die View unveraendert.
     *
     * @param circular schneidet das Bild rund zu (Profilbilder).
     * @param placeholder wird angezeigt, solange das Bild noch nicht da ist.
     */
    fun into(
        target: ImageView,
        source: String?,
        circular: Boolean = false,
        @DrawableRes placeholder: Int = 0
    ) {
        if (source.isNullOrEmpty()) {
            target.setTag(R.id.image_loader_tag, null)
            if (placeholder != 0) target.setImageResource(placeholder)
            return
        }

        val key = "$source|$circular"
        target.setTag(R.id.image_loader_tag, key)

        memoryCache.get(key)?.let {
            target.setImageBitmap(it)
            return
        }

        if (placeholder != 0) target.setImageResource(placeholder)

        val context = target.context.applicationContext
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, source, circular) }
                ?: return@launch
            memoryCache.put(key, bitmap)
            // Die View kann inzwischen fuer einen anderen Eintrag wiederverwendet
            // worden sein - dann gehoert dieses Bild nicht mehr hierher.
            if (target.getTag(R.id.image_loader_tag) == key) {
                target.setImageBitmap(bitmap)
            }
        }
    }

    /**
     * Dekodiert ein per Content-Uri ausgewaehltes Bild verkleinert.
     * Wird beim Auswaehlen eines Profilbilds gebraucht, damit weder die
     * Vorschau noch der Upload das Originalfoto in voller Groesse anfasst.
     */
    fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Log.e("ImageLoader", "Could not decode $uri: ${e.message}")
            null
        }
    }

    /**
     * Bereitet eine lokale Bilddatei fuer den Upload auf: verkleinert und als
     * JPEG neu kodiert. Ein Kamerafoto schrumpft dabei von mehreren Megabyte
     * auf einige zehn Kilobyte - das spart Uploadzeit beim Aufnehmen und
     * Datenvolumen bei allen, die das Bild spaeter ansehen.
     *
     * Gibt null zurueck, wenn die Datei kein dekodierbares Bild ist; der
     * Aufrufer laedt dann die Originaldatei hoch.
     */
    fun compressForUpload(localPath: String): ByteArray? {
        val file = File(localPath)
        if (!file.exists()) return null
        val bitmap = decodeFile(file) ?: return null
        return try {
            java.io.ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                output.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("ImageLoader", "Could not compress $localPath: ${e.message}")
            null
        }
    }

    /**
     * Laedt ein einzelnes Bild von einer URL und gibt es zurueck, statt es in
     * eine View zu setzen. Wird von [StaticMap] gebraucht, das mehrere
     * Kartenkacheln zu einem Bild zusammensetzt. Nutzt denselben Dateicache
     * und dieselbe Sperre pro URL wie [into].
     */
    suspend fun remoteBitmap(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            cachedDownload(context, url)?.let { decodeFile(it) }
        }

    // --- interne Hilfen ---

    private suspend fun loadBitmap(context: Context, source: String, circular: Boolean): Bitmap? {
        val file = if (source.startsWith("http")) {
            cachedDownload(context, source) ?: return null
        } else {
            File(source).takeIf { it.exists() } ?: return null
        }

        val bitmap = decodeFile(file) ?: return null
        return if (circular) circleCrop(bitmap) else bitmap
    }

    /**
     * Laedt eine URL hoechstens einmal herunter und legt sie im
     * Cache-Verzeichnis ab.
     *
     * Die Sperre pro URL ist noetig, weil dasselbe Bild an mehreren Stellen
     * gleichzeitig angefordert wird - das Profilbild steht sowohl in der
     * Mitgliederleiste als auch in der Rangliste. Ohne sie liefen zwei
     * Downloads parallel in dieselbe Datei und ueberschrieben sich
     * gegenseitig; das Ergebnis war eine unvollstaendige Datei, die
     * BitmapFactory kommentarlos als null zurueckgibt. Sichtbar war das als
     * Platzhalter an der einen und korrektes Bild an der anderen Stelle.
     */
    private suspend fun cachedDownload(context: Context, url: String): File? {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) return null
        trimDiskCacheOnce(dir)

        val target = File(dir, fileNameFor(url))
        if (target.exists() && target.length() > 0) return target

        return downloadLocks.getOrPut(url) { Mutex() }.withLock {
            // Waehrend des Wartens kann die Datei bereits fertig sein.
            if (target.exists() && target.length() > 0) target
            else download(url, dir, target)
        }
    }

    private fun download(url: String, dir: File, target: File): File? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("ImageLoader", "Download of $url failed: HTTP ${connection.responseCode}")
                return null
            }
            // Erst in eine temporaere Datei schreiben, damit ein Abbruch keine
            // halbe Datei im Cache hinterlaesst, die spaeter als gueltig gilt.
            // Der Name ist eindeutig, damit zwei Downloads sich nie dieselbe
            // Zwischendatei teilen.
            val temp = File.createTempFile(target.name, ".tmp", dir)
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (target.exists()) target.delete()
            if (temp.renameTo(target)) target else temp
        } catch (e: Exception) {
            Log.e("ImageLoader", "Download of $url failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fileNameFor(url: String): String =
        Integer.toHexString(url.hashCode()) + "_" + url.length

    /**
     * Haelt das Cache-Verzeichnis in Grenzen. Einmal pro Prozessstart genuegt,
     * die Menge waechst waehrend einer Sitzung nur langsam.
     */
    private fun trimDiskCacheOnce(dir: File) {
        if (diskCacheTrimmed) return
        diskCacheTrimmed = true
        try {
            val files = dir.listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_DISK_CACHE_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_DISK_CACHE_BYTES) break
                total -= file.length()
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("ImageLoader", "Cache cleanup failed: ${e.message}")
        }
    }

    private fun decodeFile(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) {
                // Kein gueltiges Bild - etwa eine abgebrochene Uebertragung.
                // Wird protokolliert, weil BitmapFactory in diesem Fall
                // kommentarlos null liefert und der Fehler sonst nur als
                // fehlendes Bild sichtbar waere.
                Log.e("ImageLoader", "Not a decodable image: ${file.name}")
                file.delete()
                return null
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: OutOfMemoryError) {
            Log.e("ImageLoader", "Out of memory while decoding ${file.name}")
            null
        }
    }

    /** Groesste Zweierpotenz, mit der das Bild noch ueber MAX_SIZE_PX bleibt. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_SIZE_PX || height / (sample * 2) >= MAX_SIZE_PX) {
            sample *= 2
        }
        return sample
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, Rect(left, top, left + size, top + size), Rect(0, 0, size, size), paint)

        return output
    }
}
