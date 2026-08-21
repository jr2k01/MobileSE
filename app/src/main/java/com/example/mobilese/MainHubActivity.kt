package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Startbildschirm: Crew-Name, Mitglieder, Top 3 und die letzten Aktivitaeten.
 *
 * Alle vier Bereiche stammen aus einem einzigen [CrewSnapshot]. Vorher holte
 * jeder Bereich seine Daten getrennt und innerhalb der Bereiche noch einmal pro
 * Mitglied - bei fuenf Mitgliedern kamen so ueber fuenfzig aufeinanderfolgende
 * Netzanfragen zusammen, jede mit voller Latenz. Jetzt sind es fuenf.
 */
class MainHubActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    private lateinit var tvCrewName: TextView
    private lateinit var llMembers: LinearLayout
    private lateinit var llLatestActivities: LinearLayout

    /** Die drei Podestplaetze, von Platz eins an. */
    private lateinit var podium: List<PodiumPlace>

    /**
     * Eine Saeule des Podests.
     *
     * Abgeblendet wird nur [avatarHolder], also das Bild samt Rahmen - und bei
     * Platz eins zusaetzlich Krone und Schein. Die Saeule selbst bleibt in
     * voller Farbe stehen: sie gehoert zum Podest, nicht zur Person, und ein
     * halb ausgegrautes Podest sah aus, als fehle etwas an der Anzeige.
     */
    private class PodiumPlace(val avatar: ImageView, val avatarHolder: View)

    private val voicePlayer = VoicePlayer { toast(R.string.playback_failed) }

    /** Laufender Ladevorgang, damit sich zwei Aufrufe nicht ueberholen. */
    private var loadJob: Job? = null

    // --- Schrittzahl aus Health Connect ---

    private lateinit var tvStepsCount: TextView
    private lateinit var tvStepsHint: TextView
    private lateinit var btnConnectHealth: Button
    /** Zuletzt an den Server gemeldeter Stand, um gleiche Werte nicht erneut zu schicken. */
    private var lastPublishedSteps: Long = -1

    private lateinit var flStepsGoal: View
    private lateinit var piStepsGoal: CircularProgressIndicator
    private lateinit var ivStepsGoalReached: ImageView

    /**
     * Fragt die Erlaubnis zum Lesen der Schritte an.
     *
     * Health Connect bringt seinen eigenen Dialog mit; wir bekommen nur
     * zurueck, welche Berechtigungen danach erteilt sind. Deshalb wird das
     * Ergebnis nicht ausgewertet, sondern schlicht neu geladen - das gibt
     * dieselbe Anzeige, egal ob der Nutzer zugestimmt, abgelehnt oder den
     * Dialog weggewischt hat.
     */
    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            granted ->
            if (granted.none { it in HealthAccess.ALL }) {
                toast(R.string.steps_permission_denied)
            }
            showStepsToday()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main_hub)

        repository = AppRepository.get(this)

        // Herunterziehen holt Crew und Schritte neu. Beides, weil beim
        // Zurueckkommen ebenfalls beides geholt wird - was die Geste tut, soll
        // sich nicht davon unterscheiden.
        setUpPullToRefresh {
            refresh()
            showStepsToday()
        }

        tvCrewName = findViewById(R.id.tvHomeCrewName)
        llMembers = findViewById(R.id.llMembersContainer)
        llLatestActivities = findViewById(R.id.llLatestActivitiesContainer)

        podium = listOf(
            PodiumPlace(findViewById(R.id.ivPodiumFirst), findViewById(R.id.llPodiumFirstAvatar)),
            PodiumPlace(findViewById(R.id.ivPodiumSecond), findViewById(R.id.cvPodiumSecond)),
            PodiumPlace(findViewById(R.id.ivPodiumThird), findViewById(R.id.cvPodiumThird))
        )

        tvStepsCount = findViewById(R.id.tvStepsCount)
        tvStepsHint = findViewById(R.id.tvStepsHint)
        btnConnectHealth = findViewById(R.id.btnConnectHealth)
        flStepsGoal = findViewById(R.id.flStepsGoal)
        piStepsGoal = findViewById(R.id.piStepsGoal)
        ivStepsGoalReached = findViewById(R.id.ivStepsGoalReached)
        btnConnectHealth.setOnClickListener {
            requestHealthPermissions.launch(HealthAccess.ALL)
        }

        // Steht unter der Liste der letzten Aktivitaeten statt in der
        // Navigationsleiste.
        findViewById<Button>(R.id.btnAllActivities).setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

        findViewById<View>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        findViewById<View>(R.id.llCrewSwitch).setOnClickListener { showCrewSwitcher() }

        setUpBottomNavigation()
    }

    /**
     * Die Ziele der unteren Leiste.
     *
     * Alle fuenf fuehren auf einen eigenen Bildschirm; der Startbildschirm
     * selbst steht nicht in der Leiste. Deshalb wird die Auswahl abgeschaltet -
     * sonst erschiene dauerhaft ein Eintrag als "hier bist du gerade", obwohl
     * man auf keinem davon ist.
     */
    private fun setUpBottomNavigation() {
        // NavigationBarView ist die gemeinsame Oberklasse: auf dem Telefon
        // liegt hier die untere Leiste, auf dem Tablet die senkrechte
        // Schiene. Der Code muss den Unterschied nicht kennen.
        val bottomNav = findViewById<NavigationBarView>(R.id.bottomNav)
        bottomNav.menu.setGroupCheckable(0, false, true)

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.navCrew -> CrewDetailsActivity::class.java
                R.id.navAddWorkout -> WorkoutTrackingActivity::class.java
                R.id.navLeaderboard -> LeaderboardActivity::class.java
                R.id.navMe -> MeActivity::class.java
                R.id.navSettings -> SettingsActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            startActivity(Intent(this, target))
            false
        }
    }

    override fun onResume() {
        super.onResume()
        // Nur hier laden, nicht zusaetzlich in onCreate: onResume laeuft beim
        // Start ohnehin direkt nach onCreate, sonst wuerde jeder Kaltstart alle
        // Abfragen doppelt ausloesen.
        refresh()
        // Ebenfalls hier, damit eine in den Systemeinstellungen erteilte oder
        // wieder entzogene Erlaubnis beim Zurueckkehren sofort greift.
        showStepsToday()
        // Auch hier und nicht in onCreate: die Uhr kann melden, waehrend die
        // App offen ist, und nach dem Eintragen soll die Karte verschwunden
        // sein, sobald man zurueckkommt.
        showPendingWorkout()
    }

    /**
     * Die Karte fuer ein Workout, das auf der Uhr aufgezeichnet wurde.
     *
     * Zeigt immer das aelteste: warten mehrere, waere eine Liste auf dem
     * Startbildschirm zu viel: nach dem Eintragen rueckt das naechste nach.
     */
    private fun showPendingWorkout() {
        val card = findViewById<View>(R.id.pendingWorkoutCard)
        val workout = PendingWorkouts.oldest(this)

        if (workout == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.tvPendingDetails).text =
            when (val average = workout.avgHeartRate) {
                null -> getString(R.string.pending_workout_details, workout.sport, workout.minutes)
                else -> getString(
                    R.string.pending_workout_details_pulse,
                    workout.sport,
                    workout.minutes,
                    average
                )
            }
        card.setOnClickListener {
            startActivity(WorkoutTrackingActivity.intent(this, workout))
        }
        card.setOnLongClickListener {
            askToDiscard(workout)
            true
        }
    }

    /**
     * Bietet an, ein Workout von der Uhr wegzuwerfen.
     *
     * Ohne diesen Weg gab es nur einen einzigen: eintragen. Wer die Uhr aus
     * Versehen gestartet oder das Training abgebrochen hat, waere die Karte
     * nicht mehr losgeworden - ausser durch ein Workout, das es nie gab.
     *
     * Auf langen Druck und nicht als eigener Knopf: das Eintragen ist der
     * Regelfall, das Wegwerfen die Ausnahme, und ein zweiter Knopf auf der
     * Karte machte den ersten kleiner. Mit Rueckfrage, weil es nicht rueckgaengig
     * zu machen ist - die Uhr hat den Datensatz nach dem Uebertragen abgegeben.
     */
    private fun askToDiscard(workout: PendingWorkout) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pending_discard_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.pending_discard_message,
                    workout.minutes,
                    workout.sport,
                    workout.minutes
                )
            )
            .setNegativeButton(R.string.cancel_btn, null)
            .setPositiveButton(R.string.discard_btn) { _, _ ->
                PendingWorkouts.remove(this, workout.endedAt)
                showPendingWorkout()
                toast(R.string.pending_discarded)
            }
            .show()
    }

    /**
     * Zeigt die heutigen Schritte an - oder das, was stattdessen zutrifft.
     *
     * Vier Faelle, und jeder sagt dem Nutzer etwas anderes: Zahl vorhanden,
     * Erlaubnis fehlt (dann der Knopf), Health Connect gibt es hier nicht, oder
     * die Abfrage ging schief. Bewusst kein stiller Nullwert - "0 Schritte"
     * waere fuer alle drei anderen Faelle schlicht gelogen.
     */
    private fun showStepsToday() {
        lifecycleScope.launch {
            when (val reading = HealthSteps.today(this@MainHubActivity)) {
                is HealthSteps.Reading.Steps -> {
                    tvStepsCount.text = formatSteps(reading.count)
                    tvStepsCount.visibility = View.VISIBLE
                    btnConnectHealth.visibility = View.GONE
                    showGoalRing(reading.count)
                    publishSteps(reading.count)
                }

                HealthSteps.Reading.NotAllowed -> {
                    hideSteps()
                    btnConnectHealth.visibility = View.VISIBLE
                    tvStepsHint.setText(R.string.steps_hint_not_allowed)
                }

                HealthSteps.Reading.Unavailable -> {
                    hideSteps()
                    tvStepsHint.setText(R.string.steps_hint_unavailable)
                }

                HealthSteps.Reading.Failed -> {
                    hideSteps()
                    tvStepsHint.setText(R.string.steps_hint_failed)
                }
            }
        }
    }

    /**
     * Fuellt den Ring nach dem Tagesziel.
     *
     * Ist es erreicht, tritt in der Mitte ein Haken an die Stelle der leeren
     * Flaeche - ein voller Kreis allein liesse sich zu leicht mit "fast
     * geschafft" verwechseln.
     */
    private fun showGoalRing(steps: Long) {
        val reached = StepGoal.isReached(steps)

        flStepsGoal.visibility = View.VISIBLE
        piStepsGoal.setProgressCompat(StepGoal.progressPercent(steps), true)
        ivStepsGoalReached.visibility = if (reached) View.VISIBLE else View.GONE

        tvStepsHint.text = when {
            // Bei erreichtem Ziel steht dort, was es gebracht hat, statt nur,
            // dass es erreicht ist.
            reached -> getString(R.string.steps_bonus_hint, StepGoal.BONUS_POINTS)
            steps == 0L -> getString(R.string.steps_hint_none_yet)
            else -> getString(R.string.steps_hint_goal, formatSteps(StepGoal.DAILY_STEPS.toLong()))
        }
    }

    /**
     * Hinterlegt den eigenen Tagesstand, damit die Crew den Ring sieht.
     *
     * Uebertragen wird nur die Tageszahl, keine einzelnen Messwerte. Der
     * Bildschirm wird oft betreten und verlassen; unveraenderte Werte noch
     * einmal zu schicken waere eine Netzanfrage ohne Wirkung.
     */
    private suspend fun publishSteps(steps: Long) {
        if (steps == lastPublishedSteps) return
        if (repository.saveTodaySteps(steps.toInt())) lastPublishedSteps = steps
    }

    /** Zahl und Ring verbergen - in allen Faellen ohne gueltigen Messwert. */
    private fun hideSteps() {
        tvStepsCount.visibility = View.GONE
        flStepsGoal.visibility = View.GONE
        btnConnectHealth.visibility = View.GONE
    }

    /** Tausendertrennung nach den Regeln der eingestellten Sprache. */
    private fun formatSteps(count: Long): String =
        NumberFormat.getIntegerInstance().format(count)

    override fun onStop() {
        super.onStop()
        voicePlayer.release()
    }

    // --- Daten ---

    private fun refresh() {
        val crewCode = repository.getJoinedCrewCode()
        if (crewCode == null) {
            tvCrewName.text = getString(R.string.no_crew_joined)
            llMembers.removeAllViews()
            clearPodium()
            llLatestActivities.removeAllViews()
            // Auch hier: ohne Crew gibt es nichts zu laden, aber der Kreis
            // dreht sich sonst weiter und behauptet das Gegenteil.
            finishRefreshing()
            return
        }

        // Bis der Name geladen ist, bleibt das Feld leer statt eine
        // Zwischenaussage zu treffen. Vorher stand hier der Vorgabetext aus
        // dem Layout - "Not joined any crew" - obwohl eine Crew vorhanden war
        // und nur noch nicht geladen.
        tvCrewName.text = ""

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // finally und nicht am Ende des Blocks: geht eine Abfrage schief
            // oder wird der Auftrag abgebrochen, weil inzwischen neu geladen
            // wird, drehte sich der Kreis sonst bis zum Verlassen weiter.
            try {
                val (crewName, snapshot) = coroutineScope {
                    val nameAsync = async { repository.getCrewName(crewCode) }
                    val snapshotAsync = async { repository.loadCrewSnapshot(crewCode) }
                    nameAsync.await() to snapshotAsync.await()
                }

                tvCrewName.text = getString(R.string.your_crew_prefix, crewName)
                showCrewLogo(snapshot.crewImageUrl)
                showMembers(snapshot)
                showStreak(snapshot)
                showChallenges(snapshot)
                showTopThree(snapshot)
                showLatestActivities(snapshot)
            } finally {
                finishRefreshing()
            }
        }
    }

    /**
     * Die Auswahl der angezeigten Crew.
     *
     * Der Wechsel aendert nur, welche Crew gerade gezeigt wird - Mitglied
     * bleibt man in allen. Deshalb wird danach schlicht neu geladen und nicht
     * etwa zum Startbildschirm zurueckgesetzt.
     *
     * Steht am Ende der Liste auch der Weg zu einer weiteren Crew: wer
     * umschalten will, will manchmal auch beitreten, und ein zweiter Knopf im
     * Kopfbereich waere dafuer zu viel.
     */
    private fun showCrewSwitcher() {
        lifecycleScope.launch {
            val crews = repository.getJoinedCrews()
            val active = repository.getJoinedCrewCode()

            val entries = crews.map {
                ChoiceAdapter.Entry(it.name, R.drawable.ic_group)
            } + ChoiceAdapter.Entry(getString(R.string.crew_switch_join), R.drawable.ic_add)

            MaterialAlertDialogBuilder(this@MainHubActivity)
                .setTitle(R.string.crew_switch_title)
                .setAdapter(ChoiceAdapter(this@MainHubActivity, entries)) { _, index ->
                    if (index == crews.size) {
                        startActivity(Intent(this@MainHubActivity, CrewLandingActivity::class.java))
                        return@setAdapter
                    }
                    val chosen = crews[index]
                    if (chosen.id == active) return@setAdapter

                    repository.setJoinedCrewCode(chosen.id)
                    toastFormatted(R.string.crew_switched, chosen.name)
                    refresh()
                }
                .setNegativeButton(R.string.cancel_btn, null)
                .show()
        }
    }

    /**
     * Die eigene Serie und der Aufschlag, den sie gerade bringt.
     *
     * Der Hinweis nennt nicht den Stand, sondern was der naechste Schritt
     * bringt - das ist der Grund, heute noch loszugehen. Auf der hoechsten
     * Stufe entfaellt er.
     */
    private suspend fun showStreak(snapshot: CrewSnapshot) {
        val userId = repository.currentUserId() ?: return
        val card = findViewById<View>(R.id.streakCard)
        val days = Streak.current(Streak.activeDays(userId, snapshot.activities, snapshot.stepDays))
        val multiplier = Streak.multiplierFor(days)

        card.findViewById<TextView>(R.id.tvStreakTitle).text =
            if (days <= 0) getString(R.string.streak_title_none)
            else resources.getQuantityString(R.plurals.streak_title, days, days)

        val toNext = Streak.daysToNextTier(days)
        card.findViewById<TextView>(R.id.tvStreakHint).text = when {
            days <= 0 -> getString(R.string.streak_hint_start)
            toNext == null -> getString(R.string.streak_hint_top)
            else -> resources.getQuantityString(
                R.plurals.streak_hint_next,
                toNext,
                toNext,
                decimal(Streak.multiplierFor(days + toNext))
            )
        }

        card.findViewById<TextView>(R.id.tvStreakMultiplier).text =
            getString(R.string.streak_multiplier, decimal(multiplier))
    }

    /** "1,1" statt "1.1000000000000001". */
    private fun decimal(value: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)


    /**
     * Oben links das Bild der Crew, solange sie eines hat - sonst das
     * CrewFit-Logo.
     *
     * Die Beschriftung fuer die Sprachausgabe wechselt mit: "CrewFit logo" waere
     * falsch, sobald dort das Bild der eigenen Crew steht.
     */
    private fun showCrewLogo(imageUrl: String?) {
        val logo = findViewById<ImageView>(R.id.ivHomeLogo)
        ImageLoader.into(logo, imageUrl, circular = true, placeholder = R.drawable.logo_new)
        logo.contentDescription = getString(
            if (imageUrl.isNullOrEmpty()) R.string.logo_desc else R.string.crew_image_desc
        )
    }

    private fun showMembers(snapshot: CrewSnapshot) {
        llMembers.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (member in snapshot.members) {
            val view = inflater.inflate(R.layout.item_member_profile_mini, llMembers, false)
            view.findViewById<TextView>(R.id.tvMemberName).text =
                DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
            // Antippen fuehrt zum Kurzprofil mit Kennzahlen und Medaillen.
            view.setOnClickListener {
                startActivity(MemberProfileActivity.intent(this, member.id))
            }
            ImageLoader.into(
                view.findViewById(R.id.ivMemberPhoto),
                member.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
            llMembers.addView(view)
        }
    }

    /**
     * Besetzt das Podest mit den ersten drei der Rangliste.
     *
     * Auf dem Podest steht nur das Profilbild - welcher Platz das ist, sagen
     * die Hoehe der Saeule und die Ziffer darauf. Name und Punktzahl stehen
     * weiterhin auf dem Ranglisten-Bildschirm.
     *
     * Hat die Crew weniger als drei Mitglieder, bleiben die hinteren Plaetze
     * abgeblendet stehen, statt zu verschwinden. Ein Podest, das je nach
     * Crew-Groesse ein oder zwei Saeulen hat, wuerde bei jedem Beitritt anders
     * aussehen.
     */
    /**
     * Die laufenden Challenges, seit sie keinen eigenen Reiter mehr haben.
     *
     * Kurzfassung: Art, Stand, Balken, gegebenenfalls die Frist. Wer mehr
     * wissen will - die Beitraege der einzelnen Mitglieder, oder eine neue
     * anlegen -, kommt ueber die Zeile in den Challenge-Bildschirm.
     *
     * Erledigte fallen heraus. Sie waeren keine laufenden mehr, und der
     * Startbildschirm fuellte sich mit der Zeit mit Abgehaktem.
     */
    private fun showChallenges(snapshot: CrewSnapshot) {
        val container = findViewById<LinearLayout>(R.id.llHomeChallenges)
        container.removeAllViews()

        val open = snapshot.challenges.filter { challenge ->
            val done = ChallengeManager.progressByMember(challenge, snapshot).sumOf { it.second }
            done < challenge.goal && !ChallengeDeadline.isOver(challenge.deadline)
        }

        findViewById<View>(R.id.tvHomeChallengesLabel).visibility =
            if (open.isEmpty()) View.GONE else View.VISIBLE
        if (open.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        for (challenge in open) {
            val view = inflater.inflate(R.layout.item_home_challenge, container, false)
            val type = ChallengeType.fromStored(challenge.type)
            val total = ChallengeManager.progressByMember(challenge, snapshot).sumOf { it.second }

            view.findViewById<TextView>(R.id.tvHomeChallengeTitle).setText(type.labelRes)
            view.findViewById<TextView>(R.id.tvHomeChallengeProgress).text =
                getString(R.string.home_challenge_progress, total, challenge.goal)

            val bar = view.findViewById<LinearProgressIndicator>(R.id.piHomeChallenge)
            bar.max = challenge.goal.coerceAtLeast(1)
            bar.setProgressCompat(total.coerceAtMost(bar.max), true)

            val deadline = view.findViewById<TextView>(R.id.tvHomeChallengeDeadline)
            val date = ChallengeDeadline.toDisplay(challenge.deadline)
            if (date.isEmpty()) {
                deadline.visibility = View.GONE
            } else {
                deadline.visibility = View.VISIBLE
                val daysLeft = ChallengeDeadline.daysLeft(challenge.deadline) ?: 0L
                deadline.text =
                    if (daysLeft <= 0L) getString(R.string.challenge_deadline_today)
                    else getString(R.string.challenge_deadline_days, date, daysLeft.toInt())
            }

            view.setOnClickListener {
                startActivity(Intent(this, CrewChallengesActivity::class.java))
            }
            container.addView(view)
        }
    }

    private fun showTopThree(snapshot: CrewSnapshot) {
        val ranking = Scoreboard.build(snapshot)

        podium.forEachIndexed { index, place ->
            val entry = ranking.getOrNull(index)
            val rank = index + 1

            if (entry == null) {
                showEmptyPlace(place, rank)
                return@forEachIndexed
            }

            place.avatarHolder.alpha = 1f
            // Der Name ist auf dem Podest nicht zu sehen; fuer die
            // Sprachausgabe gehoert er trotzdem dazu.
            place.avatar.contentDescription =
                getString(R.string.podium_place_desc, rank, entry.name)
            ImageLoader.into(
                place.avatar,
                entry.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )

            // Wie die Mitgliederleiste darueber fuehrt auch das Podest aufs
            // Profil. Vorher waren dieselben Personen einmal antippbar und
            // einmal nicht, je nachdem wo man sie erwischt hat.
            place.avatarHolder.isClickable = true
            place.avatarHolder.setOnClickListener {
                startActivity(MemberProfileActivity.intent(this, entry.userId))
            }
        }
    }

    private fun clearPodium() =
        podium.forEachIndexed { index, place -> showEmptyPlace(place, index + 1) }

    /** Ein unbesetzter Platz: abgeblendet, mit Platzhalter statt Bild. */
    private fun showEmptyPlace(place: PodiumPlace, rank: Int) {
        place.avatarHolder.alpha = EMPTY_PLACE_ALPHA
        place.avatar.setImageResource(R.drawable.ic_image)
        place.avatar.contentDescription = getString(R.string.podium_place_empty_desc, rank)

        // Muss geloescht werden, nicht nur uebersprungen: die Ansicht wird beim
        // Crew-Wechsel wiederverwendet, und ein stehengebliebener Zuhoerer
        // fuehrte auf das Profil aus der vorigen Crew.
        place.avatarHolder.setOnClickListener(null)
        place.avatarHolder.isClickable = false
    }

    private fun showLatestActivities(snapshot: CrewSnapshot) {
        llLatestActivities.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val nameById = snapshot.members.associate { it.id to DisplayName.of(it) }

        // Nach dem normalisierten ISO-Schluessel sortieren: ein Vergleich der
        // Anzeigetexte wuerde zuerst nach Tag und erst danach nach Monat und
        // Jahr sortieren.
        val latest = snapshot.activities
            .filter { it.userId in nameById }
            .sortedByDescending { ActivityTime.sortKey(it.timestamp) }
            .take(3)

        for (activity in latest) {
            val view = inflater.inflate(R.layout.item_feed_entry, llLatestActivities, false)
            val author = nameById[activity.userId]?.takeIf { it.isNotBlank() }

            view.findViewById<TextView>(R.id.tvLatestActivityUser).text =
                author ?: getString(R.string.unknown_member)

            // Wie in der vollen Liste: die Zeile fuehrt ins Workout. Vorher
            // liess sich nur dort etwas oeffnen, und dieselben drei Eintraege
            // auf dem Startbildschirm reagierten nicht - derselbe Eintrag
            // verhielt sich also je nach Bildschirm anders.
            view.setOnClickListener {
                startActivity(WorkoutDetailActivity.intent(this, activity, author))
            }
            view.findViewById<TextView>(R.id.tvLatestActivityInfo).text = activity.sport
            showTogether(view, activity, nameById)
            view.findViewById<TextView>(R.id.tvLatestActivityTime).text =
                ActivityTime.toDisplay(activity.timestamp)
            view.findViewById<TextView>(R.id.tvLatestActivityDuration).text =
                getString(R.string.duration_unit, activity.duration.toString())

            val btnPlay = view.findViewById<ImageButton>(R.id.btnPlayVoice)
            val voiceUrl = activity.voiceUrl
            if (voiceUrl.isNullOrEmpty()) {
                btnPlay.visibility = View.GONE
            } else {
                btnPlay.visibility = View.VISIBLE
                btnPlay.setOnClickListener { voicePlayer.play(voiceUrl) }
            }

            llLatestActivities.addView(view)
        }
    }

    /**
     * Die Zeile "Together: ..." unter einem gemeinsam absolvierten Workout.
     *
     * Sie steht im Feed und nicht nur in der Einzelansicht, weil sie sonst
     * niemand sieht: der Sinn des gemeinsamen Trainings ist, dass die Crew es
     * mitbekommt. Die Namen kommen aus den ohnehin geladenen Profilen der Crew,
     * kosten also keine zusaetzliche Abfrage.
     */
    private fun showTogether(view: View, activity: Activity, nameById: Map<String, String>) {
        val row = view.findViewById<View>(R.id.llLatestActivityTogether)
        val names = JointWorkout.participants(activity, nameById, getString(R.string.unknown_member))

        if (names.isEmpty()) {
            row.visibility = View.GONE
            return
        }

        row.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.tvLatestActivityTogether).text =
            getString(R.string.joint_workout_with, names.joinToString(", "))
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun toastFormatted(resId: Int, vararg args: Any) =
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_SHORT).show()

    private companion object {
        /** Deckkraft eines noch unbesetzten Podestplatzes. */
        const val EMPTY_PLACE_ALPHA = 0.35f
    }
}
