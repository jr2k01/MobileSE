package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Kurzer Ueberblick ueber ein Crewmitglied.
 *
 * Erreichbar ueber das Bild eines Mitglieds auf dem Startbildschirm und ueber
 * die Mitgliederliste der Crew. Gezeigt wird, was die Crew angeht: Kuerzel,
 * Punkte, Anzahl Workouts, die heutigen Schritte und die Medaillen. Persoenliche
 * Angaben wie Geburtsdatum, Groesse und Gewicht bleiben im eigenen Profil.
 *
 * Alle Zahlen stammen aus demselben [CrewSnapshot], den auch Startbildschirm und
 * Rangliste benutzen - so kann hier keine andere Punktzahl stehen als dort.
 */
class MemberProfileActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_member_profile)
        setUpTopBar(R.string.member_profile_title)

        repository = AppRepository.get(this)

        val userId = intent.getStringExtra(EXTRA_USER_ID)
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, R.string.member_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        labelStats()
        load(userId)
        showFollowAndCrews(userId)
    }

    /** Die Beschriftungen stehen fest, nur die Zahlen kommen spaeter. */
    private fun labelStats() {
        findViewById<View>(R.id.statPoints)
            .findViewById<TextView>(R.id.tvStatLabel).setText(R.string.member_stat_points)
        findViewById<View>(R.id.statWorkouts)
            .findViewById<TextView>(R.id.tvStatLabel).setText(R.string.member_stat_workouts)
        findViewById<View>(R.id.statMedals)
            .findViewById<TextView>(R.id.tvStatLabel).setText(R.string.member_stat_medals)
    }

    /**
     * Zahlen und Medaillen stammen aus der Crew, die gerade angezeigt wird.
     *
     * Wer nicht darin ist - jemand aus der Suche oder aus der Folgen-Liste -
     * hat hier keine Zahlen. Dann bleiben Name und Bild stehen, und die
     * crewbezogenen Bereiche verschwinden. Vorher schloss sich der Bildschirm
     * in dem Fall sofort wieder, was aus der Suche heraus wie ein Absturz
     * aussah.
     */
    private fun load(userId: String) {
        lifecycleScope.launch {
            val crewCode = repository.getJoinedCrewCode()
            val snapshot = crewCode?.let { repository.loadCrewSnapshot(it) }
            val entry = snapshot?.let { snap ->
                Scoreboard.build(snap).firstOrNull { it.userId == userId }
            }

            if (entry != null && snapshot != null) {
                showCrewMember(entry, snapshot)
                return@launch
            }

            // Nur das Profil selbst - die Person ist nicht in dieser Crew.
            val profile = repository.getProfileById(userId)
            if (profile == null) {
                Toast.makeText(
                    this@MemberProfileActivity,
                    R.string.member_not_found,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            showStranger(profile)
        }
    }

    /** Blendet aus, was ohne gemeinsame Crew keinen Wert haette. */
    private fun showStranger(profile: UserProfile) {
        findViewById<TextView>(R.id.tvMemberName).text =
            DisplayName.of(profile).ifEmpty { getString(R.string.unknown_member) }
        ImageLoader.into(
            findViewById(R.id.ivMemberPhoto),
            profile.avatarUrl,
            circular = true,
            placeholder = android.R.drawable.ic_menu_gallery
        )

        findViewById<View>(R.id.llMemberSteps).visibility = View.GONE
        findViewById<View>(R.id.llMemberStats).visibility = View.GONE
        findViewById<View>(R.id.tvMemberMedalsLabel).visibility = View.GONE
        findViewById<View>(R.id.glMedals).visibility = View.GONE
    }

    private fun showCrewMember(entry: Scoreboard.Entry, snapshot: CrewSnapshot) {
        findViewById<TextView>(R.id.tvMemberName).text = entry.name
        ImageLoader.into(
            findViewById(R.id.ivMemberPhoto),
            entry.avatarUrl,
            circular = true,
            placeholder = android.R.drawable.ic_menu_gallery
        )

        val medals = Medals.statusFor(entry.userId, snapshot)
        val workouts = snapshot.activities.count { it.userId == entry.userId }

        setStat(R.id.statPoints, entry.points)
        setStat(R.id.statWorkouts, workouts)
        setStat(R.id.statMedals, medals.count { it.value })

        showSteps(entry.todaySteps)
        MedalGrid.fill(findViewById(R.id.glMedals), medals)
    }

    /**
     * Folgen-Knopf und die Crews dieser Person.
     *
     * Beides haengt nicht an der Crew, ueber die man hierher gekommen ist -
     * deshalb wird es getrennt vom Snapshot geladen und steht auch dann da,
     * wenn die Person die Crew inzwischen verlassen hat.
     */
    private fun showFollowAndCrews(userId: String) {
        lifecycleScope.launch {
            val me = repository.currentUserId()
            val button = findViewById<MaterialButton>(R.id.btnFollow)

            // Sich selbst zu folgen ergibt keinen Sinn; dann bleibt der Knopf weg.
            if (me != null && me != userId) {
                button.visibility = View.VISIBLE
                showFollowState(button, repository.isFollowing(userId), userId)
            }

            showCrews(userId)
        }
    }

    private fun showFollowState(button: MaterialButton, following: Boolean, userId: String) {
        button.setText(if (following) R.string.unfollow_btn else R.string.follow_btn)
        button.setOnClickListener {
            lifecycleScope.launch {
                if (repository.setFollowing(userId, !following)) {
                    showFollowState(button, !following, userId)
                } else {
                    Toast.makeText(
                        this@MemberProfileActivity,
                        R.string.follow_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun showCrews(userId: String) {
        val crews = repository.getCrewsOf(userId)
        val container = findViewById<LinearLayout>(R.id.llMemberCrews)
        container.removeAllViews()

        findViewById<View>(R.id.tvMemberCrewsLabel).visibility =
            if (crews.isEmpty()) View.GONE else View.VISIBLE

        val mine = repository.getJoinedCrews().map { it.id }.toSet()
        val inflater = LayoutInflater.from(this)

        for (crew in crews) {
            val row = inflater.inflate(R.layout.item_member_crew_row, container, false)
            row.findViewById<TextView>(R.id.tvCrewRowName).text = crew.name

            val alreadyIn = crew.id in mine
            row.findViewById<View>(R.id.tvCrewRowMember).visibility =
                if (alreadyIn) View.VISIBLE else View.GONE

            val join = row.findViewById<MaterialButton>(R.id.btnCrewRowJoin)
            join.visibility = if (alreadyIn) View.GONE else View.VISIBLE
            join.setOnClickListener { joinCrew(crew) }

            container.addView(row)
        }
    }

    /**
     * Der Crew beitreten, in der die Person ist.
     *
     * Danach wird sie zur angezeigten Crew - wer beitritt, will sie sehen.
     * Zurueck auf den Startbildschirm statt hier zu bleiben, weil sich mit der
     * Crew alles andere auch aendert.
     */
    private fun joinCrew(crew: Crew) {
        lifecycleScope.launch {
            if (!repository.joinCrew(crew.id)) {
                Toast.makeText(this@MemberProfileActivity, R.string.invalid_crew_code, Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(
                this@MemberProfileActivity,
                getString(R.string.joined_crew, crew.name),
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(this@MemberProfileActivity, MainHubActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setStat(containerId: Int, value: Int) {
        findViewById<View>(containerId)
            .findViewById<TextView>(R.id.tvStatValue).text = format(value)
    }

    private fun showSteps(steps: Int) {
        findViewById<CircularProgressIndicator>(R.id.piStepsGoal)
            .setProgressCompat(StepGoal.progressPercent(steps.toLong()), false)

        findViewById<ImageView>(R.id.ivStepsGoalReached).visibility =
            if (StepGoal.isReached(steps.toLong())) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.tvMemberSteps).text =
            getString(R.string.member_steps_today, format(steps))
    }

    private fun format(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    companion object {
        private const val EXTRA_USER_ID = "user_id"

        /** Einziger Weg hierher, damit der Schluessel des Extras nur hier steht. */
        fun intent(context: Context, userId: String): Intent =
            Intent(context, MemberProfileActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
    }
}
