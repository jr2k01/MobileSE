package com.example.mobilese

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.MotionEvent
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
 * Die eine Ausnahme ist ein Halt mit [Step.tapTarget] - dort ist das
 * freigestellte Ziel der Weg weiter, und die Ebene loest seinen Klick selbst
 * aus (siehe [handleTouch]).
 *
 * **Der Fortschritt liegt in den Einstellungen und nicht im Speicher.** Dreht
 * jemand das Geraet, wird die Activity neu gebaut und diese Ebene ist weg; beim
 * naechsten Zeichnen geht es an derselben Stelle weiter statt von vorn.
 */
class CoachTour private constructor(
    private val activity: Activity,
    private val id: String,
    private val steps: List<Step>,
    private val remember: Boolean,
    private val station: String
) {

    /**
     * Ein Halt der Einfuehrung.
     *
     * @param targetId was freigestellt wird. Fehlt die Ansicht auf diesem
     *        Geraet oder ist sie gerade ausgeblendet, wird der Halt
     *        uebersprungen - das Tablet-Layout hat nicht jede Ansicht des
     *        Telefons, und die Karte fuer ein wartendes Workout ist meistens
     *        gar nicht da.
     * @param tapTarget ob es hier ueber das Ziel selbst weitergeht statt ueber
     *        einen Knopf. Damit endet die Einfuehrung dieses Bildschirms, also
     *        nur sinnvoll auf dem letzten Halt: der Weiter-Knopf verschwindet,
     *        und wer nicht auf das freigestellte Ziel tippt, kommt nur ueber
     *        Abbrechen hier weg.
     */
    data class Step(
        val targetId: Int,
        val titleRes: Int,
        val textRes: Int,
        val tapTarget: Boolean = false
    )

    private lateinit var overlay: FrameLayout
    private lateinit var spotlight: Spotlight
    private lateinit var bubble: View

    private var index = 0

    /** Das gerade erklaerte Ziel, und wo sein Loch zuletzt lag. */
    private var current: View? = null
    private var lastHole: RectF? = null

    /** Ob die laufende Beruehrung im Loch begonnen hat. */
    private var pressed = false

    private fun begin(startAt: Int) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        overlay = object : FrameLayout(activity) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                // Schluckt jede Beruehrung, die nicht auf der Blase landet:
                // sonst tippt man versehentlich auf das, was gerade erklaert
                // wird.
                return true
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            tag = TAG
            isFocusable = true
        }
        spotlight = Spotlight(activity)
        overlay.addView(spotlight, FrameLayout.LayoutParams(MATCH, MATCH))

        bubble = LayoutInflater.from(activity).inflate(R.layout.view_coach_bubble, overlay, false)
        overlay.addView(bubble)

        bubble.findViewById<MaterialButton>(R.id.btnCoachSkip).setOnClickListener { finish(true) }
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

        // "1 von 1" sagt nichts - ein Wegweiser besteht nur aus sich selbst.
        bubble.findViewById<TextView>(R.id.tvCoachProgress).apply {
            visibility = if (steps.size == 1) View.GONE else View.VISIBLE
            text = activity.getString(R.string.coach_progress, index + 1, steps.size)
        }
        bubble.findViewById<TextView>(R.id.tvCoachTitle).setText(step.titleRes)
        bubble.findViewById<TextView>(R.id.tvCoachText).setText(step.textRes)
        // Fuehrt der Halt weiter, gibt es keinen Knopf dafuer: der Weg ist das
        // freigestellte Ziel selbst.
        bubble.findViewById<MaterialButton>(R.id.btnCoachNext).apply {
            visibility = if (step.tapTarget) View.GONE else View.VISIBLE
            setText(if (index == steps.lastIndex) R.string.coach_done else R.string.coach_next)
        }
        pressed = false

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

    /**
     * Der Griff nach dem freigestellten Bereich.
     *
     * Auf einem Halt mit [Step.tapTarget] ist das Loch der einzige Weg weiter.
     * Die Beruehrung wird trotzdem geschluckt und der Klick danach selbst
     * ausgeloest, statt sie durchzureichen: durchgereicht wuerde die Ebene
     * mitten im Verteilen der Beruehrung entfernt, und das Loslassen bekaeme
     * sie nie zu sehen - wer im Loch drueckt und daneben loslaesst, haette den
     * Weg sonst schon hinter sich, ohne dass etwas passiert waere.
     */
    private fun handleTouch(event: MotionEvent) {
        val hole = lastHole
        val inside = hole != null &&
            index < steps.size && steps[index].tapTarget &&
            hole.contains(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressed = inside
            MotionEvent.ACTION_UP -> {
                val done = pressed && inside
                pressed = false
                if (done) handOver()
            }
            MotionEvent.ACTION_CANCEL -> pressed = false
        }
    }

    /**
     * Erst die Ebene abraeumen, dann das Ziel wirklich betaetigen.
     *
     * Steht die Uebergabe am Ende, ist dieser Bildschirm damit erklaert. Steht
     * sie mittendrin, ist es ein Ausflug: der naechste Halt wird gemerkt, und
     * beim Zurueckkommen geht es dort weiter, statt von vorn oder gar nicht.
     */
    private fun handOver() {
        val view = current
        if (index == steps.lastIndex) {
            finish()
        } else {
            index++
            saveStep()
            dismiss()
        }
        view?.performClick()
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

    /**
     * Beendet die Einfuehrung dieses Bildschirms.
     *
     * @param skipped ob abgebrochen wurde. Auf dem gefuehrten Weg heisst
     *        Abbrechen: **den ganzen Weg** abbrechen. Nur diesen einen
     *        Bildschirm zu ueberspringen liesse den Nutzer an der naechsten
     *        Station stehen, ohne dass ihm jemand gesagt haette, wohin.
     */
    private fun finish(skipped: Boolean = false) {
        val editor = prefs(activity).edit()
        if (remember) editor.putBoolean(doneKey(id), true).remove(stepKey(id))

        val position = Tours.JOURNEY.indexOf(station)
        if (position >= 0) {
            if (skipped) {
                Tours.JOURNEY.forEach { editor.putBoolean(doneKey(it), true) }
                editor.putInt(JOURNEY_AT, Tours.JOURNEY.size)
                editor.putBoolean(ARMED, false)
            } else if (remember) {
                // Ein Wegweiser rueckt nicht vor: er zeigt auf eine Station,
                // die noch vor sich hat, was sie zu erklaeren hat.
                editor.putInt(JOURNEY_AT, position + 1)
                // Die letzte Station ist durch - damit ist auch der Modus aus.
                if (position + 1 >= Tours.JOURNEY.size) editor.putBoolean(ARMED, false)
            }
        }
        editor.apply()
        dismiss()
    }

    /** Nimmt die Ebene wieder weg, ohne etwas zu merken. */
    private fun dismiss() {
        current = null
        overlay.viewTreeObserver.removeOnPreDrawListener(follow)
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun saveStep() {
        if (remember) prefs(activity).edit().putInt(stepKey(id), index).apply()
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

        /** Woran eine schon liegende Ebene zu erkennen ist. */
        private const val TAG = "coach_overlay"

        /** Die Station des gefuehrten Wegs, an der es weitergeht. */
        private const val JOURNEY_AT = "journey_at"

        /** Ob der Tutorialmodus laeuft. Ohne ihn zeigt sich keine Erklaerung. */
        private const val ARMED = "armed"
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
        fun start(
            activity: Activity,
            id: String,
            steps: List<Step>,
            remember: Boolean = true,
            station: String = id
        ) {
            if (!isRunning(activity)) return
            if (steps.isEmpty() || (remember && isDone(activity, id))) return

            // Auf dem gefuehrten Weg ist immer nur eine Station an der Reihe.
            // Wer zwischendurch woanders hinsieht, bekommt dort nichts - die
            // Einfuehrung wartet, wo sie stehengeblieben ist.
            val position = Tours.JOURNEY.indexOf(station)
            if (position >= 0 && position != prefs(activity).getInt(JOURNEY_AT, 0)) return

            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            if (root.findViewWithTag<View>(TAG) != null) return

            // Erst zeichnen lassen: vorher haben die Ziele weder Groesse noch
            // Lage, und das Loch saesse in der linken oberen Ecke.
            root.doOnPreDraw {
                if (remember && isDone(activity, id)) return@doOnPreDraw
                // Zwischen Anmeldung und Zeichnen kann eine zweite Ebene
                // entstanden sein: onResume laeuft oefter als man denkt.
                if (root.findViewWithTag<View>(TAG) != null) return@doOnPreDraw
                val startAt = if (remember) prefs(activity).getInt(stepKey(id), 0) else 0
                CoachTour(activity, id, steps, remember, station)
                    .begin(startAt.coerceIn(0, steps.lastIndex))
            }
        }

        /** Dieselbe Einfuehrung, nur mit ihrem Buendel aus [Tours]. */
        fun start(activity: Activity, tour: Tours.Tour) = start(activity, tour.id, tour.steps)

        /**
         * Setzt den gefuehrten Weg auf dem Startbildschirm fort.
         *
         * Der Weg fuehrt ueber sechs Bildschirme, und fuenf davon erreicht man
         * nur ueber die Leiste unten. Ist die naechste Station also nicht der
         * Startbildschirm selbst, steht hier ein einzelner Wegweiser auf ihrem
         * Reiter: ohne ihn endete die Einfuehrung bei jedem Zurueckkommen, und
         * niemand wuesste, wohin als naechstes.
         *
         * Der Wegweiser merkt sich nichts - er soll wiederkommen, solange
         * seine Station aussteht. Gedacht fuer `onResume`.
         */
        fun guide(activity: Activity) {
            val at = prefs(activity).getInt(JOURNEY_AT, 0)
            val id = Tours.JOURNEY.getOrNull(at) ?: return
            if (id == Tours.HOME.id) {
                start(activity, Tours.HOME)
                return
            }
            val sign = Tours.BRIDGE[id] ?: return
            // Eigene Kennung, damit er nichts vom Fortschritt der Station
            // ueberschreibt - aber ihre Stelle auf dem Weg, damit Abbrechen
            // hier dasselbe heisst wie ueberall: den ganzen Weg abbrechen.
            start(activity, "bridge_$id", listOf(sign), remember = false, station = id)
        }

        fun isDone(context: Context, id: String): Boolean =
            prefs(context).getBoolean(doneKey(id), false)

        /**
         * Schaltet den Tutorialmodus ein und stellt ihn auf Anfang.
         *
         * Es gibt genau zwei Wege hierher: den Willkommensbildschirm nach der
         * ersten Anmeldung ([WelcomeActivity]) und die Zeile in den
         * Einstellungen. Von allein laeuft nichts - wer die App schon kennt,
         * soll nicht bei jedem neuen Bildschirm eine Erklaerung wegtippen
         * muessen, und wer sie noch einmal sehen will, weiss, wo sie steht.
         *
         * Raeumt dabei alle Merker weg: "noch einmal" heisst von vorn, und
         * nicht dort weiter, wo jemand vor drei Monaten aufgehoert hat.
         */
        fun begin(context: Context) {
            prefs(context).edit().clear().putBoolean(ARMED, true).apply()
        }

        /** Ob der Tutorialmodus gerade laeuft. */
        fun isRunning(context: Context) = prefs(context).getBoolean(ARMED, false)

        private fun doneKey(id: String) = "${id}_done"
        private fun stepKey(id: String) = "${id}_step"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
