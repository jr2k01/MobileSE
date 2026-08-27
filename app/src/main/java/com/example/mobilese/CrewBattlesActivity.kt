package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Crew-Battles: eine andere Crew herausfordern und offene Einladungen
 * beantworten.
 *
 * Eigener Bildschirm und nicht mehr ein Knopf zwischen den Challenges. Ein
 * Battle ist etwas anderes als ein Ziel der eigenen Crew: er richtet sich nach
 * draussen und laeuft erst, wenn die Gegenseite zustimmt. Zwischen laufenden
 * Challenges stand er da wie eines von ihnen - mit Fortschrittsbalken, obwohl
 * noch niemand gefragt worden war.
 *
 * Hier stehen deshalb nur die **offenen** Einladungen, getrennt nach Richtung:
 * was man selbst verschickt hat, und was hereinkam. Sobald ein Battle
 * angenommen ist, verschwindet er von hier und taucht bei den Challenges auf -
 * dort gehoert er dann hin, denn ab da ist er ein laufendes Ziel.
 */
class CrewBattlesActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var crewCode: String

    /** Die Namen der beteiligten Crews, damit nicht nur Codes dastehen. */
    private var names: Map<String, String> = emptyMap()

    private val pickOpponent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val crew = BattleOpponentActivity.crewFrom(result.data) ?: return@registerForActivityResult
            ChallengeSetup.show(this, crew) { type, goal, deadline ->
                createBattle(crew, type, goal, deadline)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_crew_battles)

        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        setUpTopBar(R.string.crew_battles_title)
        findViewById<MaterialButton>(R.id.btnStartBattle).setOnClickListener {
            pickOpponent.launch(BattleOpponentActivity.intent(this, crewCode))
        }
    }

    /**
     * Beim Zurueckkommen neu laden: wer gerade einen Battle gestellt hat, soll
     * ihn hier sofort unter "You challenged" finden.
     */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val snapshot = repository.loadCrewSnapshot(crewCode)
            val open = snapshot.challenges.filter { it.isBattle && CrewBattle.isPending(it) }

            // Die Namen der Gegner, damit nicht nur Codes dastehen. Eine
            // Abfrage je Crew, aber es sind hoechstens eine Handvoll offene
            // Einladungen.
            names = open
                .mapNotNull { CrewBattle.opponentOf(it, crewCode) }
                .distinct()
                .associateWith { repository.getCrewName(it) }

            show(
                container = findViewById(R.id.llBattleReceived),
                empty = R.string.battle_none_received,
                invites = open.filter { CrewBattle.wasChallenged(it, crewCode) },
                answerable = true
            )
            show(
                container = findViewById(R.id.llBattleSent),
                empty = R.string.battle_none_sent,
                invites = open.filterNot { CrewBattle.wasChallenged(it, crewCode) },
                answerable = false
            )
        }
    }

    /**
     * Eine der beiden Listen.
     *
     * @param answerable Empfangene Einladungen bekommen Annehmen und Ablehnen,
     *        selbst verschickte nur das Zuruecknehmen. Wer eingeladen hat, darf
     *        seine Einladung nicht auch noch selbst annehmen.
     */
    private fun show(
        container: LinearLayout,
        empty: Int,
        invites: List<Challenge>,
        answerable: Boolean
    ) {
        container.removeAllViews()

        if (invites.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(empty)
                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        for (invite in invites) {
            val view = inflater.inflate(R.layout.item_battle_invite, container, false)
            val type = ChallengeType.fromStored(invite.type)
            val opponent = CrewBattle.opponentOf(invite, crewCode)
            val name = names[opponent] ?: opponent ?: getString(R.string.battle_label_unknown)

            view.findViewById<TextView>(R.id.tvInviteOpponent).text =
                getString(R.string.battle_label, name)
            view.findViewById<TextView>(R.id.tvInviteDetail).text = getString(
                R.string.battle_invite_line,
                getString(type.labelRes),
                // Nur das Ziel, nicht "0 / 30": vor der Annahme gibt es
                // keinen Stand, und eine Null davor sieht aus wie einer.
                getString(type.contributionRes, invite.goal)
            )

            val deadline = view.findViewById<TextView>(R.id.tvInviteDeadline)
            val days = ChallengeDeadline.daysLeft(invite.deadline)
            if (days != null) {
                deadline.visibility = View.VISIBLE
                deadline.text = getString(
                    R.string.challenge_deadline_days,
                    ChallengeDeadline.toDisplay(invite.deadline),
                    days.toInt()
                )
            }

            if (answerable) {
                view.findViewById<View>(R.id.llInviteAnswer).visibility = View.VISIBLE
                view.findViewById<MaterialButton>(R.id.btnInviteAccept)
                    .setOnClickListener { accept(invite) }
                view.findViewById<MaterialButton>(R.id.btnInviteDecline)
                    .setOnClickListener { drop(invite, R.string.battle_declined_toast) }
            } else {
                view.findViewById<MaterialButton>(R.id.btnInviteWithdraw).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { drop(invite, R.string.battle_declined_toast) }
                }
            }

            container.addView(view)
        }
    }

    /**
     * Legt den Battle an. Er steht danach hier unter "You challenged" - bei
     * den Challenges taucht er erst auf, wenn die Gegenseite zustimmt.
     */
    private fun createBattle(
        opponent: Crew,
        type: ChallengeType,
        goal: Double,
        deadline: String?
    ) {
        lifecycleScope.launch {
            val reward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)
            val added = repository.addCrewBattle(
                crewCode,
                opponent.id,
                type.name,
                goal.toInt(),
                reward,
                deadline
            )

            if (!added) {
                toast(R.string.battle_add_failed)
                return@launch
            }
            toast(R.string.battle_sent_ok)
            load()
        }
    }

    private fun accept(invite: Challenge) {
        lifecycleScope.launch {
            if (!repository.setBattleStatus(invite.id, CrewBattle.STATUS_ACCEPTED)) {
                toast(R.string.battle_answer_failed)
                return@launch
            }
            toast(R.string.battle_accepted_toast)
            load()
        }
    }

    /**
     * Ablehnen und Zuruecknehmen sind dasselbe: die Zeile verschwindet.
     *
     * Ein Vermerk "abgelehnt" blieb sonst bei **beiden** Crews stehen - es ist
     * dieselbe Zeile -, und wegraeumen konnte ihn niemand.
     */
    private fun drop(invite: Challenge, message: Int) {
        lifecycleScope.launch {
            if (!repository.deleteCrewChallenge(invite.id)) {
                toast(R.string.battle_answer_failed)
                return@launch
            }
            toast(message)
            load()
        }
    }

    private fun toast(res: Int) =
        android.widget.Toast.makeText(this, res, android.widget.Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: Context): Intent = Intent(context, CrewBattlesActivity::class.java)
    }
}
