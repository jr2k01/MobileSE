package com.example.mobilese

import android.app.Activity
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Die Suche nach einer Crew, die herausgefordert werden soll.
 *
 * Gesucht wird nach Crews **und** nach Personen: den Namen einer Crew hat man
 * selten parat, den Namen des Kollegen aus dem Studio schon. Ueber die Person
 * fuehrt der Weg zu ihren Crews - man fordert am Ende immer eine Crew heraus,
 * nie eine einzelne Person.
 *
 * Aufgebaut wie [SearchActivity] und mit demselben Bildschirm, aber mit einem
 * anderen Zweck: dort fuehrt ein Treffer zum Profil oder zur Beitrittsanfrage,
 * hier gibt er das Ergebnis an den aufrufenden Bildschirm zurueck.
 */
class BattleOpponentActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var results: LinearLayout
    private lateinit var hint: TextView

    /** Die eigene Crew - gegen sich selbst tritt niemand an. */
    private var ownCrewCode: String = ""

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_search)
        setUpTopBar(R.string.battle_search_title)

        repository = AppRepository.get(this)
        ownCrewCode = intent.getStringExtra(EXTRA_OWN_CREW).orEmpty()

        results = findViewById(R.id.llSearchResults)
        hint = findViewById(R.id.tvSearchHint)

        // Die Beschriftung gehoert an das TextInputLayout, nicht an das
        // Eingabefeld darin. Am Feld gesetzt zeichnet Material beide: seine
        // eigene schwebende Beschriftung und den Hinweis im Feld - Schrift
        // ueber Schrift.
        findViewById<TextInputLayout>(R.id.tilSearch).setHint(R.string.battle_search_hint)

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
            // Dieselbe Tipppause wie in der allgemeinen Suche: ohne sie liefe
            // fuer jeden Buchstaben eine Abfrage, die schon veraltet ist,
            // bevor sie zurueckkommt.
            delay(TYPING_PAUSE_MS)
            show(repository.searchPeople(query), repository.searchCrews(query))
        }
    }

    private fun show(people: List<UserProfile>, crews: List<Crew>) {
        results.removeAllViews()

        if (people.isEmpty() && crews.isEmpty()) {
            hint.setText(R.string.search_no_results)
            hint.visibility = View.VISIBLE
            return
        }
        hint.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        if (crews.isNotEmpty()) {
            addLabel(R.string.search_crews_label)
            crews.forEach { addCrew(inflater, it) }
        }
        if (people.isNotEmpty()) {
            addLabel(R.string.search_people_label)
            people.forEach { addPerson(inflater, it) }
        }
    }

    private fun addLabel(textRes: Int) {
        val label = TextView(this)
        label.setTextAppearance(R.style.TextAppearance_CrewFit_SectionLabel)
        label.setText(textRes)
        label.setPadding(0, resources.getDimensionPixelSize(R.dimen.card_padding), 0, 0)
        results.addView(label)
    }

    /**
     * Eine Crew als Gegner.
     *
     * Die eigene Crew steht mit einem Hinweis da statt gar nicht: sie taucht in
     * der Suche nach dem eigenen Namen zwangslaeufig auf, und ein Eintrag, der
     * einfach fehlt, sieht nach einem Fehler aus.
     */
    private fun addCrew(inflater: LayoutInflater, crew: Crew) {
        val row = inflater.inflate(R.layout.item_member_crew_row, results, false)
        row.findViewById<TextView>(R.id.tvCrewRowName).text = crew.name

        val note = row.findViewById<TextView>(R.id.tvCrewRowMember)
        val action = row.findViewById<MaterialButton>(R.id.btnCrewRowJoin)

        if (crew.id == ownCrewCode) {
            note.setText(R.string.battle_own_crew)
            note.visibility = View.VISIBLE
            action.visibility = View.GONE
        } else {
            note.visibility = View.GONE
            action.visibility = View.VISIBLE
            action.setText(R.string.battle_challenge_btn)
            action.setOnClickListener { pick(crew) }
            row.setOnClickListener { pick(crew) }
        }

        results.addView(row)
    }

    /**
     * Eine Person. Antippen zeigt ihre Crews - eine davon wird herausgefordert.
     */
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
        row.setOnClickListener { showCrewsOf(person) }
        results.addView(row)
    }

    private fun showCrewsOf(person: UserProfile) {
        lifecycleScope.launch {
            val name = DisplayName.of(person).ifEmpty { getString(R.string.unknown_member) }
            // Die eigene Crew fliegt hier heraus statt nur gesperrt zu werden:
            // in einer Liste, die nach dem Antippen einer Person erscheint,
            // waere ein nicht waehlbarer Eintrag nur im Weg.
            val crews = repository.getCrewsOf(person.id).filter { it.id != ownCrewCode }

            if (crews.isEmpty()) {
                MaterialAlertDialogBuilder(this@BattleOpponentActivity)
                    .setTitle(name)
                    .setMessage(R.string.battle_person_no_crew)
                    .setPositiveButton(R.string.ok_btn, null)
                    .show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@BattleOpponentActivity)
                .setTitle(getString(R.string.battle_person_crews, name))
                .setItems(crews.map { it.name }.toTypedArray()) { _, index -> pick(crews[index]) }
                .setNegativeButton(R.string.cancel_btn, null)
                .show()
        }
    }

    private fun pick(crew: Crew) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_CREW_CODE, crew.id)
                .putExtra(EXTRA_CREW_NAME, crew.name)
        )
        finish()
    }

    companion object {
        private const val EXTRA_OWN_CREW = "own_crew"
        const val EXTRA_CREW_CODE = "crew_code"
        const val EXTRA_CREW_NAME = "crew_name"

        /** 300 ms wie in der allgemeinen Suche. */
        private const val TYPING_PAUSE_MS = 300L

        fun intent(context: Context, ownCrewCode: String): Intent =
            Intent(context, BattleOpponentActivity::class.java)
                .putExtra(EXTRA_OWN_CREW, ownCrewCode)

        /** Die gewaehlte Crew aus dem Ergebnis, oder null wenn abgebrochen. */
        fun crewFrom(data: Intent?): Crew? {
            val code = data?.getStringExtra(EXTRA_CREW_CODE) ?: return null
            val name = data.getStringExtra(EXTRA_CREW_NAME).orEmpty()
            return Crew(id = code, name = name, creatorId = "")
        }
    }
}
