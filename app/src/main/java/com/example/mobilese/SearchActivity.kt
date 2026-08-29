package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Suche nach Leuten und Crews.
 *
 * Der Weg nach draussen: alles andere in der App zeigt die eigene Crew. Hier
 * findet man Personen, denen man folgen kann, und Crews, denen man beitreten
 * kann - ohne dass jemand einen Code herumschicken muss.
 *
 * Gesucht wird waehrend des Tippens, aber nicht bei jedem Zeichen: nach der
 * letzten Eingabe wird kurz gewartet. Ohne diese Pause liefe fuer "MobileSE"
 * acht Mal eine Abfrage, von denen sieben schon veraltet sind, bevor sie
 * zurueckkommen.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var results: LinearLayout
    private lateinit var hint: TextView

    /** Laufende Suche, damit sich zwei Eingaben nicht ueberholen. */
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_search)
        // Nur waehrend des Tutorialmodus, und dort einmal: sonst kostet
        // der Aufruf einen Blick in die Einstellungen und tut nichts.
        CoachTour.start(this, Tours.SEARCH)
        setUpTopBar(R.string.search_title)

        repository = AppRepository.get(this)
        results = findViewById(R.id.llSearchResults)
        hint = findViewById(R.id.tvSearchHint)

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = search(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
    }

    private fun search(query: String) {
        searchJob?.cancel()

        if (query.trim().length < AppRepository.SEARCH_MIN_LENGTH) {
            results.removeAllViews()
            hint.setText(R.string.search_empty)
            hint.visibility = View.VISIBLE
            return
        }

        searchJob = lifecycleScope.launch {
            delay(TYPING_PAUSE_MS)

            val people = repository.searchPeople(query)
            val crews = repository.searchCrews(query)
            show(people, crews)
        }
    }

    private suspend fun show(people: List<UserProfile>, crews: List<Crew>) {
        results.removeAllViews()

        if (people.isEmpty() && crews.isEmpty()) {
            hint.setText(R.string.search_no_results)
            hint.visibility = View.VISIBLE
            return
        }
        hint.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        if (people.isNotEmpty()) {
            addLabel(R.string.search_people_label)
            people.forEach { addPerson(inflater, it) }
        }
        if (crews.isNotEmpty()) {
            addLabel(R.string.search_crews_label)
            val mine = repository.getJoinedCrews().map { it.id }.toSet()
            crews.forEach { crew ->
                val requested = crew.id !in mine && repository.hasRequestedToJoin(crew.id)
                addCrew(inflater, crew, crew.id in mine, requested)
            }
        }
    }

    private fun addLabel(textRes: Int) {
        val label = TextView(this)
        label.setTextAppearance(R.style.TextAppearance_CrewFit_SectionLabel)
        label.setText(textRes)
        label.setPadding(0, resources.getDimensionPixelSize(R.dimen.card_padding), 0, 0)
        results.addView(label)
    }

    /** Antippen fuehrt auf das Profil - dort steht der Folgen-Knopf. */
    private fun addPerson(inflater: LayoutInflater, person: UserProfile) {
        val row = inflater.inflate(R.layout.item_crew_member_row, results, false)
        row.findViewById<TextView>(R.id.tvMemberName).text =
            DisplayName.of(person).ifEmpty { getString(R.string.unknown_member) }
        ImageLoader.into(
            row.findViewById<ImageView>(R.id.ivMemberPhoto),
            person.avatarUrl,
            circular = true,
            placeholder = android.R.drawable.ic_menu_gallery
        )
        row.setOnClickListener {
            startActivity(MemberProfileActivity.intent(this, person.id))
        }
        results.addView(row)
    }

    /**
     * Drei Zustaende: schon drin, schon angefragt, oder der Knopf zum Anfragen.
     * Der Hinweistext traegt die ersten beiden - ein ausgegrauter Knopf haette
     * dieselbe Groesse und liesse einen erst hinsehen, warum er nicht geht.
     */
    private fun addCrew(
        inflater: LayoutInflater,
        crew: Crew,
        alreadyIn: Boolean,
        requested: Boolean
    ) {
        val row = inflater.inflate(R.layout.item_member_crew_row, results, false)
        row.findViewById<TextView>(R.id.tvCrewRowName).text = crew.name

        val note = row.findViewById<TextView>(R.id.tvCrewRowMember)
        val join = row.findViewById<MaterialButton>(R.id.btnCrewRowJoin)

        when {
            alreadyIn -> {
                note.setText(R.string.member_crew_joined)
                note.visibility = View.VISIBLE
                join.visibility = View.GONE
            }
            requested -> {
                note.setText(R.string.member_crew_requested)
                note.visibility = View.VISIBLE
                join.visibility = View.GONE
            }
            else -> {
                note.visibility = View.GONE
                join.visibility = View.VISIBLE
                join.setText(R.string.member_crew_request)
                join.setOnClickListener { askToJoin(crew, note, join) }
            }
        }

        results.addView(row)
    }

    /**
     * Um Aufnahme bitten.
     *
     * Anders als frueher wird hier nicht mehr beigetreten und auch nicht auf
     * den Startbildschirm gesprungen: die Crew gehoert einem ja noch nicht.
     * Die Zeile wechselt nur in den angefragten Zustand, damit sichtbar ist,
     * dass die Bitte draussen ist.
     */
    private fun askToJoin(crew: Crew, note: TextView, join: MaterialButton) {
        lifecycleScope.launch {
            join.isEnabled = false
            if (!repository.requestToJoinCrew(crew.id)) {
                join.isEnabled = true
                Toast.makeText(this@SearchActivity, R.string.crew_request_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            note.setText(R.string.member_crew_requested)
            note.visibility = View.VISIBLE
            join.visibility = View.GONE

            Toast.makeText(
                this@SearchActivity,
                getString(R.string.crew_request_sent, crew.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private companion object {
        /** Wartezeit nach dem letzten Zeichen, bevor gesucht wird. */
        const val TYPING_PAUSE_MS = 300L
    }
}
