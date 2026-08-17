package com.example.mobilese

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Ein Ringdiagramm.
 *
 * Selbst gezeichnet statt mit einer Diagramm-Bibliothek: ein Ring aus Boegen
 * sind dreissig Zeilen Canvas, und der Kurs verlangt, mit moeglichst wenigen
 * fremden Bibliotheken auszukommen. Eine Bibliothek wie MPAndroidChart wuerde
 * fuer diesen einen Ring mehrere hundert Kilobyte mitbringen.
 *
 * Erwartet ueber [setSlices] Werte mit Farbe. Die Anteile rechnet die Ansicht
 * selbst aus, der Aufrufer gibt Rohwerte.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Ein Stueck: der Rohwert und seine Farbe. */
    data class Slice(val value: Int, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val bounds = RectF()
    private var slices: List<Slice> = emptyList()

    /** Dicke des Rings; der Rest bleibt frei. */
    private val ringWidth = resources.getDimension(R.dimen.donut_ring_width)

    /**
     * Kleiner Abstand zwischen den Stuecken, damit sie sich bei aehnlichen
     * Farben nicht zu einer Flaeche verbinden. Faellt weg, sobald nur noch ein
     * Stueck da ist - sonst klaffte im vollen Ring eine Luecke.
     */
    private val gapDegrees = 2f

    fun setSlices(slices: List<Slice>) {
        this.slices = slices.filter { it.value > 0 }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val total = slices.sumOf { it.value }
        if (total <= 0) return

        val inset = ringWidth / 2f
        bounds.set(inset, inset, width - inset, height - inset)
        paint.strokeWidth = ringWidth

        val gap = if (slices.size > 1) gapDegrees else 0f
        // Oben beginnen statt rechts: so liest sich der Ring wie eine Uhr.
        var start = -90f

        slices.forEach { slice ->
            val sweep = 360f * slice.value / total
            paint.color = slice.color
            canvas.drawArc(bounds, start + gap / 2f, sweep - gap, false, paint)
            start += sweep
        }
    }
}
