package com.example.mobilese

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Die Challenges der Crew mit Fortschritt und Beitraegen der Mitglieder.
 *
 * Stand frueher oberhalb der Rangliste auf demselben Bildschirm. Dort war es
 * nur zu finden, wer die Rangliste oeffnete, und schob diese aus dem Blick.
 * Jetzt ein eigener Eintrag in der Navigationsleiste.
 */
class CrewChallengesActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var container: LinearLayout
    private lateinit var crewCode: String

    private var loadJob: Job? = null

    /**
     * Der Stand der gegnerischen Crews, nach Crew-Code.
     *
     * Wird beim Laden einmal fuer alle laufenden Battles geholt und nicht je
     * Karte: zwei Battles gegen dieselbe Crew wuerden sonst zweimal dieselben
     * Aktivitaeten holen.
     */
    private var opponents: Map<String, OpponentProgress> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_crew_challenges)

        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        container = findViewById(R.id.llChallengesContainer)

        setUpTopBar(R.string.crew_challenges_title)
        setUpPullToRefresh { load() }
        findViewById<Button>(R.id.btnLaunchChallenge).setOnClickListener { showAddChallengeDialog() }

        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val snapshot = repository.loadCrewSnapshot(crewCode)

                // Erst faellige Belohnungen schreiben, dann zeichnen - sonst zeigte
                // die Rangliste beim naechsten Oeffnen noch die alten Punktstaende.
                val current =
                    if (repository.awardCompletedChallenges(snapshot)) repository.loadCrewSnapshot(crewCode)
                    else snapshot

                opponents = loadOpponents(current)
                showChallenges(current)
            } finally {
                finishRefreshing()
            }
        }
    }

    /**
     * Holt den Stand aller Crews, gegen die gerade ein Battle laeuft.
     *
     * Nebenlaeufig und ohne Doppelungen: bei zwei Battles gegen dieselbe Crew
     * wird ihr Stand einmal geholt. Ein abgelehnter Battle bleibt aussen vor -
     * dort gibt es nichts zu vergleichen.
     */
    private suspend fun loadOpponents(snapshot: CrewSnapshot): Map<String, OpponentProgress> {
        val codes = snapshot.challenges
            .filter { it.isBattle && !CrewBattle.isDeclined(it) }
            .mapNotNull { CrewBattle.opponentOf(it, crewCode) }
            .distinct()

        if (codes.isEmpty()) return emptyMap()

        return coroutineScope {
            codes.map { code -> async { repository.loadOpponentProgress(code) } }
                .awaitAll()
                .associateBy { it.crewCode }
        }
    }

    private fun showChallenges(snapshot: CrewSnapshot) {
        container.removeAllViews()

        if (snapshot.challenges.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.no_challenges_yet)
                textSize = 16f
                setPadding(0, 100, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@CrewChallengesActivity, R.color.text_secondary))
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        val accent = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))

        // Offene Battles stehen im eigenen Bildschirm. Hier gehoert hin,
        // was laeuft: die Ziele der eigenen Crew und angenommene Battles.
        for (challenge in snapshot.challenges.filter {
            !it.isBattle || CrewBattle.isRunning(it)
        }) {
            val view = inflater.inflate(R.layout.item_challenge_entry, container, false)
            val type = ChallengeType.fromStored(challenge.type)
            // Ein Battle, den die Gegenseite noch nicht angenommen hat,
            // laeuft nicht - und zeigt deshalb auch nichts. Vorher stand dort
            // der volle Stand der eigenen Crew, und der Battle sah aus, als
            // sei er in vollem Gange, bevor der andere ueberhaupt gefragt
            // worden war.
            val running = !challenge.isBattle || CrewBattle.isRunning(challenge)
            val contributions =
                if (running) ChallengeManager.progressByMember(challenge, snapshot)
                else emptyList()
            val total = contributions.sumOf { it.second }

            view.findViewById<TextView>(R.id.tvChallengeTitle).setText(type.labelRes)
            view.findViewById<TextView>(R.id.tvChallengeProgress).text =
                getString(type.progressRes, total, challenge.goal)

            // "Geschafft" gilt nur, wenn ueberhaupt gezaehlt wird. Ein Battle,
            // der noch nicht angenommen oder abgelehnt wurde, laeuft nicht -
            // dann meldete die Karte einen Erfolg, den es nicht gibt, bloss
            // weil die eigene Crew das Ziel ohnehin schon ueberschritten hat.
            val counts = running

            showDeadline(view, challenge, done = counts && total >= challenge.goal)
            showBattle(view, challenge, total, type)

            // Loeschen darf nur, wem die Zeile gehoert. Die herausgeforderte
            // Crew wuerde sonst den Battle der anderen aus der Welt schaffen;
            // ihr Weg heraus ist das Ablehnen.
            val delete = view.findViewById<ImageButton>(R.id.btnDeleteChallenge)
            if (CrewBattle.wasChallenged(challenge, crewCode)) {
                delete.visibility = View.GONE
            } else {
                delete.visibility = View.VISIBLE
                delete.setOnClickListener { deleteChallenge(challenge.id) }
            }

            showContributions(
                view.findViewById(R.id.llContributionsContainer),
                inflater,
                contributions,
                type,
                challenge.reward
            )

            val progressBar = view.findViewById<LinearProgressIndicator>(R.id.pbChallenge)
            progressBar.max = challenge.goal.coerceAtLeast(1)
            progressBar.setProgressCompat(total.coerceAtMost(progressBar.max), true)

            if (counts && total >= challenge.goal) {
                view.findViewById<TextView>(R.id.tvChallengeStatus).visibility = View.VISIBLE
                view.findViewById<MaterialCardView>(R.id.cvChallengeRoot).strokeColor = accent.defaultColor
                // LinearProgressIndicator faerbt sich ueber setIndicatorColor,
                // nicht ueber progressTintList wie die alte ProgressBar.
                progressBar.setIndicatorColor(accent.defaultColor)
            }

            container.addView(view)
        }
    }

    /**
     * Alles, was einen Crew-Battle von einer gewoehnlichen Challenge
     * unterscheidet: die Marke oben, der zweite Balken, der Stand und - bei
     * der herausgeforderten Crew - die Antwort.
     *
     * Solange nicht angenommen wurde, steht kein Balken der Gegenseite da.
     * Dort ist noch nichts zu vergleichen, und ein Balken bei null saehe aus
     * wie eine Crew, die nichts tut, statt wie eine, die noch nicht zugesagt
     * hat.
     */
    private fun showBattle(view: View, challenge: Challenge, myTotal: Int, type: ChallengeType) {
        val label = view.findViewById<TextView>(R.id.tvBattleLabel)
        val opponentBox = view.findViewById<View>(R.id.llBattleOpponent)
        val standing = view.findViewById<TextView>(R.id.tvBattleStanding)
        val answer = view.findViewById<View>(R.id.llBattleAnswer)

        if (!challenge.isBattle) {
            label.visibility = View.GONE
            opponentBox.visibility = View.GONE
            standing.visibility = View.GONE
            answer.visibility = View.GONE
            return
        }

        val opponentCode = CrewBattle.opponentOf(challenge, crewCode)
        val opponent = opponentCode?.let { opponents[it] }
        // Der Name kann fehlen, wenn die Crew inzwischen geloescht wurde. Dann
        // steht der Code da - besser als eine leere Zeile.
        val opponentName = opponent?.name ?: opponentCode.orEmpty()

        label.visibility = View.VISIBLE
        label.text =
            if (opponentName.isEmpty()) getString(R.string.battle_label_unknown)
            else getString(R.string.battle_label, opponentName)

        // Im Battle bekommt auch der eigene Balken eine Beschriftung - sonst
        // waere nicht zu sehen, welcher der beiden der eigene ist.
        view.findViewById<TextView>(R.id.tvChallengeProgress).text = getString(
            R.string.battle_side,
            getString(R.string.battle_us),
            getString(type.progressRes, myTotal, challenge.goal)
        )

        standing.visibility = View.VISIBLE
        answer.visibility = View.GONE
        opponentBox.visibility = View.GONE

        when {
            CrewBattle.isDeclined(challenge) -> {
                standing.setText(R.string.battle_declined)
                standing.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }

            CrewBattle.isPending(challenge) -> {
                val challenged = CrewBattle.wasChallenged(challenge, crewCode)
                standing.text =
                    if (challenged) getString(R.string.battle_pending_received, opponentName)
                    else getString(R.string.battle_pending_sent, opponentName)
                standing.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

                if (challenged) showAnswerButtons(answer, challenge)
            }

            else -> showRunningBattle(view, challenge, myTotal, type, opponent, opponentName)
        }
    }

    /** Der laufende Battle: der Balken der Gegenseite und wer vorn liegt. */
    private fun showRunningBattle(
        view: View,
        challenge: Challenge,
        myTotal: Int,
        type: ChallengeType,
        opponent: OpponentProgress?,
        opponentName: String
    ) {
        val standing = view.findViewById<TextView>(R.id.tvBattleStanding)

        if (opponent == null) {
            // Der Stand der anderen Crew liess sich nicht laden. Lieber nichts
            // zeigen als eine Null, die wie ein Vorsprung aussaehe.
            standing.visibility = View.GONE
            return
        }

        val theirs = ChallengeManager.progressOfOpponent(challenge, opponent)

        view.findViewById<View>(R.id.llBattleOpponent).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.tvBattleOpponentProgress).text = getString(
            R.string.battle_side,
            opponentName,
            getString(type.progressRes, theirs, challenge.goal)
        )

        val bar = view.findViewById<LinearProgressIndicator>(R.id.pbBattleOpponent)
        bar.max = challenge.goal.coerceAtLeast(1)
        bar.setProgressCompat(theirs.coerceAtMost(bar.max), true)

        val (textRes, colorRes) = when (CrewBattle.standingOf(myTotal, theirs, challenge.goal)) {
            CrewBattle.Standing.WON -> R.string.battle_won to R.color.accent
            CrewBattle.Standing.LOST -> R.string.battle_lost to R.color.error
            CrewBattle.Standing.LEADING -> R.string.battle_leading to R.color.accent
            CrewBattle.Standing.BEHIND -> R.string.battle_behind to R.color.error
            CrewBattle.Standing.TIED -> R.string.battle_tied to R.color.text_secondary
        }
        standing.visibility = View.VISIBLE
        standing.setText(textRes)
        standing.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showAnswerButtons(answer: View, challenge: Challenge) {
        answer.visibility = View.VISIBLE
        answer.findViewById<MaterialButton>(R.id.btnBattleAccept).setOnClickListener {
            answerBattle(challenge, CrewBattle.STATUS_ACCEPTED)
        }
        answer.findViewById<MaterialButton>(R.id.btnBattleDecline).setOnClickListener {
            answerBattle(challenge, CrewBattle.STATUS_DECLINED)
        }
    }

    /**
     * Annehmen oder ablehnen.
     *
     * Abgelehnt wird nicht vermerkt, sondern geloescht. Vorher blieb die Zeile
     * mit dem Vermerk stehen - und zwar bei **beiden** Crews, denn es ist
     * dieselbe Zeile. Die herausfordernde Seite behielt damit auf Dauer einen
     * Battle in der Liste, an dem nichts mehr passieren konnte, und wegraeumen
     * konnte sie ihn auch nicht. Weg ist hier ehrlicher als ein Vermerk, den
     * niemand mehr braucht.
     */
    private fun answerBattle(challenge: Challenge, status: String) {
        lifecycleScope.launch {
            val done =
                if (status == CrewBattle.STATUS_ACCEPTED) {
                    repository.setBattleStatus(challenge.id, status)
                } else {
                    repository.deleteCrewChallenge(challenge.id)
                }

            if (!done) {
                toast(R.string.battle_answer_failed)
                return@launch
            }
            toast(
                if (status == CrewBattle.STATUS_ACCEPTED) R.string.battle_accepted_toast
                else R.string.battle_declined_toast
            )
            load()
        }
    }

    /**
     * Die Zeile mit der Frist.
     *
     * Vier Faelle, und die Reihenfolge ist wichtig: erreicht schlaegt
     * abgelaufen. Wer es rechtzeitig geschafft hat, soll nicht spaeter lesen,
     * die Frist sei verstrichen - die Punkte sind laengst vergeben.
     *
     * Ist die Frist verstrichen, ohne dass das Ziel stand, faerbt sich die
     * Zeile: von da an kann sich nichts mehr aendern, und das sollte man sehen,
     * ohne nachzurechnen.
     */
    private fun showDeadline(view: View, challenge: Challenge, done: Boolean) {
        val label = view.findViewById<TextView>(R.id.tvChallengeDeadline)
        val date = ChallengeDeadline.toDisplay(challenge.deadline)

        if (date.isEmpty()) {
            label.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE

        val over = ChallengeDeadline.isOver(challenge.deadline)
        val daysLeft = ChallengeDeadline.daysLeft(challenge.deadline) ?: 0L

        label.text = when {
            done -> getString(R.string.challenge_deadline_made_it, date)
            over -> getString(R.string.challenge_deadline_over, date)
            daysLeft <= 0L -> getString(R.string.challenge_deadline_today)
            else -> getString(R.string.challenge_deadline_days, date, daysLeft.toInt())
        }
        label.setTextColor(
            ContextCompat.getColor(
                this,
                if (over && !done) R.color.error else R.color.text_secondary
            )
        )
    }

    /**
     * Die Beitraege der Mitglieder, mit dem Anteil am Topf daneben.
     *
     * Gerechnet wird mit denselben Anteilen, die spaeter ausgezahlt werden -
     * die Zahl auf der Karte und die Zahl in der Rangliste kommen aus derselben
     * Funktion und koennen deshalb nicht auseinanderlaufen.
     */
    private fun showContributions(
        container: LinearLayout,
        inflater: LayoutInflater,
        contributions: List<Pair<UserProfile, Int>>,
        type: ChallengeType,
        reward: Int
    ) {
        container.removeAllViews()
        val shares = ChallengeCalculator.shareOut(reward, contributions.map { it.second })

        contributions.forEachIndexed { index, (member, value) ->
            if (value <= 0) return@forEachIndexed
            val row = inflater.inflate(R.layout.item_challenge_contributor_row, container, false)
            row.findViewById<TextView>(R.id.tvContributorName).text =
                DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
            row.findViewById<TextView>(R.id.tvContributorValue).text =
                getString(type.contributionRes, value)

            val share = row.findViewById<TextView>(R.id.tvContributorShare)
            if (shares[index] > 0) {
                share.visibility = View.VISIBLE
                share.text = getString(R.string.contribution_share, shares[index])
            } else {
                share.visibility = View.GONE
            }

            container.addView(row)
        }
    }

    private fun deleteChallenge(challengeId: String) {
        lifecycleScope.launch {
            if (repository.deleteCrewChallenge(challengeId)) {
                toast(R.string.challenge_deleted)
                load()
            } else {
                toast(R.string.challenge_delete_failed)
            }
        }
    }

    /**
     * Die Einrichtung einer Challenge - fuer die eigene Crew oder als Battle.
     *
     * Derselbe Dialog fuer beides: Art, Ziel und Frist werden gleich gewaehlt,
     * und ein zweiter Dialog mit denselben drei Feldern waere nur eine zweite
     * Stelle zum Pflegen. Nur die Ueberschrift sagt, gegen wen es geht.
     *
     * @param opponent die herausgeforderte Crew, oder null fuer eine
     *        gewoehnliche Challenge.
     */
    private fun showAddChallengeDialog(opponent: Crew? = null) {
        ChallengeSetup.show(this, opponent) { type, goal, deadline ->
            addChallenge(type, goal, deadline, opponent)
        }
    }

    private fun askForChallengeType(onChosen: (ChallengeType) -> Unit) {
        val types = ChallengeType.entries
        val choices = types.map { ChoiceAdapter.Entry(getString(it.labelRes), it.iconRes) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.challenge_type_choose)
            .setAdapter(ChoiceAdapter(this, choices)) { _, index -> onChosen(types[index]) }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun addChallenge(
        type: ChallengeType,
        goal: Double,
        deadline: String?,
        opponent: Crew?
    ) {
        lifecycleScope.launch {
            val reward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)

            val added =
                if (opponent == null) {
                    repository.addCrewChallenge(crewCode, type.name, goal.toInt(), reward, deadline)
                } else {
                    repository.addCrewBattle(
                        crewCode,
                        opponent.id,
                        type.name,
                        goal.toInt(),
                        reward,
                        deadline
                    )
                }

            if (!added) {
                toast(if (opponent == null) R.string.challenge_add_failed else R.string.battle_add_failed)
                return@launch
            }

            if (opponent == null) {
                toast(R.string.challenge_added)
            } else {
                Toast.makeText(
                    this@CrewChallengesActivity,
                    getString(R.string.battle_sent, opponent.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            load()
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
