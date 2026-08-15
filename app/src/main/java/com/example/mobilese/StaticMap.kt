package com.example.mobilese

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Erzeugt ein Kartenbild zu einer Position.
 *
 * Statt einer Karten-Bibliothek werden die Kacheln von OpenStreetMap direkt
 * ueber HTTP geholt und zusammengesetzt - das ist ein gewoehnlicher Bildabruf
 * und kommt mit dem vorhandenen [ImageLoader] aus. Eine echte Karte zum
 * Verschieben waere damit nicht zu bauen, fuer eine Vorschau des gewaehlten
 * Ortes genuegt es.
 *
 * Kacheln folgen dem verbreiteten "slippy map"-Schema: die Welt wird je
 * Zoomstufe in 2^zoom mal 2^zoom Quadrate von 256 Pixeln geteilt, die ueber
 * die Mercator-Projektion adressiert werden.
 *
 * Die Nutzungsbedingungen von OpenStreetMap verlangen einen sprechenden
 * User-Agent - den setzt der ImageLoader - und einen sichtbaren
 * Quellenhinweis, siehe [ATTRIBUTION].
 */
object StaticMap {

    const val ATTRIBUTION = "© OpenStreetMap contributors"

    private const val TILE_SIZE = 256
    private const val DEFAULT_ZOOM = 16
    private const val OUTPUT_SIZE = 512

    /**
     * Ein quadratischer Kartenausschnitt, in dessen Mitte die Position mit
     * einem Marker sitzt. Null, wenn keine Kachel geladen werden konnte.
     */
    suspend fun preview(
        context: Context,
        latitude: Double,
        longitude: Double,
        markerColor: Int,
        zoom: Int = DEFAULT_ZOOM
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val tilesPerAxis = 1 shl zoom

            // Position in Weltpixeln der gewaehlten Zoomstufe.
            val worldX = (longitude + 180.0) / 360.0 * tilesPerAxis * TILE_SIZE
            val latitudeRad = latitude * PI / 180.0
            val worldY = (1.0 - ln(tan(latitudeRad) + 1.0 / cos(latitudeRad)) / PI) /
                    2.0 * tilesPerAxis * TILE_SIZE

            // Linke obere Ecke des Ausschnitts, damit die Position mittig liegt.
            val left = worldX - OUTPUT_SIZE / 2.0
            val top = worldY - OUTPUT_SIZE / 2.0

            val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.DKGRAY)

            var drawnTiles = 0
            val firstColumn = floor(left / TILE_SIZE).toInt()
            val lastColumn = floor((left + OUTPUT_SIZE - 1) / TILE_SIZE).toInt()
            val firstRow = floor(top / TILE_SIZE).toInt()
            val lastRow = floor((top + OUTPUT_SIZE - 1) / TILE_SIZE).toInt()

            for (column in firstColumn..lastColumn) {
                for (row in firstRow..lastRow) {
                    // Laengengrade laufen um die Welt herum, Breitengrade nicht.
                    val tileX = ((column % tilesPerAxis) + tilesPerAxis) % tilesPerAxis
                    val tileY = row.coerceIn(0, tilesPerAxis - 1)

                    val tile = ImageLoader.remoteBitmap(context, tileUrl(zoom, tileX, tileY))
                        ?: continue
                    canvas.drawBitmap(
                        tile,
                        (column * TILE_SIZE - left).toFloat(),
                        (row * TILE_SIZE - top).toFloat(),
                        null
                    )
                    drawnTiles++
                }
            }

            if (drawnTiles == 0) return@withContext null

            drawMarker(canvas, markerColor)
            output
        } catch (e: Exception) {
            Log.e("StaticMap", "Could not build the map preview: ${e.message}")
            null
        }
    }

    private fun tileUrl(zoom: Int, x: Int, y: Int) = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    /** Ein Punkt mit weissem Rand in der Bildmitte. */
    private fun drawMarker(canvas: Canvas, markerColor: Int) {
        val centerX = OUTPUT_SIZE / 2f
        val centerY = OUTPUT_SIZE / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 22f, paint)

        paint.color = markerColor
        canvas.drawCircle(centerX, centerY, 16f, paint)
    }
}
