package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

    private fun load(userId: String) {
        val crewCode = repository.getJoinedCrewCode() ?: run {
            finish()
            return
        }

        lifecycleScope.launch {
            val snapshot = repository.loadCrewSnapshot(crewCode)
            val entry = Scoreboard.build(snapshot).firstOrNull { it.userId == userId }
            if (entry == null) {
                // Das Mitglied hat die Crew verlassen, waehrend der Bildschirm
                // geoeffnet wurde - dann gibt es hier nichts mehr zu zeigen.
                Toast.makeText(
                    this@MemberProfileActivity,
                    R.string.member_not_found,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            showMember(entry, snapshot)
        }
    }

    private fun showMember(entry: Scoreboard.Entry, snapshot: CrewSnapshot) {
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
