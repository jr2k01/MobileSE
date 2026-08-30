package com.example.mobilese

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Die Rangliste der Crew, umschaltbar zwischen Tabelle und Auswertung.
 *
 * Die Challenges standen frueher auf demselben Bildschirm oberhalb der
 * Rangliste und schoben sie nach unten; sie haben jetzt einen eigenen, siehe
 * [CrewChallengesActivity]. Faellige Belohnungen werden hier trotzdem noch
 * ausgeschuettet - die Punkte sollen ankommen, egal welchen der beiden
 * Bildschirme jemand oeffnet.
 *
 * Dasselbe Platzproblem hatte danach die Auswertung: drei Karten ueber der
 * Tabelle, die man jedes Mal wegscrollen musste. Beides steht deshalb jetzt
 * hinter einem Umschalter, mit der Rangliste vorne - sie ist der Grund, aus dem
 * der Bildschirm geoeffnet wird.
 */
class LeaderboardActivity : AppCompatActivity() {

    /** Welche der beiden Ansichten gerade zu sehen ist. */
    private enum class Tab { RANKING, ANALYTICS }

    private lateinit var repository: AppRepository
    private lateinit var llLeaderboard: LinearLayout
    private lateinit var crewCode: String
    private lateinit var memeView: CrewMemeView

    private var tab = Tab.RANKING
    private var loadJob: Job? = null

    /** Ob der angemeldete Nutzer gerade fuehrt - nur dann darf er aufhaengen. */
    private var isLeader = false

    private val pickMemeLauncher = GalleryPicker(this) { picked ->
        askForCaption { caption -> saveMeme(picked, caption) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_leaderboard)

        // Nur waehrend des Tutorialmodus, und dort einmal: sonst kostet
        // der Aufruf einen Blick in die Einstellungen und tut nichts.
        CoachTour.start(this, Tours.RANKING)
        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        llLeaderboard = findViewById(R.id.llLeaderboardContainer)
        memeView = CrewMemeView(
            findViewById(R.id.crewMeme),
            onChange = { startPickingMeme() },
            onRemove = { confirmRemoveMeme() }
        )
        setUpTopBar(R.string.crew_ranking)
        setUpPullToRefresh { load() }
        setUpTabs(savedInstanceState)
    }

    /**
     * Neu laden, sooft der Bildschirm nach vorn kommt.
     *
     * Wer aufhaengen darf, haengt am Rang, und der aendert sich waehrend die
     * App laeuft: traegt jemand anderes ein Workout ein, zieht er womoeglich
     * vorbei. Wurde nur beim Oeffnen geladen, behielt der bisherige Erste
     * seine Schaltflaechen, bis jemand von Hand herunterzog - und der neue
     * Erste bekam seine nicht. Nur hier und nicht zusaetzlich in onCreate:
     * onResume laeuft beim Start ohnehin direkt danach.
     */
    override fun onResume() {
        super.onResume()
        load()
    }

    /**
     * Der Umschalter zwischen Rangliste und Auswertung.
     *
     * Die gewaehlte Seite ueberlebt das Drehen des Geraets: sonst stuende man
     * nach dem Drehen wieder in der Rangliste, obwohl man sich gerade die
     * Auswertung angesehen hat.
     */
    private fun setUpTabs(savedInstanceState: Bundle?) {
        tab = savedInstanceState?.getString(STATE_TAB)?.let { Tab.valueOf(it) } ?: Tab.RANKING

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.tgLeaderboardTab)
        toggle.check(if (tab == Tab.ANALYTICS) R.id.btnTabAnalytics else R.id.btnTabRanking)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            tab = if (checkedId == R.id.btnTabAnalytics) Tab.ANALYTICS else Tab.RANKING
            showTab()
        }

        showTab()
    }

    private fun showTab() {
        findViewById<View>(R.id.llRankingSection).visibility =
            if (tab == Tab.RANKING) View.VISIBLE else View.GONE
        findViewById<View>(R.id.llAnalyticsSection).visibility =
            if (tab == Tab.ANALYTICS) View.VISIBLE else View.GONE

        // Beide Seiten teilen sich eine ScrollView. Ohne das Zuruecksetzen
        // begaenne die kuerzere dort, wo die laengere stand.
        findViewById<ScrollView>(R.id.svLeaderboard).scrollTo(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
    }

    // --- Bild der Nummer eins ---

    private fun startPickingMeme() {
        // Zweite Pruefung neben der ausgeblendeten Schaltflaeche: zwischen dem
        // Laden und dem Antippen kann jemand vorbeigezogen sein.
        if (!isLeader) {
            toast(R.string.meme_not_leader)
            return
        }
        showPictureChoice()
    }

    /**
     * Die Auswahl: das Raster der vorgegebenen Bilder, daneben der Weg ueber
     * die eigene Galerie.
     *
     * Beides in einem Dialog statt hintereinander - vorgeschaltet zu fragen,
     * woher das Bild kommen soll, waere ein Schritt mehr fuer eine Frage, die
     * sich beim Hinsehen von selbst beantwortet.
     *
     * Die Auswahl wird bei jedem Oeffnen geholt: sie liegt in einem Ordner im
     * Bucket und kann sich geaendert haben, ohne dass die App neu gebaut wurde.
     */
    private fun showPictureChoice() {
        lifecycleScope.launch {
            val presets = repository.listMemePresets()

            val view = layoutInflater.inflate(R.layout.dialog_meme_picker, null)
            val dialog = MaterialAlertDialogBuilder(this@LeaderboardActivity)
                .setTitle(R.string.meme_choose_title)
                .setView(view)
                .setNegativeButton(R.string.cancel_btn, null)
                .setNeutralButton(R.string.meme_from_gallery) { _, _ ->
                    pickMemeLauncher.open()
                }
                .show()

            view.findViewById<View>(R.id.tvMemePresetsEmpty).visibility =
                if (presets.isEmpty()) View.VISIBLE else View.GONE

            fillPresetGrid(view.findViewById(R.id.glMemePresets), presets, dialog)
        }
    }

    private fun fillPresetGrid(grid: GridLayout, presets: List<MemePreset>, dialog: DialogInterface) {
        grid.removeAllViews()

        presets.forEach { preset ->
            val tile = layoutInflater.inflate(R.layout.item_meme_preset, grid, false)
            val image = tile.findViewById<ImageView>(R.id.ivMemePreset)

            ImageLoader.into(image, preset.url, placeholder = android.R.drawable.ic_menu_gallery)
            image.contentDescription = getString(R.string.meme_preset_desc, preset.name)

            tile.setOnClickListener {
                dialog.dismiss()
                askForCaption { caption -> savePreset(preset, caption) }
            }
            grid.addView(tile)
        }
    }

    /** Der Spruch ist freiwillig; leer gelassen zeigt die Karte nur das Bild. */
    private fun askForCaption(onConfirmed: (String) -> Unit) {
        val input = EditText(this).apply {
            setHint(R.string.meme_caption_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val container = FrameLayout(this).apply {
            val padding = resources.getDimensionPixelSize(R.dimen.card_padding)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.meme_pick_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_btn, null)
            .setPositiveButton(R.string.add_btn) { _, _ ->
                onConfirmed(input.text.toString())
            }
            .show()
    }

    /** Ein Bild aus der Auswahl: es liegt schon im Bucket, nichts zu laden. */
    private fun savePreset(preset: MemePreset, caption: String) {
        lifecycleScope.launch {
            val saved = repository.saveCrewMemePreset(crewCode, preset, caption)
            toast(if (saved) R.string.meme_saved else R.string.meme_save_failed)
            if (saved) load()
        }
    }

    private fun saveMeme(uri: Uri, caption: String) {
        lifecycleScope.launch {
            // Dieselbe Verkleinerung wie beim Profilbild: ein Foto aus der
            // Galerie ist schnell mehrere Megabyte gross und muesste von jedem
            // Crew-Mitglied heruntergeladen werden.
            val file = File(cacheDir, "crew_meme.jpg")
            val written = withContext(Dispatchers.IO) {
                ImageLoader.saveScaled(this@LeaderboardActivity, uri, file) != null
            }
            if (!written) {
                toast(R.string.meme_save_failed)
                return@launch
            }

            val saved = repository.saveCrewMeme(crewCode, file.absolutePath, caption)
            toast(if (saved) R.string.meme_saved else R.string.meme_save_failed)
            if (saved) load()
        }
    }

    private fun confirmRemoveMeme() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.meme_remove_title)
            .setMessage(R.string.meme_remove_message)
            .setNegativeButton(R.string.cancel_btn, null)
            .setPositiveButton(R.string.meme_remove) { _, _ ->
                lifecycleScope.launch {
                    if (repository.deleteCrewMeme(crewCode)) {
                        toast(R.string.meme_removed)
                        load()
                    }
                }
            }
            .show()
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun load() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val snapshot = repository.loadCrewSnapshot(crewCode)

                // Wurde etwas gutgeschrieben, ist der Snapshot veraltet und die
                // Rangliste wuerde ohne Neuladen die alten Punktstaende zeigen.
                val current =
                    if (repository.awardCompletedChallenges(snapshot)) repository.loadCrewSnapshot(crewCode)
                    else snapshot

                showLeaderboard(current)
            } finally {
                finishRefreshing()
            }
        }
    }

    private suspend fun showLeaderboard(snapshot: CrewSnapshot) {
        CrewChartsView(findViewById(android.R.id.content)).show(snapshot)

        llLeaderboard.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val ranking = Scoreboard.build(snapshot)
        // Massstab fuer die Balkenlaenge: der Punktestand an der Spitze.
        val leaderPoints = ranking.firstOrNull()?.points ?: 0

        showMeme(ranking, snapshot)

        ranking.forEachIndexed { index, entry ->
            val view = inflater.inflate(R.layout.item_leaderboard_entry, llLeaderboard, false)
            view.findViewById<TextView>(R.id.tvRank).text = (index + 1).toString()
            view.findViewById<TextView>(R.id.tvLeaderboardName).text = entry.name
            view.findViewById<TextView>(R.id.tvPoints).text =
                getString(R.string.points_unit, entry.points)
            ImageLoader.into(
                view.findViewById<ImageView>(R.id.ivLeaderboardPhoto),
                entry.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
            showStepRing(view, entry.todaySteps)
            showPointsSplit(view, CrewStats.pointsSplit(entry.userId, snapshot), leaderPoints)
            llLeaderboard.addView(view)
        }
    }

    /**
     * Die Karte mit dem Bild der Nummer eins.
     *
     * Fuehrend ist, wer in der Rangliste oben steht - bei null Punkten fuer
     * alle waere das der Erste einer beliebigen Reihenfolge, deshalb zaehlt nur
     * ein Stand ueber null. Sonst duerfte in einer frischen Crew der
     * alphabetisch Erste aufhaengen, ohne etwas geleistet zu haben.
     */
    private suspend fun showMeme(ranking: List<Scoreboard.Entry>, snapshot: CrewSnapshot) {
        val leader = ranking.firstOrNull()?.takeIf { it.points > 0 }
        isLeader = leader != null && leader.userId == repository.currentUserId()

        val meme = repository.getCrewMeme(crewCode)
        val owner = meme?.let { put ->
            snapshot.members.firstOrNull { it.id == put.userId }?.let { DisplayName.of(it) }
        }

        memeView.show(meme, owner, isLeader)
    }

    /**
     * Der Balken unter dem Namen, der die Herkunft der Punkte zeigt.
     *
     * Die Breiten kommen aus den Gewichten, damit sie sich die Zeile teilen,
     * ohne dass hier mit Pixeln gerechnet werden muss. Ein Anteil von null
     * bekommt Gewicht null und verschwindet damit ganz - sonst bliebe ein
     * Farbstrich stehen, den es nicht gibt.
     */
    private fun showPointsSplit(row: View, split: CrewStats.PointsSplit, leaderPoints: Int) {
        val bar = row.findViewById<View>(R.id.llPointsSplit)
        if (split.total <= 0) {
            bar.visibility = View.INVISIBLE
            return
        }
        bar.visibility = View.VISIBLE

        setSplitWeight(row, R.id.vSplitWorkouts, split.workouts)
        setSplitWeight(row, R.id.vSplitChallenges, split.challenges)
        setSplitWeight(row, R.id.vSplitSteps, split.steps)
        setSplitWeight(row, R.id.vSplitRest, (leaderPoints - split.total).coerceAtLeast(0))
    }

    private fun setSplitWeight(row: View, id: Int, value: Int) {
        val part = row.findViewById<View>(id)
        part.layoutParams = (part.layoutParams as LinearLayout.LayoutParams).apply {
            weight = value.toFloat()
        }
    }

    /**
     * Der Ring zum heutigen Schrittziel in einer Ranglistenzeile.
     *
     * Der Ring steht auch bei null Schritten da, nur leer. Ihn wegzulassen
     * waere zweideutig: dann saehe ein Mitglied ohne Health Connect genauso aus
     * wie eines, das den Bildschirm gerade nicht geoeffnet hat - und die Zeilen
     * waeren unterschiedlich breit.
     */
    private fun showStepRing(row: View, steps: Int) {
        row.findViewById<CircularProgressIndicator>(R.id.piStepsGoal)
            .setProgressCompat(StepGoal.progressPercent(steps.toLong()), false)

        row.findViewById<ImageView>(R.id.ivStepsGoalReached).visibility =
            if (StepGoal.isReached(steps.toLong())) View.VISIBLE else View.GONE

        row.findViewById<View>(R.id.flStepsGoal).contentDescription =
            if (StepGoal.isReached(steps.toLong())) getString(R.string.steps_hint_goal_reached)
            else getString(R.string.steps_ring_desc, steps, StepGoal.DAILY_STEPS)
    }

    private companion object {
        const val STATE_TAB = "leaderboard_tab"
    }
}
