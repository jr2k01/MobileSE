package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.NumberFormat

/**
 * Ein einzelnes Workout in voller Laenge.
 *
 * Die Liste der Aktivitaeten zeigt nur noch eine Kurzfassung je Eintrag; alles
 * Uebrige steht hier: Foto, Kartenausschnitt, Sprachnotiz, Dauer, Distanz, Ort,
 * Zeitpunkt und - in der Crew-Ansicht - von wem.
 *
 * Das Workout wird als Ganzes mitgegeben statt nur seine Kennung. Die Liste hat
 * die Daten bereits geladen; sie hier erneut zu holen waere eine zweite Abfrage
 * fuer etwas, das schon vorliegt - und aeltere Zeilen haben nicht einmal eine
 * Kennung, mit der sich nachladen liesse.
 */
class WorkoutDetailActivity : AppCompatActivity() {

    private val voicePlayer = VoicePlayer { toast(R.string.playback_failed) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_detail)
        // Nur waehrend des Tutorialmodus, und dort einmal: sonst kostet
        // der Aufruf einen Blick in die Einstellungen und tut nichts.
        CoachTour.start(this, Tours.DETAIL)
        setUpTopBar(R.string.workout_detail_title)
        repository = AppRepository.get(this)

        val activity = readActivity()
        if (activity == null) {
            toast(R.string.workout_not_found)
            finish()
            return
        }

        show(activity, intent.getStringExtra(EXTRA_AUTHOR))
        setUpFeedback(activity.id)
    }

    override fun onStop() {
        super.onStop()
        voicePlayer.release()
    }

    private fun readActivity(): Activity? {
        val raw = intent.getStringExtra(EXTRA_ACTIVITY) ?: return null
        return try {
            Json.decodeFromString(Activity.serializer(), raw)
        } catch (e: Exception) {
            null
        }
    }

    // --- Reaktionen und Kommentare ---

    private lateinit var repository: AppRepository

    /** Die Kennung des gezeigten Workouts, oder null bei aelteren Eintraegen. */
    private var activityId: String? = null

    /** Wer angemeldet ist - fuer "meine Reaktion" und "mein Kommentar". */
    private var myUserId: String? = null

    /**
     * Richtet Reaktionen und Kommentare ein.
     *
     * Beides braucht die Kennung der Aktivitaet. Aeltere Eintraege haben keine
     * - die stammen aus der Zeit vor der Umstellung auf Supabase. Statt eines
     * Bereichs, der bei jedem Antippen nur eine Fehlermeldung liefert, steht
     * dann ein Satz da, der sagt warum.
     */
    private fun setUpFeedback(id: String?) {
        activityId = id

        val block = findViewById<View>(R.id.llFeedback)
        val note = findViewById<View>(R.id.tvFeedbackUnavailable)

        if (id.isNullOrEmpty()) {
            block.visibility = View.GONE
            note.visibility = View.VISIBLE
            return
        }

        block.visibility = View.VISIBLE
        note.visibility = View.GONE

        buildReactionButtons(id)
        buildPresetChips(id)

        findViewById<MaterialButton>(R.id.btnSendComment).setOnClickListener { sendTypedComment(id) }

        lifecycleScope.launch {
            myUserId = repository.currentUserId()
            loadFeedback(id)
        }
    }

    /**
     * Ein Knopf je Zeichen aus [Reactions.ALL].
     *
     * Aus der Liste und nicht aus dem Layout: ein sechstes Zeichen dort soll
     * genuegen, damit es hier erscheint.
     */
    private fun buildReactionButtons(activityId: String) {
        val row = findViewById<LinearLayout>(R.id.llReactions)
        row.removeAllViews()
        reactionButtons.clear()

        val inflater = LayoutInflater.from(this)
        Reactions.ALL.forEach { emoji ->
            val button = inflater.inflate(R.layout.part_reaction_button, row, false) as MaterialButton
            button.text = emoji
            button.contentDescription = getString(R.string.reaction_desc, emoji)
            button.setOnClickListener { react(activityId, emoji) }
            row.addView(button)
            reactionButtons[emoji] = button
        }
    }

    private val reactionButtons = linkedMapOf<String, MaterialButton>()

    /**
     * Reagieren, oder die Reaktion zuruecknehmen.
     *
     * Die Knoepfe werden waehrenddessen gesperrt: zweimal schnell antippen
     * schickte sonst zwei Anfragen los, deren Reihenfolge nicht feststeht, und
     * am Ende zeigte der Bildschirm etwas anderes als die Datenbank.
     */
    private fun react(activityId: String, emoji: String) {
        lifecycleScope.launch {
            reactionButtons.values.forEach { it.isEnabled = false }
            val saved = repository.setReaction(activityId, emoji)
            reactionButtons.values.forEach { it.isEnabled = true }

            if (!saved) {
                toast(R.string.reaction_failed)
                return@launch
            }
            loadFeedback(activityId)
        }
    }

    /** Fertige Zurufe. Antippen schickt sofort ab - das ist ihr Zweck. */
    private fun buildPresetChips(activityId: String) {
        val group = findViewById<ChipGroup>(R.id.cgCommentPresets)
        group.removeAllViews()

        resources.getStringArray(R.array.comment_presets).forEach { preset ->
            val chip = Chip(this)
            chip.text = preset
            chip.isCheckable = false
            chip.setOnClickListener { sendComment(activityId, preset) }
            group.addView(chip)
        }

        // Chips sind fokussierbar, und der zuletzt eingehaengte zieht den
        // Scrollbereich zu sich - die Reihe stuende sonst von Anfang an ganz
        // rechts, mit den ersten Vorschlaegen ausserhalb des Bildes. Nach dem
        // Einhaengen, deshalb per post.
        val strip = findViewById<HorizontalScrollView>(R.id.hsvCommentPresets)
        strip.post { strip.scrollX = 0 }
    }

    private fun sendTypedComment(activityId: String) {
        val field = findViewById<EditText>(R.id.etComment)
        val text = field.text.toString()
        if (text.isBlank()) return
        sendComment(activityId, text) { field.setText("") }
    }

    private fun sendComment(activityId: String, text: String, onSent: () -> Unit = {}) {
        lifecycleScope.launch {
            if (!repository.addComment(activityId, text)) {
                toast(R.string.comment_failed)
                return@launch
            }
            onSent()
            loadFeedback(activityId)
        }
    }

    private suspend fun loadFeedback(activityId: String) {
        val feedback = repository.loadActivityFeedback(activityId)
        showReactions(feedback)
        showComments(activityId, feedback)
    }

    /**
     * Zahlen an den Zeichen, und die eigene Wahl hervorgehoben.
     *
     * Ohne Reaktion steht am Knopf nur das Zeichen - eine "0" daneben zaehlte
     * etwas, das nicht stattgefunden hat.
     */
    private fun showReactions(feedback: ActivityFeedback) {
        val counts = Reactions.countsOf(feedback.reactions)
        val mine = Reactions.chosenBy(feedback.reactions, myUserId)

        reactionButtons.forEach { (emoji, button) ->
            val count = counts[emoji] ?: 0
            button.text = if (count == 0) emoji else getString(R.string.reaction_with_count, emoji, count)

            val chosen = emoji == mine
            button.backgroundTintList = ColorStateList.valueOf(
                if (chosen) themeColor(com.google.android.material.R.attr.colorSecondaryContainer)
                else Color.TRANSPARENT
            )
            button.setTextColor(
                if (chosen) themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                else themeColor(com.google.android.material.R.attr.colorOnSurface)
            )
        }
    }

    private fun showComments(activityId: String, feedback: ActivityFeedback) {
        val container = findViewById<LinearLayout>(R.id.llComments)
        container.removeAllViews()

        findViewById<View>(R.id.tvCommentsEmpty).visibility =
            if (feedback.comments.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        feedback.comments.forEach { comment ->
            val row = inflater.inflate(R.layout.item_comment_row, container, false)
            val author = feedback.authors[comment.userId]

            row.findViewById<TextView>(R.id.tvCommentAuthor).text =
                author?.let { DisplayName.of(it) }?.ifEmpty { null }
                    ?: getString(R.string.unknown_member)
            row.findViewById<TextView>(R.id.tvCommentText).text = comment.text
            row.findViewById<TextView>(R.id.tvCommentTime).text =
                comment.createdAt?.let { ActivityTime.toDisplay(it) }.orEmpty()

            ImageLoader.into(
                row.findViewById(R.id.ivCommentPhoto),
                author?.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )

            // Loeschen nur beim eigenen Kommentar - fremde laesst die Datenbank
            // ohnehin nicht zu, und ein Knopf, der nur eine Fehlermeldung
            // einbringt, gehoert nicht dorthin.
            val delete = row.findViewById<MaterialButton>(R.id.btnDeleteComment)
            val id = comment.id
            if (id != null && comment.userId == myUserId) {
                delete.visibility = View.VISIBLE
                delete.setOnClickListener { deleteComment(activityId, id) }
            } else {
                delete.visibility = View.GONE
            }

            container.addView(row)
        }
    }

    private fun deleteComment(activityId: String, commentId: String) {
        lifecycleScope.launch {
            if (!repository.deleteComment(commentId)) {
                toast(R.string.comment_delete_failed)
                return@launch
            }
            toast(R.string.comment_deleted)
            loadFeedback(activityId)
        }
    }

    private fun themeColor(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun show(activity: Activity, author: String?) {
        findViewById<ImageView>(R.id.ivDetailSport)
            .setImageResource(Sports.iconFor(activity.sport))
        findViewById<TextView>(R.id.tvDetailSport).text = activity.sport
        findViewById<TextView>(R.id.tvDetailDate).text =
            ActivityTime.toDisplay(activity.timestamp)

        // In der eigenen Liste waere "von mir" bei jedem Eintrag ueberfluessig.
        findViewById<TextView>(R.id.tvDetailAuthor).apply {
            if (author == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.workout_by, author)
                visibility = View.VISIBLE
            }
        }

        showTogether(activity)
        showStats(activity)
        showPhoto(activity.photoUrl)
        showLocation(activity)
        showVoice(activity.voiceUrl)
    }

    /**
     * Die Zeile mit allen, die zusammen trainiert haben.
     *
     * Genannt werden auch die, die nicht in der eigenen Crew sind: gespeichert
     * ist die Kennung, und wer sie liest, soll einen Namen sehen und keine
     * Zeichenkette aus dreissig Zeichen. Die Namen werden nachgeladen, weil in
     * der Aktivitaet nur Kennungen stehen - aber in einer einzigen Abfrage.
     *
     * Bis sie da sind, bleibt die Zeile unsichtbar. Sie erst leer einzublenden
     * und dann zu fuellen liesse den Bildschirm unter dem Finger springen.
     */
    private fun showTogether(activity: Activity) {
        val row = findViewById<View>(R.id.llDetailTogether)
        if (!JointWorkout.isJoint(activity)) {
            row.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val ids = listOf(activity.userId) + activity.partnerIds.orEmpty()
            val nameById = repository.getProfilesByIds(ids)
                .associate { it.id to DisplayName.of(it) }
            val names = JointWorkout.participants(
                activity,
                nameById,
                getString(R.string.unknown_member)
            )

            row.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailTogether).apply {
                text = getString(R.string.joint_workout_with, names.joinToString(", "))
                contentDescription =
                    getString(R.string.joint_workout_desc, names.joinToString(", "))
            }
        }
    }

    private fun showStats(activity: Activity) {
        setStat(
            R.id.statDuration,
            getString(R.string.duration_unit, activity.duration.toString()),
            R.string.detail_stat_duration
        )
        // Ohne Distanz - Yoga, Kraftraum - steht dort ein Strich statt "0 km",
        // was nach einer gemessenen Null aussaehe.
        setStat(
            R.id.statDistance,
            if (activity.distance > 0) {
                getString(R.string.detail_distance_km, formatKm(activity.distance))
            } else {
                getString(R.string.value_none)
            },
            R.string.detail_stat_distance
        )
        setStat(
            R.id.statPoints,
            PointsCalculator.calculateWorkoutPoints(
                activity.duration,
                WorkoutIntensity.fromName(activity.intensity)
            ).toString(),
            R.string.detail_stat_points
        )

        // Puls nur, wenn eine Uhr etwas aufgezeichnet hat. Eine leere Kachel
        // mit Strich wuerde behaupten, der Wert fehle - dabei war schlicht
        // keine Uhr dabei.
        val heartRate = activity.avgHeartRate
        val heartRateTile = findViewById<View>(R.id.statHeartRate)
        heartRateTile.visibility = if (heartRate == null) View.GONE else View.VISIBLE
        if (heartRate != null) {
            setStat(R.id.statHeartRate, heartRate.toString(), R.string.detail_stat_heart_rate)
            // Der Hoechstwert steht nicht als eigene Kachel da - dafuer ist er
            // zu beilaeufig -, sondern nur fuer die Sprachausgabe.
            activity.maxHeartRate?.let { max ->
                heartRateTile.contentDescription =
                    getString(R.string.detail_heart_rate_desc, heartRate, max)
            }
        }
    }

    private fun setStat(containerId: Int, value: String, labelRes: Int) {
        val card = findViewById<View>(containerId)
        card.findViewById<TextView>(R.id.tvStatValue).text = value
        card.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
    }

    private fun showPhoto(photoUrl: String?) {
        val card = findViewById<View>(R.id.cvDetailPhoto)
        if (photoUrl.isNullOrEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        ImageLoader.into(
            findViewById(R.id.ivDetailPhoto),
            photoUrl,
            placeholder = android.R.drawable.ic_menu_gallery
        )
    }

    private fun showLocation(activity: Activity) {
        val row = findViewById<View>(R.id.llDetailLocation)
        if (activity.location.isNullOrEmpty()) {
            row.visibility = View.GONE
        } else {
            row.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailLocation).text = activity.location
        }

        val latitude = activity.latitude
        val longitude = activity.longitude
        if (latitude == null || longitude == null) return

        lifecycleScope.launch {
            val map = StaticMap.preview(
                this@WorkoutDetailActivity,
                latitude,
                longitude,
                ContextCompat.getColor(this@WorkoutDetailActivity, R.color.primary)
            ) ?: return@launch

            findViewById<ImageView>(R.id.ivDetailMap).setImageBitmap(map)
            findViewById<View>(R.id.cvDetailMap).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailMapAttribution).apply {
                text = StaticMap.ATTRIBUTION
                visibility = View.VISIBLE
            }
        }
    }

    private fun showVoice(voiceUrl: String?) {
        val button = findViewById<MaterialButton>(R.id.btnDetailVoice)
        if (voiceUrl.isNullOrEmpty()) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.setOnClickListener { voicePlayer.play(voiceUrl) }
    }

    /** Nachkommastellen nur, wenn es welche gibt: "5 km" statt "5,0 km". */
    private fun formatKm(distance: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(distance)

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_AUTHOR = "author"

        /** Einziger Weg hierher, damit die Schluessel der Extras nur hier stehen. */
        fun intent(context: Context, activity: Activity, author: String?): Intent =
            Intent(context, WorkoutDetailActivity::class.java)
                // Serializer ausdruecklich benannt: mit der abgekuerzten Form
                // greift die Ueberladung mit Strategie-Parameter.
                .putExtra(EXTRA_ACTIVITY, Json.encodeToString(Activity.serializer(), activity))
                .putExtra(EXTRA_AUTHOR, author)
    }
}
