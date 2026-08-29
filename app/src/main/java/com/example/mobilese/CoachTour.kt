package com.example.mobilese

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import com.google.android.material.button.MaterialButton

/**
 * Die Einfuehrung: ein Bereich wird freigestellt, daneben steht, was er tut.
 *
 * Gedacht fuer den ersten Start. Wer die App zum ersten Mal oeffnet, sieht
 * einen Startbildschirm mit acht Bereichen und keine Erklaerung fuer einen
 * davon - Leerzustaende (siehe [EmptyState]) sagen zwar, was *fehlt*, aber
 * nicht, wofuer die Karten da sind, die schon etwas anzeigen.
 *
 * Aufgebaut aus zwei Teilen, die uebereinanderliegen:
 *
 * 1. [Spotlight] verdunkelt den Bildschirm und laesst genau ein Rechteck frei.
 *    Gezeichnet als ein einziger Pfad mit zwei Konturen und der Fuellregel
 *    EVEN_ODD - so entsteht das Loch, ohne mit Ebenen und Xfermode zu
 *    hantieren, was auf aelteren Geraeten gern unterschiedlich aussieht.
 * 2. Die Sprechblase aus `view_coach_bubble.xml`, also eine gewoehnliche
 *    Karte mit gewoehnlichen Knoepfen. Sie wird ueber oder unter das Loch
 *    gelegt, je nachdem wo Platz ist.
 *
 * Beides liegt in einer eigenen Ebene ueber dem Inhalt der Activity. Die Ebene
 * faengt alle Beruehrungen ab: waehrend der Einfuehrung ist nur die Blase
 * bedienbar, sonst tippt man versehentlich auf das, was gerade erklaert wird.
 *
 * **Der Fortschritt liegt in den Einstellungen und nicht im Speicher.** Dreht
 * jemand das Geraet, wird die Activity neu gebaut und diese Ebene ist weg; beim
 * naechsten Zeichnen geht es an derselben Stelle weiter statt von vorn.
 */
class CoachTour private constructor(
    private val activity: Activity,
    private val id: String,
    private val steps: List<Step>
) {

    /**
     * Ein Halt der Einfuehrung.
     *
     * @param targetId was freigestellt wird. Fehlt die Ansicht auf diesem
     *        Geraet oder ist sie gerade ausgeblendet, wird der Halt
     *        uebersprungen - das Tablet-Layout hat nicht jede Ansicht des
     *        Telefons, und die Karte fuer ein wartendes Workout ist meistens
     *        gar nicht da.
     */
    data class Step(val targetId: Int, val titleRes: Int, val textRes: Int)

    private lateinit var overlay: FrameLayout
    private lateinit var spotlight: Spotlight
    private lateinit var bubble: View

    private var index = 0

    /** Das gerade erklaerte Ziel, und wo sein Loch zuletzt lag. */
    private var current: View? = null
    private var lastHole: RectF? = null

    private fun begin(startAt: Int) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            // Schluckt jede Beruehrung, die nicht auf der Blase landet.
            isClickable = true
            isFocusable = true
        }
        spotlight = Spotlight(activity)
        overlay.addView(spotlight, FrameLayout.LayoutParams(MATCH, MATCH))

        bubble = LayoutInflater.from(activity).inflate(R.layout.view_coach_bubble, overlay, false)
        overlay.addView(bubble)

        bubble.findViewById<MaterialButton>(R.id.btnCoachSkip).setOnClickListener { finish() }
        bubble.findViewById<MaterialButton>(R.id.btnCoachNext).setOnClickListener {
            index++
            saveStep()
            showStep()
        }

        root.addView(overlay)
        overlay.viewTreeObserver.addOnPreDrawListener(follow)
        index = startAt
        showStep()
    }

    /**
     * Zeigt den naechsten Halt, der ein Ziel hat.
     *
     * [View.requestRectangleOnScreen] holt das Ziel in den sichtbaren Bereich -
     * der Startbildschirm ist laenger als die Anzeige, und ein Loch ueber
     * etwas, das gerade nicht zu sehen ist, waere ein Loch ueber nichts.
     * Gemessen wird hier nichts: das uebernimmt [follow] bei jedem Zeichnen,
     * denn das Scrollen ist im naechsten Bild noch nicht durch.
     */
    private fun showStep() {
        while (index < steps.size && target(steps[index]) == null) index++

        if (index >= steps.size) {
            finish()
            return
        }

        val step = steps[index]
        val view = target(step) ?: return

        bubble.findViewById<TextView>(R.id.tvCoachProgress).text =
            activity.getString(R.string.coach_progress, index + 1, steps.size)
        bubble.findViewById<TextView>(R.id.tvCoachTitle).setText(step.titleRes)
        bubble.findViewById<TextView>(R.id.tvCoachText).setText(step.textRes)
        bubble.findViewById<MaterialButton>(R.id.btnCoachNext)
            .setText(if (index == steps.lastIndex) R.string.coach_done else R.string.coach_next)

        // Holt das Ziel in den sichtbaren Bereich; der Startbildschirm ist
        // laenger als die Anzeige.
        current = view
        lastHole = null
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
    }

    /**
     * Fuehrt Loch und Blase bei jedem Zeichnen nach.
     *
     * Einmal zu messen genuegt nicht: das Scrollen zum Ziel ist im naechsten
     * Bild noch nicht abgeschlossen, und die Daten der Crew treffen ohnehin
     * erst nach dem Start ein und verschieben alles darunter. Beides war zu
     * sehen - nach dem Drehen umrahmte der Kegel die Karte *ueber* der
     * gemeinten, weil er die Lage von vorher festgehalten hatte.
     *
     * Neu gelegt wird nur, wenn sich die Lage wirklich geaendert hat; sonst
     * loeste jedes Bild ein neues Messen der Blase aus.
     */
    private val follow = ViewTreeObserver.OnPreDrawListener {
        val view = current
        if (view != null && view.isShown) {
            val rect = holeFor(view)
            if (rect != lastHole) {
                lastHole = rect
                place(rect)
            }
        }
        true
    }

    /** Wo das Loch fuer dieses Ziel liegt, in Koordinaten der Ebene. */
    private fun holeFor(view: View): RectF {
        val here = IntArray(2).also { overlay.getLocationInWindow(it) }
        val there = IntArray(2).also { view.getLocationInWindow(it) }
        return RectF(
            (there[0] - here[0] - PADDING).toFloat(),
            (there[1] - here[1] - PADDING).toFloat(),
            (there[0] - here[0] + view.width + PADDING).toFloat(),
            (there[1] - here[1] + view.height + PADDING).toFloat()
        )
    }

    /** Legt das Loch auf das Ziel und die Blase daneben. */
    private fun place(hole: RectF) {
        spotlight.shine(hole)

        val margin = MARGIN
        val width = overlay.width - 2 * margin
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = bubble.measuredHeight

        // Unter das Loch, wenn es dort passt - sonst darueber. Passt es
        // nirgends, klebt die Blase am unteren Rand: lieber ueberdeckt sie
        // einen Teil des Ziels, als dass sie aus dem Bild laeuft.
        val below = hole.bottom + GAP
        val above = hole.top - GAP - height
        val top = when {
            below + height <= overlay.height - margin -> below.toInt()
            above >= margin -> above.toInt()
            else -> overlay.height - margin - height
        }

        (bubble.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = width
            this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            leftMargin = margin
            topMargin = top.coerceAtLeast(margin)
        }
        bubble.requestLayout()
        bubble.visibility = View.VISIBLE
    }

    private fun target(step: Step): View? =
        activity.findViewById<View>(step.targetId)?.takeIf { it.isShown }

    private fun finish() {
        current = null
        overlay.viewTreeObserver.removeOnPreDrawListener(follow)
        prefs(activity).edit().putBoolean(doneKey(id), true).remove(stepKey(id)).apply()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun saveStep() {
        prefs(activity).edit().putInt(stepKey(id), index).apply()
    }

    /**
     * Die dunkle Ebene mit dem Loch.
     *
     * Eine innere Klasse und keine eigene Datei: sie wird nur von hier
     * benutzt, steht in keinem Layout und waere anderswo ein Bauteil ohne
     * Zweck.
     */
    private class Spotlight(context: Context) : View(context) {

        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE0090C12.toInt()
        }
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * context.resources.displayMetrics.density
            color = ContextCompat.getColor(context, R.color.accent)
        }

        private val path = Path()
        private var hole: RectF? = null

        fun shine(rect: RectF) {
            hole = rect
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val rect = hole
            val radius = 18f * resources.displayMetrics.density

            if (rect == null) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
                return
            }

            // Zwei Konturen in einem Pfad: die aeussere fuellt, die innere
            // nimmt wieder weg. Das ist die Fuellregel EVEN_ODD.
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.drawPath(path, shade)
            canvas.drawRoundRect(rect, radius, radius, edge)
        }
    }

    companion object {

        private const val PREFS = "coach_tour"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** Luft zwischen Ziel und Lochrand, in Pixeln. */
        private const val PADDING = 12

        /** Abstand der Blase zum Bildschirmrand und zum Loch. */
        private const val MARGIN = 40
        private const val GAP = 24

        /** Die Einfuehrung des Startbildschirms. */
        const val HOME = "home"

        /**
         * Startet die Einfuehrung, wenn sie noch aussteht - sonst geschieht
         * nichts. Gedacht fuer den Aufruf aus `onResume`: der laeuft bei jedem
         * Zurueckkommen, und alles ausser dem ersten Mal ist ein Blick in die
         * Einstellungen.
         */
        fun start(activity: Activity, id: String, steps: List<Step>) {
            if (steps.isEmpty() || isDone(activity, id)) return

            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            // Erst zeichnen lassen: vorher haben die Ziele weder Groesse noch
            // Lage, und das Loch saesse in der linken oberen Ecke.
            root.doOnPreDraw {
                if (isDone(activity, id)) return@doOnPreDraw
                val startAt = prefs(activity).getInt(stepKey(id), 0)
                CoachTour(activity, id, steps).begin(startAt.coerceIn(0, steps.lastIndex))
            }
        }

        fun isDone(context: Context, id: String): Boolean =
            prefs(context).getBoolean(doneKey(id), false)

        /** Laesst die Einfuehrung beim naechsten Oeffnen wieder laufen. */
        fun reset(context: Context, id: String) {
            prefs(context).edit().remove(doneKey(id)).remove(stepKey(id)).apply()
        }

        private fun doneKey(id: String) = "${id}_done"
        private fun stepKey(id: String) = "${id}_step"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
