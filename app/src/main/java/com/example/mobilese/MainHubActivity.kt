package com.example.mobilese

import android.content.Intent
import android.media.MediaPlayer
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    private lateinit var llRanking: LinearLayout
    private lateinit var llLatestActivities: LinearLayout

    private var mediaPlayer: MediaPlayer? = null

    /** Laufender Ladevorgang, damit sich zwei Aufrufe nicht ueberholen. */
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main_hub)

        repository = AppRepository.get(this)

        tvCrewName = findViewById(R.id.tvHomeCrewName)
        llMembers = findViewById(R.id.llMembersContainer)
        llRanking = findViewById(R.id.llRankingContainer)
        llLatestActivities = findViewById(R.id.llLatestActivitiesContainer)

        // Steht unter der Liste der letzten Aktivitaeten statt in der
        // Navigationsleiste.
        findViewById<Button>(R.id.btnAllActivities).setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

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
                R.id.navChallenges -> CrewChallengesActivity::class.java
                R.id.navProfile -> ProfileActivity::class.java
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
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // --- Daten ---

    private fun refresh() {
        val crewCode = repository.getJoinedCrewCode()
        if (crewCode == null) {
            tvCrewName.text = getString(R.string.no_crew_joined)
            llMembers.removeAllViews()
            llRanking.removeAllViews()
            llLatestActivities.removeAllViews()
            return
        }

        // Bis der Name geladen ist, bleibt das Feld leer statt eine
        // Zwischenaussage zu treffen. Vorher stand hier der Vorgabetext aus
        // dem Layout - "Not joined any crew" - obwohl eine Crew vorhanden war
        // und nur noch nicht geladen.
        tvCrewName.text = ""

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val (crewName, snapshot) = coroutineScope {
                val nameAsync = async { repository.getCrewName(crewCode) }
                val snapshotAsync = async { repository.loadCrewSnapshot(crewCode) }
                nameAsync.await() to snapshotAsync.await()
            }

            tvCrewName.text = getString(R.string.your_crew_prefix, crewName)
            showMembers(snapshot)
            showTopThree(snapshot)
            showLatestActivities(snapshot)
        }
    }

    private fun showMembers(snapshot: CrewSnapshot) {
        llMembers.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (member in snapshot.members) {
            val view = inflater.inflate(R.layout.item_member_profile_mini, llMembers, false)
            view.findViewById<TextView>(R.id.tvMemberName).text =
                DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
            ImageLoader.into(
                view.findViewById(R.id.ivMemberPhoto),
                member.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
            llMembers.addView(view)
        }
    }

    private fun showTopThree(snapshot: CrewSnapshot) {
        llRanking.removeAllViews()
        val inflater = LayoutInflater.from(this)

        Scoreboard.build(snapshot).take(3).forEachIndexed { index, entry ->
            val view = inflater.inflate(R.layout.item_leaderboard_entry, llRanking, false)
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
            llRanking.addView(view)
        }
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
            view.findViewById<TextView>(R.id.tvLatestActivityUser).text =
                nameById[activity.userId]?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.unknown_member)
            view.findViewById<TextView>(R.id.tvLatestActivityInfo).text = activity.sport
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
                btnPlay.setOnClickListener { playVoiceNote(voiceUrl) }
            }

            llLatestActivities.addView(view)
        }
    }

    /**
     * Sprachnotizen liegen als URL vor. prepare() wuerde dafuer synchron im
     * Main-Thread auf das Netz warten und einen ANR riskieren, deshalb
     * prepareAsync() mit Callback.
     */
    private fun playVoiceNote(url: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, what, extra ->
                android.util.Log.e("Audio", "Playback error $what/$extra")
                toast(R.string.playback_failed)
                true
            }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (e: Exception) {
                android.util.Log.e("Audio", "Could not set data source: ${e.message}")
                toast(R.string.playback_failed)
            }
        }
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
