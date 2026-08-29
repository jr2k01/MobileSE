package com.example.mobilese

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Sucht das Crew-Mitglied, mit dem gerade zusammen trainiert wird.
 *
 * Beide Telefone stehen in diesem Bildschirm, senden ihre Kennung ueber
 * Bluetooth Low Energy aus und hoeren zugleich auf die der anderen - siehe
 * [PartnerBeacon]. Wer gefunden wird, erscheint in der Liste; ein Antippen
 * waehlt ihn und schliesst den Bildschirm.
 *
 * Eigener Bildschirm und kein Dialog im Workout-Formular: die Suche laeuft
 * ueber Sekunden, braucht Berechtigungen und kann auf mehrerlei Art
 * schiefgehen. In einem Dialog waere fuer die Erklaerung dazu kein Platz.
 *
 * Das Ergebnis geht als Kennung zurueck; das Formular holt sich den Namen
 * selbst aus der Crew.
 */
class TrainingPartnerActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private var beacon: PartnerBeacon? = null
    private var link: PartnerLink? = null

    /** Die Geraete hinter den gefundenen Namen - ohne sie keine Verbindung. */
    private val devices = mutableMapOf<String, BluetoothDevice>()

    /**
     * Wen man angetippt hat.
     *
     * Frueher tippte nur einer, und wer zuerst kam, fuehrte. Das war ein
     * Rennen: der eigene GATT-Server meldet dieselbe Strecke, die man selbst
     * aufbaut, und je nachdem, welcher Rueckruf zuerst kam, wuergte sich das
     * Geraet den eigenen Aufbau ab. Jetzt tippen **beide**, und wer anruft,
     * entscheidet ein Vergleich der Kennungen - dasselbe Ergebnis auf beiden
     * Geraeten, ohne Absprache und ohne Rennen.
     */
    private var chosen: UserProfile? = null

    /**
     * Ruft immer wieder an, bis es steht.
     *
     * Ein einzelner Versuch reicht nicht: die Gegenseite tippt vielleicht erst
     * zwei Sekunden spaeter, und vorher gibt es dort nichts anzurufen.
     */
    /** Wie oft schon vergeblich angerufen wurde. */
    private var attempts = 0

    private val retry = object : Runnable {
        override fun run() {
            val target = chosen ?: return
            if (JointSession.hasPartners()) return

            // Irgendwann ist gut. Ohne Obergrenze lief der Kreis endlos, und
            // auf dem Bildschirm stand minutenlang dasselbe.
            if (attempts >= MAX_ATTEMPTS) {
                showStep(R.string.linking_step_failed)
                // Aufgegeben - dann darf wieder gesucht werden, sonst findet
                // ein neuer Anlauf niemanden mehr.
                beacon?.resumeScanning()
                return
            }
            attempts++
            val link = link
            // Nicht dazwischenfunken: laeuft schon ein eigener Aufbau, oder
            // haengt die Gegenseite bereits an unserem Server, waere ein
            // Anruf jetzt genau das Kreuzen, das die Strecke zerreisst.
            if (link != null && !link.isBusy() && !link.hasPeers()) {
                devices[target.id]?.let { link.connectTo(it, target) }
            }
            ticker.postDelayed(this, RETRY_MILLIS)
        }
    }

    private val watch = Stopwatch()

    /** Die eigene Kennung - fuer die Runde, die herumgeschickt wird. */
    private var ownUserId: String? = null
    private val ticker = Handler(Looper.getMainLooper())
    private var members: List<UserProfile> = emptyList()

    /** Laeuft im Sekundentakt, solange die Uhr laeuft. */
    private val tick = object : Runnable {
        override fun run() {
            showTimer()
            ticker.postDelayed(this, TICK_MS)
        }
    }

    private val requestNearby =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AppPermission.NEARBY.isGranted(this)) {
                startSearching()
            } else {
                showProblem(R.string.partner_permission_missing)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_training_partner)
        // Nur waehrend des Tutorialmodus, und dort einmal: sonst kostet
        // der Aufruf einen Blick in die Einstellungen und tut nichts.
        CoachTour.start(this, Tours.PARTNER)
        setUpTopBar(R.string.partner_title)

        repository = AppRepository.get(this)

        findViewById<View>(R.id.btnPartnerSkip).setOnClickListener {
            // Allein weiter: kein Ergebnis, das Formular bleibt beim
            // einfachen Punktestand.
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (AppPermission.NEARBY.isGranted(this)) {
            startSearching()
        } else {
            requestNearby.launch(AppPermission.NEARBY.manifestNames)
        }
    }

    /**
     * Die Crew wird zuerst geholt: gefunden werden soll nur, wer auch dazu
     * gehoert. Ein fremdes CrewFit in Reichweite - im Fitnessstudio durchaus
     * denkbar - darf keine doppelten Punkte verschaffen.
     */
    private fun startSearching() {
        lifecycleScope.launch {
            val crewCode = repository.getJoinedCrewCode()
            if (crewCode == null) {
                showProblem(R.string.partner_no_crew)
                return@launch
            }

            members = repository.getCrewMembers(crewCode)
            val me = repository.currentUserId()
            ownUserId = me
            if (me == null) {
                showProblem(R.string.partner_permission_missing)
                return@launch
            }

            // Der Server laeuft von Anfang an: wer angetippt wird, muss die
            // Verbindung annehmen koennen, ohne selbst etwas getan zu haben.
            link = PartnerLink(
                context = this@TrainingPartnerActivity,
                ownUserId = me,
                members = members
            ).also {
                it.onPartner = { member -> runOnUiThread { paired(member) } }
                it.onLost = { runOnUiThread { linkLost() } }
                it.onProblem = { res -> runOnUiThread { showProblem(res) } }
                it.onMessage = { message -> runOnUiThread { received(message) } }
                it.onIncoming = { runOnUiThread { incoming() } }
                it.onNeedsFreshAddress = {
                    runOnUiThread {
                        // Nach dem Entkoppeln taugt die gemerkte Adresse
                        // nichts mehr. Also wieder suchen, bis eine neue
                        // hereinkommt; der naechste Anlauf nimmt sie dann.
                        showStep(R.string.linking_step_pairing)
                        beacon?.resumeScanning()
                    }
                }
                it.startServer()
            }

            beacon = PartnerBeacon(
                context = this@TrainingPartnerActivity,
                ownUserId = me,
                members = members,
                // Beide Rueckrufe kommen vom Bluetooth-System, nicht vom
                // Bildschirm-Thread. runOnUiThread ist deshalb Pflicht, nicht
                // Vorsicht: Views von dort anzufassen stuerzt ab.
                onFound = { member, device -> runOnUiThread { addFound(member, device) } },
                onProblem = { res -> runOnUiThread { showProblem(res) } }
            ).also { it.start() }
        }
    }

/**
     * Die Gegenseite ruft an.
     *
     * Wer noch nicht angetippt hat, sieht das hier. Wer schon gewaehlt hat,
     * wartet ohnehin - dann steht der Text bereits richtig da und wird nicht
     * ueberschrieben.
     */
    private fun incoming() {
        // Jemand klopft an - und ab hier muss das Funkmodul frei sein.
        //
        // Ein Scan im Modus LOW_LATENCY horcht ununterbrochen; die gerade
        // aufgebaute Verbindung bekommt daneben kaum Funkfenster, und ihre
        // Dienstsuche bleibt unbeantwortet. Anhalten muss deshalb der
        // Angerufene, sobald angeklopft wird - nicht erst, wenn er selbst
        // tippt. Wer zuerst tippte, rief sonst in ein Geraet hinein, das noch
        // mit voller Leistung suchte, und genau diese Reihenfolge scheiterte
        // reproduzierbar.
        beacon?.pauseScanning()

        if (chosen != null) return
        findViewById<TextView>(R.id.tvPartnerStatus).setText(R.string.partner_incoming)
    }

    private fun addFound(member: UserProfile, device: BluetoothDevice) {
        // Die Adresse wird bei jeder Sichtung aufgefrischt, die Zeile aber
        // nur einmal angelegt - sonst stuende derselbe Name mehrfach da.
        val known = devices.put(member.id, device) != null
        if (known) return

        val container = findViewById<LinearLayout>(R.id.llPartnerFound)
        findViewById<View>(R.id.tvPartnerEmpty).visibility = View.GONE
        findViewById<TextView>(R.id.tvPartnerStatus).setText(R.string.partner_pick)

        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_crew_member_row, container, false)

        row.findViewById<TextView>(R.id.tvMemberName).text =
            DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
        ImageLoader.into(
            row.findViewById(R.id.ivMemberPhoto),
            member.avatarUrl,
            circular = true,
            placeholder = android.R.drawable.ic_menu_gallery
        )
        row.setOnClickListener { choose(member) }

        container.addView(row)
    }

    /**
     * Verbindet sich mit dem Angetippten.
     *
     * Waehrend die Verbindung aufgebaut wird, wird nicht weiter gesucht: die
     * Suche und der Verbindungsaufbau teilen sich dieselbe Funkstrecke, und
     * beides zugleich macht den Aufbau langsamer und unzuverlaessiger.
     */
    /**
     * Jemanden auswaehlen - beide tun das, dann verbinden sich die Geraete.
     *
     * Angerufen wird nur von einer Seite, und welche das ist, ergibt der
     * Vergleich der Kennungen: die kleinere ruft an. Beide Geraete rechnen
     * dasselbe aus, also ruft genau einer - ohne dass sie sich darueber
     * verstaendigen muessten. Der andere wartet und nimmt an.
     *
     * Die Suche laeuft weiter, denn die Gegenseite muss uns noch finden
     * koennen, wenn sie erst spaeter antippt.
     */
    private fun choose(member: UserProfile) {
        val me = ownUserId ?: return
        if (chosen != null) return
        chosen = member
        attempts = 0

        // Ab hier wird nicht mehr gesucht: der Partner ist gefunden, und ein
        // laufender Scan nimmt dem Verbindungsaufbau die Funkzeit weg.
        beacon?.pauseScanning()

        val name = DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }

        // Die Liste weicht einem eigenen Bildschirm. Vorher blieben die Namen
        // stehen und darueber wechselte eine Zeile Text - man sah nicht, ob
        // ueberhaupt etwas geschieht, und tippte im Zweifel noch einmal.
        findViewById<View>(R.id.llSearch).visibility = View.GONE
        findViewById<View>(R.id.llLinking).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvLinkingTitle).text =
            getString(R.string.linking_title, name)
        showStep(R.string.linking_step_search)

        val status = findViewById<TextView>(R.id.tvPartnerStatus)

        if (me < member.id) {
            status.text = getString(R.string.partner_linking, name)
            ticker.post(retry)
        } else {
            // Wir warten erst einmal ab: der mit der kleineren Kennung ruft an,
            // und solange beide getippt haben, ist gleich alles fertig. Der
            // Server laeuft seit dem Oeffnen des Bildschirms, die Gegenseite
            // kann also jederzeit anklopfen.
            status.text = getString(R.string.partner_linking_wait, name)
            showStep(R.string.linking_step_wait)

            // Und wir rufen **nicht** an. Das ist der ganze Sinn der
            // Aufteilung: rufen beide, kreuzen sich die Anrufe und legen
            // einander auf - im Protokoll steht dann Fehler 22 auf der einen
            // und 133 auf der anderen Seite, und der Kreis dreht sich alle
            // paar Sekunden neu. Ein Rueckfall, der nach einer Weile doch
            // anruft, hat genau das wieder eingeschleppt.
            //
            // Wer wartet, wartet also. Der Server laeuft seit dem Oeffnen des
            // Bildschirms; sobald der andere tippt, steht die Verbindung.
        }
    }

    /**
     * Die Verbindung steht und beide Seiten wissen voneinander.
     *
     * Ab hier wird nicht mehr gesucht, sondern trainiert: Der Bildschirm
     * wechselt in die Trainingsansicht und bleibt offen. Er darf sich nicht
     * schliessen - mit ihm ginge die Verbindung, und die wird bis zum
     * Speichern gebraucht.
     */
    private fun paired(member: UserProfile) {
        val current = link ?: return
        ticker.removeCallbacks(retry)
        beacon?.stop()
        findViewById<View>(R.id.llLinking).visibility = View.GONE

        if (!JointSession.hasPartners()) {
            // Wer angetippt hat, fuehrt: er waehlt die Sportart und bestimmt
            // Beginn und Ende. Angetippt hat, wer das Geraet in der Liste
            // hatte.
            // Fuehrend ist, wer angerufen hat. Beide Geraete kommen zum
            // selben Ergebnis, denn genau eines von ihnen hat die Verbindung
            // aufgebaut - und es weiss das von sich selbst.
            JointSession.begin(current, isLeader = current.didWeCall())
            setUpSession()
        }
        JointSession.add(member)

        findViewById<View>(R.id.llSession).visibility = View.VISIBLE
        // Der Fuehrende darf weitersuchen und noch jemanden dazuholen; beim
        // Angerufenen ist die Liste ohnehin gesperrt.
        findViewById<View>(R.id.llSearch).visibility =
            if (JointSession.isLeader && !watch.hasStarted) View.VISIBLE else View.GONE

        // Alle erfahren, wer dabei ist - untereinander sind sie nicht
        // verbunden, nur mit dem Fuehrenden.
        if (JointSession.isLeader) sendRoster()

        showSession()
    }

    /** Schickt die ganze Runde an alle, die daran haengen. */
    private fun sendRoster() {
        val payloads = JointSession.partners.mapNotNull { CoLocation.payloadFor(it.id) }
        val me = ownUserId?.let { CoLocation.payloadFor(it) }
        link?.send(TrainingProtocol.rosterMessage(payloads + listOfNotNull(me)))
    }

    private fun setUpSession() {
        val openPicker = View.OnClickListener { pickSport() }
        findViewById<EditText>(R.id.etSessionSport).setOnClickListener(openPicker)
        findViewById<TextInputLayout>(R.id.tilSessionSport).setEndIconOnClickListener(openPicker)

        findViewById<MaterialButton>(R.id.btnSessionAction).setOnClickListener {
            if (watch.isRunning) stopTraining() else startTraining()
        }
        findViewById<MaterialButton>(R.id.btnSessionReconnect).setOnClickListener { reconnect() }
    }

    /** Nur der Fuehrende waehlt - so kann die Sportart nicht auseinandergehen. */
    private fun pickSport() {
        if (!JointSession.isLeader || watch.hasStarted) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_train_question)
            .setItems(Sports.ALL) { _, which ->
                val sport = Sports.ALL[which]
                JointSession.sport = sport
                link?.send(TrainingProtocol.sportMessage(sport))
                showSession()
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun startTraining() {
        if (JointSession.sport == null) {
            toast(R.string.session_need_sport)
            return
        }
        if (!JointSession.connected) {
            toast(R.string.session_disconnected)
            return
        }

        link?.send(TrainingProtocol.startMessage())
        findViewById<View>(R.id.llSearch).visibility = View.GONE
        beginClock()
    }

    private fun beginClock() {
        JointSession.connected = true
        watch.start(SystemClock.elapsedRealtime())
        ticker.removeCallbacks(tick)
        ticker.post(tick)
        showSession()
    }

    /**
     * Beendet das Training und geht ins Formular.
     *
     * Ohne eigene Mindestdauer. Was zu kurz ist, faengt das Formular ab - dort
     * gilt dieselbe Grenze wie fuer jedes andere Workout auch, und zwei
     * Stellen mit zwei Zahlen waeren eine zuviel.
     */
    private fun stopTraining() {
        val now = SystemClock.elapsedRealtime()
        watch.pause(now)
        ticker.removeCallbacks(tick)

        val seconds = watch.elapsedSeconds(now)
        link?.send(TrainingProtocol.stopMessage(seconds))
        JointSession.seconds = seconds
        handOver()
    }

    private fun handOver() {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_JOINT, true))
        finish()
    }

    /** Nachrichten des Fuehrenden - beim Folgenden laeuft alles von aussen. */
    private fun received(message: ByteArray) {
        when (TrainingProtocol.typeOf(message)) {
            TrainingProtocol.TYPE_SPORT -> {
                JointSession.sport = TrainingProtocol.sportFrom(message)
                showSession()
            }
            TrainingProtocol.TYPE_START -> {
                findViewById<View>(R.id.llSearch).visibility = View.GONE
                beginClock()
            }

            TrainingProtocol.TYPE_ROSTER -> {
                // Wer sonst noch mitlaeuft. Die eigene Person faellt dabei
                // heraus, der Fuehrende steht ohnehin schon in der Liste.
                TrainingProtocol.rosterFrom(message).forEach { payload ->
                    CoLocation.memberFor(payload, members, ownUserId)
                        ?.let { JointSession.add(it) }
                }
                showSession()
            }
            TrainingProtocol.TYPE_STOP -> {
                watch.pause(SystemClock.elapsedRealtime())
                ticker.removeCallbacks(tick)
                // Die Dauer des Fuehrenden gilt fuer beide. Zwei getrennt
                // laufende Uhren gehen sonst um Sekunden auseinander, und die
                // Absicherung verlangt dieselbe Minutenzahl.
                JointSession.seconds = TrainingProtocol.secondsFrom(message)
                handOver()
            }
        }
    }

    /** Die Verbindung ist weg: die Uhr haelt an, und beide erfahren es. */
    private fun linkLost() {
        JointSession.connected = false
        watch.pause(SystemClock.elapsedRealtime())
        ticker.removeCallbacks(tick)
        showSession()
    }

    private fun reconnect() {
        val partner = JointSession.partners.firstOrNull() ?: return
        val device = devices[partner.id]
        if (device == null) {
            // Nur der Fuehrende kann von sich aus wieder verbinden; beim
            // anderen kommt die Verbindung von der Gegenseite.
            toast(R.string.session_disconnected)
            return
        }
        link?.connectTo(device, partner)
    }

    /** Setzt alles im Trainingsbereich auf den jetzigen Stand. */
    private fun showSession() {
        val leader = JointSession.isLeader
        val names = JointSession.partners
            .map { DisplayName.of(it).ifEmpty { getString(R.string.unknown_member) } }
        val partnerName = names.firstOrNull() ?: getString(R.string.unknown_member)

        findViewById<TextView>(R.id.tvSessionPartner).text =
            getString(R.string.session_with, names.joinToString(", "))

        findViewById<EditText>(R.id.etSessionSport).setText(JointSession.sport.orEmpty())
        findViewById<TextInputLayout>(R.id.tilSessionSport).isEnabled = leader && !watch.hasStarted

        val offline = if (JointSession.connected) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvSessionWarning).setText(R.string.session_disconnected)
        findViewById<View>(R.id.tvSessionWarning).visibility = offline
        findViewById<View>(R.id.btnSessionReconnect).visibility = offline

        val action = findViewById<MaterialButton>(R.id.btnSessionAction)
        action.setText(if (watch.isRunning) R.string.session_stop else R.string.session_start)
        // Angefangen wird vom Fuehrenden - sonst koennte die Sportart noch
        // wechseln, waehrend beim anderen die Uhr schon laeuft. **Beendet**
        // wird von beiden: wer fertig ist, ist fertig, und der andere soll
        // nicht warten muessen. Der Stopp erreicht die Gegenseite jetzt auch
        // vom Angerufenen aus - vorher konnte der gar nichts zurueckschicken,
        // und seine Uhr lief nach dem Stopp allein weiter.
        action.visibility = if (leader || watch.isRunning) View.VISIBLE else View.GONE
        action.isEnabled = JointSession.connected

        findViewById<TextView>(R.id.tvSessionHint).text = when {
            watch.isRunning -> getString(R.string.session_running)
            leader -> getString(R.string.session_pick_sport)
            JointSession.sport == null -> getString(R.string.session_wait_sport, partnerName)
            else -> getString(R.string.session_wait_start, partnerName)
        }

        showTimer()
    }

    private fun showTimer() {
        val seconds = watch.elapsedSeconds(SystemClock.elapsedRealtime())
        findViewById<TextView>(R.id.tvSessionTimer).text =
            String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }

    /**
     * Sagt, woran gerade gearbeitet wird.
     *
     * Drei Schritte statt einer wandernden Textzeile: suchen, koppeln,
     * verbinden. Wer wartet, soll sehen koennen, wo es steht - und ob er
     * gerade etwas tun muss.
     */
    private fun showStep(textRes: Int) {
        val step = findViewById<TextView>(R.id.tvLinkingStep) ?: return
        if (findViewById<View>(R.id.llLinking).visibility == View.VISIBLE) {
            step.setText(textRes)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    /**
     * Der Kreis hoert auf sich zu drehen, und der Text sagt, was fehlt.
     *
     * Kein Abbruch: manche Geraete koennen empfangen, aber nicht senden. Dann
     * findet man die anderen weiterhin, wird nur selbst nicht gefunden - die
     * Liste kann sich also trotzdem noch fuellen.
     */
    /**
     * Meldungen des Verbindungsaufbaus auch auf dem Wartebildschirm zeigen.
     *
     * "Pairing… confirm on both devices" ist keine Fehlermeldung, sondern
     * eine Aufforderung - sie gehoert dorthin, wo der Nutzer gerade hinsieht.
     */
    private fun showStepFor(messageRes: Int) {
        when (messageRes) {
            R.string.partner_pairing -> showStep(R.string.linking_step_pairing)
            R.string.partner_connecting -> showStep(R.string.linking_step_link)
        }
    }

    private fun showProblem(messageRes: Int) {
        showStepFor(messageRes)
        // Waehrend gekoppelt wird, dreht sich der Kreis weiter: es passiert ja
        // etwas, nur ausserhalb der App. Bei allem anderen haelt er an.
        val busy = messageRes == R.string.partner_pairing
        findViewById<View>(R.id.piPartnerSearch).visibility =
            if (busy) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvPartnerStatus).setText(messageRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Senden und Empfangen laufen sonst weiter und ziehen Akku, auch wenn
        // niemand mehr hinsieht.
        beacon?.stop()
        beacon = null
        ticker.removeCallbacks(tick)
        // Sonst ruft der Wiederholversuch weiter an, auch wenn der Bildschirm
        // laengst zu ist.
        ticker.removeCallbacks(retry)

        // Die Verbindung wird nicht mehr gebraucht: Sportart und Dauer sind
        // ausgetauscht, alles Weitere traegt jeder fuer sich ein. Offen zu
        // lassen hiesse, auf beiden Geraeten Akku zu ziehen fuer nichts.
        link?.close()
        link = null
        if (!JointSession.isFinished()) JointSession.clear()
    }

    companion object {

        private const val EXTRA_JOINT = "joint_workout"

        /** Wie oft die Anzeige der Uhr nachgezogen wird. */
        private const val TICK_MS = 1000L

        /**
         * Abstand zwischen zwei Anrufversuchen.
         *
         * Lang genug, dass ein laufender Aufbau in Ruhe scheitern oder gelingen
         * kann - der GATT-Stack braucht fuer einen Fehlschlag bis zu dreissig
         * Sekunden -, und kurz genug, dass niemand das Gefuehl hat, es passiere
         * nichts.
         */
        private const val RETRY_MILLIS = 4000L

        /** Nach so vielen vergeblichen Anlaeufen wird abgebrochen. */
        private const val MAX_ATTEMPTS = 6



        fun intent(context: Context): Intent =
            Intent(context, TrainingPartnerActivity::class.java)

        /** Ob ein gemeinsames Training zustande gekommen ist. */
        fun isJoint(data: Intent?): Boolean = data?.getBooleanExtra(EXTRA_JOINT, false) == true
    }
}
