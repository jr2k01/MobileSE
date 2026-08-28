package com.example.mobilese

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Log

/**
 * Die tatsaechliche Verbindung zwischen zwei Telefonen.
 *
 * [PartnerBeacon] findet nur - es sagt "hier ist jemand aus deiner Crew". Hier
 * wird daraus eine Verbindung: eines der beiden Geraete verbindet sich mit dem
 * anderen und schreibt ihm seine Kennung. Danach wissen **beide** Seiten
 * voneinander, und beide bekommen die doppelten Punkte.
 *
 * ## Warum ueberhaupt eine Verbindung
 *
 * Aus der Werbung allein erfaehrt der Angetippte nichts. Er hoert zwar, dass
 * jemand da ist, aber nicht, dass ausgerechnet dieser Jemand gerade *ihn*
 * ausgewaehlt hat - die Werbung geht an alle. Ohne Verbindung waere die
 * Kopplung einseitig: der eine haette einen Partner, der andere nicht.
 *
 * ## Beide Rollen zugleich
 *
 * Jedes Geraet betreibt einen **Server** und kann zugleich **Client** werden.
 * Wer zuerst antippt, verbindet sich; der andere nimmt an. Wer welche Rolle
 * bekommt, entscheidet sich also erst im Moment des Antippens, und keiner der
 * beiden muss vorher etwas Besonderes tun.
 *
 * ## Mehr als zwei
 *
 * Der Antippende wird zur Mitte der Runde: Er verbindet sich mit jedem
 * einzeln und haelt alle Verbindungen zugleich. Die anderen sind jeweils nur
 * mit ihm verbunden, nicht untereinander - ein Netz aus jedem mit jedem waere
 * bei sechs Leuten fuenfzehn Verbindungen statt fuenf.
 *
 * Verbunden wird **nacheinander**. Zwei Verbindungsaufbauten zugleich bringt
 * Androids Bluetooth-Stack durcheinander; die Warteschlange arbeitet einen
 * nach dem anderen ab.
 *
 * ## Erst koppeln, dann verbinden
 *
 * Vor der Verbindung werden die Geraete im Sinne des Systems **gekoppelt**
 * ([BluetoothDevice.createBond]). Android zeigt dazu auf beiden Seiten einen
 * Dialog mit demselben Schluessel, und beide muessen bestaetigen - der
 * sichtbare Handschlag, den eine blosse Verbindung nicht hat.
 *
 * Der Bond bleibt bestehen. Beim naechsten gemeinsamen Training entfaellt der
 * Dialog deshalb, und die Geraete stehen so lange unter "Gekoppelte Geraete"
 * in den Systemeinstellungen, bis jemand sie dort entfernt.
 *
 * Die Kopplung ersetzt den Austausch der Kennungen nicht: ein Bond verbindet
 * zwei *Geraete*, er sagt nichts darueber, welches CrewFit-Konto darauf
 * angemeldet ist. Erst der Schreibvorgang danach macht daraus zwei Personen.
 *
 * @param onPartner Die Kennung des Gegenuebers, sobald die Verbindung steht
 *        und die Kennungen ausgetauscht sind. Kommt auf einem Thread des
 *        Bluetooth-Systems.
 * @param onLost Die Verbindung ist abgerissen - der andere ist gegangen, hat
 *        die App geschlossen oder Bluetooth abgeschaltet.
 */
class PartnerLink(
    private val context: Context,
    private val ownUserId: String,
    private val members: List<UserProfile>
) {

    /**
     * Die Rueckrufe sind veraenderlich, nicht fest eingebaut.
     *
     * Die Verbindung ueberdauert den Bildschirm, auf dem sie entstanden ist -
     * sie wird im Workout-Formular noch gebraucht. Waeren die Rueckrufe an die
     * erste Activity gebunden, zeigten sie danach auf einen Bildschirm, den es
     * nicht mehr gibt. Wer die Verbindung uebernimmt, setzt sie neu.
     */
    var onPartner: (UserProfile) -> Unit = {}
    var onLost: () -> Unit = {}
    var onProblem: (Int) -> Unit = {}

    /**
     * Eine Nachricht des Gegenuebers, siehe [TrainingProtocol]. Kommt auf
     * einem Thread des Bluetooth-Systems.
     */
    var onMessage: (ByteArray) -> Unit = {}

    /**
     * Jemand verbindet sich gerade mit uns.
     *
     * Ab da darf man selbst nicht mehr antippen: Zwei Geraete, die sich
     * gegenseitig anrufen, legen einander auf - im Protokoll steht dann auf
     * der einen Seite Fehler 22 und auf der anderen 19.
     */
    var onIncoming: () -> Unit = {}

    /**
     * Die gemerkte Adresse ist unbrauchbar geworden - es muss neu gesucht
     * werden, bevor der naechste Anlauf Sinn hat.
     */
    var onNeedsFreshAddress: () -> Unit = {}


    private val manager = context.getSystemService(BluetoothManager::class.java)

    private var server: BluetoothGattServer? = null

    /**
     * Die eigene Characteristic auf der Serverseite.
     *
     * Wird gebraucht, um dem Verbundenen etwas zu schicken: der Angerufene hat
     * keine Client-Verbindung und kann nicht schreiben - er kann nur
     * benachrichtigen, und dafuer muss er seine eigene Characteristic kennen.
     */
    private var inbox: BluetoothGattCharacteristic? = null

    /** Wer sich fuer Benachrichtigungen angemeldet hat. */
    private val listeners = mutableSetOf<String>()

    /**
     * Eine offene Verbindung samt der Stelle, an die geschrieben wird.
     *
     * Die Characteristic wird beim Verbinden gemerkt und nicht spaeter neu
     * gesucht: `getService()` fragt den zwischengespeicherten Dienstkatalog,
     * und der war nach dem Aufbau ploetzlich leer - `send()` fand nichts mehr
     * und schrieb still gar nicht. Die Sportart kam beim anderen nie an,
     * obwohl die Verbindung stand.
     */
    private class Peer(
        val gatt: BluetoothGatt,
        val inbox: BluetoothGattCharacteristic
    )

    /** Alle offenen Verbindungen, nach Geraeteadresse. */
    private val clients = mutableMapOf<String, Peer>()

    /** Noch abzuarbeitende Verbindungswuensche. */
    private val queue = ArrayDeque<Pair<BluetoothDevice, UserProfile>>()

    /** Wer sich mit uns verbunden hat - fuer die Serverseite. */
    private val peers = mutableSetOf<String>()

    /** Wen wir schon gemeldet haben - Rueckrufe koennen sich wiederholen. */
    private val confirmed = mutableSetOf<String>()

    /**
     * Ob **wir** die Verbindung aufgebaut haben.
     *
     * Entscheidet, wer die Sitzung fuehrt. Frueher rechnete der Bildschirm das
     * aus einem Vergleich der Kennungen aus - dann fuehrte immer dasselbe
     * Geraet, auch wenn das andere angerufen hatte. Wer anruft, fuehrt: das
     * ist die Regel, die man erwartet, wenn man selbst auf den Namen tippt.
     */
    private var weCalled = false

    /** Wer angerufen hat - erst nach [onPartner] aussagekraeftig. */
    fun didWeCall(): Boolean = weCalled

    /**
     * Ob fuer diese Verbindung schon einmal der Katalog verworfen wurde.
     *
     * Nur ein Versuch: findet sich die Characteristic auch nach einer frischen
     * Suche nicht, hat die Gegenseite den Dienst wirklich nicht, und ein
     * zweiter Anlauf wuerde sich nur im Kreis drehen.
     */
    private var cacheDropped = false

    /** Ob gerade jemand an unserem Server haengt. */
    fun hasPeers(): Boolean = peers.isNotEmpty()

    /** Worauf gerade gewartet wird, waehrend das System koppelt. */
    private var pending: Pair<BluetoothDevice, UserProfile>? = null

    /** Ob [adapterReceiver] angemeldet ist - zweimal waere ein Fehler. */
    private var watchingAdapter = false

    /** Zaehlt die Frist ab, die der Kopplung eingeraeumt wird. */
    private val bondTimer = Handler(Looper.getMainLooper())

    /**
     * Wartet darauf, dass die Paketgroesse ausgehandelt wird.
     *
     * Androids Stack beantwortet `requestMtu()` nicht immer. Kam keine
     * Antwort, stand die Kette still: `discoverServices()` folgt erst auf
     * `onMtuChanged`, und ohne das geschah gar nichts mehr - die Verbindung
     * war offen, aber nichts passierte. Diese Frist geht danach trotzdem
     * weiter, mit der voreingestellten Paketgroesse.
     */
    private val mtuTimer = Handler(Looper.getMainLooper())

    /** Die Verbindung, auf deren Paketgroesse gerade gewartet wird. */
    private var awaitingMtu: BluetoothGatt? = null

    /** Zaehlt die Frist ab, die der Dienstsuche eingeraeumt wird. */
    private val discoveryTimer = Handler(Looper.getMainLooper())

    /** Die Verbindung, deren Dienste gerade gesucht werden. */
    private var awaitingDiscovery: BluetoothGatt? = null

    /** Ob die Dienstsuche fuer diese Verbindung schon einmal wiederholt wurde. */
    private var discoveryRetried = false

    /** Ob gerade eine alte Kopplung geloest wird, bevor neu gekoppelt wird. */
    private var unbonding = false

    /** Die Characteristic der Verbindung, die gerade fertig wird. */
    private var pendingInbox: BluetoothGattCharacteristic? = null

    /**
     * Ob die alte Kopplung in dieser Sitzung schon einmal geloest wurde.
     *
     * Nur einmal: sonst loeste jeder Wiederholversuch sie erneut, und die
     * beiden Geraete kamen aus dem Koppeln gar nicht mehr heraus.
     */
    private var bondRefreshed = false

    /**
     * Mit wem die Verbindung gerade aufgebaut wird.
     *
     * Solange das hier steht, ist die Verbindung noch im Werden. Gemeldet wird
     * die Kopplung erst, wenn die Kennungen ausgetauscht sind - vorher waere
     * es eine Behauptung.
     */
    private var connecting: Pair<BluetoothDevice, UserProfile>? = null

    /**
     * Ein zweiter Anlauf, falls der erste scheitert.
     *
     * Der erste Verbindungsversuch geht unter Android haeufig mit Fehler 133
     * daneben, ohne dass etwas kaputt waere - ein Wiederholen genuegt meist.
     * Genau einmal, damit es bei einem echten Problem nicht endlos weiterlaeuft.
     */
    private var retried = false

    /**
     * Hoert zu, waehrend das System koppelt.
     *
     * Der Vorgang laeuft ausserhalb der App: Android zeigt die Dialoge, und
     * das Ergebnis kommt als Rundruf zurueck. Ohne diesen Empfaenger wuesste
     * die App nie, ob jemand bestaetigt oder abgelehnt hat.
     */
    /**
     * Das eigene Funkmodul wurde abgeschaltet.
     *
     * Dann kommt kein `onConnectionStateChange` mehr - die Rueckrufe des
     * Stacks sterben mit ihm. Ohne diesen Empfaenger merkte ein Geraet nur,
     * wenn der **andere** wegfiel, und lief nach dem eigenen Ausfall munter
     * weiter: die Uhr des einen stand, die des anderen zaehlte, und am Ende
     * hatten zwei gemeinsam Trainierende verschiedene Zeiten. Genau das sollte
     * die gemeinsame Sitzung verhindern.
     */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (state != BluetoothAdapter.STATE_TURNING_OFF &&
                state != BluetoothAdapter.STATE_OFF
            ) return

            // Aufraeumen, solange der Stack noch antwortet.
            val had = clients.isNotEmpty() || peers.isNotEmpty()
            clients.values.forEach { closeQuietly(it.gatt) }
            clients.clear()
            peers.clear()
            confirmed.clear()
            weCalled = false
            cacheDropped = false
            unbonding = false
            bondRefreshed = false
            pendingInbox = null
            connecting = null

            if (had) onLost()
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val waiting = pending ?: return
            val device = intentDevice(intent) ?: return

            // Die Adresse aus dieser Meldung ist die **echte** des Geraets,
            // waehrend wir mit der zufaelligen aus der Werbung angefragt
            // haben. Beide sind verschieden, und ein strenger Vergleich hat
            // die geglueckte Kopplung deshalb verworfen: die App wartete
            // danach noch die vollen zwoelf Sekunden Frist ab, obwohl laengst
            // alles bestaetigt war. Bei einer Kopplung, die wir selbst
            // angestossen haben, zaehlt daher die Meldung selbst - und zwar
            // mit ihrer Adresse, denn die bleibt stabil.
            val same = device.address == waiting.first.address

            when (intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                BluetoothDevice.BOND_BONDED -> {
                    bondTimer.removeCallbacks(giveUpOnBond)
                    pending = null
                    openConnection(if (same) waiting.first else device, waiting.second)
                }
                BluetoothDevice.BOND_NONE -> {
                    if (!same) return
                    if (unbonding) {
                        // Die alte Kopplung ist geloest - jetzt frisch koppeln.
                        // Damit baut Android auch den Dienstkatalog neu auf.
                        unbonding = false
                        bondTimer.removeCallbacks(giveUpOnBond)
                        bondTimer.postDelayed(giveUpOnBond, BOND_WAIT_MILLIS)
                        try {
                            if (!device.createBond()) giveUpOnBond.run()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Could not pair: ${e.message}")
                            giveUpOnBond.run()
                        }
                        return
                    }

                    // Abgelehnt, weggetippt oder fehlgeschlagen. Frueher war
                    // hier Schluss. Jetzt wird trotzdem verbunden: wer die
                    // Abfrage wegtippt, will meistens trainieren und nicht
                    // seine Bluetooth-Einstellungen pflegen.
                    bondTimer.removeCallbacks(giveUpOnBond)
                    giveUpOnBond.run()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun intentDevice(intent: Intent?): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    // --- Serverseite: jemand verbindet sich mit uns ---

    private val serverCallback = object : BluetoothGattServerCallback() {

        /**
         * Nur der eigene Partner zaehlt. In Reichweite kann sich auch etwas
         * anderes an- und abmelden; das darf kein laufendes Training anhalten.
         */
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val address = device?.address
                address?.let { peers.add(it) }

                // Der eigene Server sieht **dieselbe** Strecke, die wir gerade
                // selbst aufbauen - eine Funkverbindung ist keine Einbahn. Das
                // ist kein Anruf von aussen, und ihn dafuer zu halten hiess,
                // den eigenen Aufbau abzuwuergen: onMtuChanged kam dann nie,
                // und nach dreissig Sekunden fiel die Verbindung mit Status 22.
                // Ob es beide zugleich versuchen, verraet erst eine andere
                // Adresse als die, die wir gerade anrufen.
                if (address != null && address == connecting?.first?.address) return

                // Wir sind der Angerufene. Ein eigener Anruf, der noch im
                // Aufbau ist, kaeme sich mit diesem hier ins Gehege.
                connecting?.let {
                    connecting = null
                    queue.clear()
                    try {
                        clients.values.forEach { peer -> peer.gatt.disconnect() }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Could not drop our own attempt: ${e.message}")
                    }
                }
                onIncoming()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                device?.address != null &&
                peers.remove(device.address) &&
                confirmed.isNotEmpty()
            ) {
                onLost()
            }
        }

        /**
         * Der Verbundene meldet sich fuer Benachrichtigungen an.
         *
         * Muss beantwortet werden, sonst wartet die Gegenseite bis zum
         * Zeitablauf und schickt danach nichts mehr.
         */
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            device?.address?.let { listeners.add(it) }
            if (!responseNeeded) return
            try {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            } catch (e: SecurityException) {
                Log.e(TAG, "Could not confirm the subscription: ${e.message}")
            }
        }

        /**
         * Der Verbindende schreibt seine Kennung. Erst damit steht fest, wer
         * es ist - die Verbindung allein sagt nur, dass jemand da ist.
         */
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                try {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Could not answer the write: ${e.message}")
                }
            }

            // Die erste Nachricht ist die Kennung - daran erkennt der
            // Angetippte, wer sich verbunden hat. Alles Weitere gehoert zum
            // Training und wandert nach oben.
            val member = CoLocation.memberFor(value, members, ownUserId)
            if (member != null) {
                confirm(member)
                return
            }
            value?.let(onMessage)
        }

    }

    // --- Clientseite: wir verbinden uns mit jemandem ---

    private val clientCallback = object : BluetoothGattCallback() {

        /**
         * Die Geraete sind verbunden - noch ohne zu wissen, wer daran sitzt.
         *
         * Von hier an laeuft eine Kette, und jeder Schritt wartet auf den
         * vorigen: erst die Paketgroesse, dann die Dienste, dann die Kennung.
         * Androids GATT-Stack bearbeitet immer nur **einen** Vorgang zugleich -
         * zwei Aufrufe hintereinander, und der zweite geht verloren.
         */
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    retried = false
                    discoveryRetried = false

                    // Nicht sofort suchen.
                    //
                    // Ist das Geraet schon gekoppelt, verschluesselt der Stack
                    // die Strecke unmittelbar nach dem Verbinden. Ein
                    // `discoverServices()` in genau dieses Fenster hinein wird
                    // verworfen - ohne Fehler, ohne Rueckruf, ohne irgendetwas.
                    // Danach steht die Verbindung offen da und es geschieht
                    // nichts mehr, bis sie nach dreissig Sekunden abbricht.
                    //
                    // Das ist der Grund, warum es immer nur beim ersten Mal
                    // ging: nach einer frischen Kopplung ist die
                    // Verschluesselung schon ausgehandelt, das Fenster gibt es
                    // dann gar nicht. Bei jedem spaeteren Versuch schon.
                    val settle =
                        if (gatt.device?.bondState == BluetoothDevice.BOND_BONDED) {
                            ENCRYPTION_SETTLE_MILLIS
                        } else {
                            0L
                        }
                    discoveryTimer.postDelayed({ lookForServices(gatt) }, settle)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    // Diese Verbindung ist erledigt, gleich ob sie stand oder
                    // nie zustande kam - ihre Registrierung im GATT-Stack muss
                    // zurueck. Android vergibt davon nur eine feste Zahl je
                    // Prozess, und `connect()` fordert bei jedem Aufruf eine
                    // neue an. Ohne dieses close() blieb je Fehlversuch eine
                    // haengen; waren sie aufgebraucht, endete jeder weitere
                    // Aufbau sofort mit Status 133 - erst der zweite Versuch
                    // eines Abends, dann jeder.
                    discoveryTimer.removeCallbacksAndMessages(null)
                    awaitingDiscovery = null
                    closeQuietly(gatt)

                    val address = gatt.device?.address
                    if (address != null && clients.remove(address) != null) {
                        // Eine bestehende Verbindung ist weg.
                        onLost()
                        return
                    }
                    // Noch nie richtig verbunden gewesen: das ist kein Abriss,
                    // sondern ein misslungener Aufbau. Einmal wiederholen.
                    val target = connecting
                    if (!retried && target != null) {
                        retried = true
                        Log.w(TAG, "Connect failed with status $status, trying once more")
                        openConnection(target.first, target.second)
                    } else {
                        onProblem(R.string.partner_link_failed)
                        connecting = null
                        next()
                    }
                }
            }
        }

        /**
         * Die Gegenseite meldet, dass sich ihre Dienste geaendert haben.
         *
         * Dann ist der Zwischenspeicher hin, und es muss neu gesucht werden -
         * sonst arbeitet man mit einem Katalog weiter, den es so nicht mehr
         * gibt.
         */
        override fun onServiceChanged(gatt: BluetoothGatt) {
            try {
                gatt.discoverServices()
            } catch (e: SecurityException) {
                Log.e(TAG, "Could not look again after a change: ${e.message}")
            }
        }

        /** Die Anmeldung steht - ab jetzt gilt die Kopplung. */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            pendingInbox?.let { settle(gatt, it) }
        }

        /** Die Gegenseite hat etwas geschickt. */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onMessage(value)
        }

        @Deprecated("Vor Android 13 kommt der Inhalt aus der Characteristic.")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { onMessage(it) }
        }

        /**
         * Die Paketgroesse steht. Mehr passiert hier nicht mehr - gesucht wird
         * schon beim Verbinden, damit ein ausbleibender Rueckruf den Aufbau
         * nicht anhaelt.
         */
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "Packet size is now $mtu")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discoveryTimer.removeCallbacksAndMessages(null)
            awaitingDiscovery = null

            val characteristic = gatt
                .getService(CoLocation.SERVICE_UUID)
                ?.getCharacteristic(CoLocation.CHARACTERISTIC_UUID)

            val payload = CoLocation.payloadFor(ownUserId)
            if (characteristic == null || payload == null) {
                // Womoeglich ist der Katalog nur veraltet. Android merkt sich
                // die Dienste eines Geraets und liefert sie beim naechsten Mal
                // aus dem Zwischenspeicher - eine "Suche", die sieben
                // Millisekunden dauert, hat gar nicht gesucht. Aendert sich
                // der Dienst danach, sieht die Gegenseite auf ewig den alten
                // Stand: bei uns fand ein Geraet die Characteristic nie, das
                // andere sofort.
                //
                // refresh() wirft den Zwischenspeicher weg. Die Methode ist
                // nicht Teil der oeffentlichen Schnittstelle, deshalb ueber
                // Reflexion und mit Rueckfall - klappt sie nicht, bleibt es
                // beim bisherigen Verhalten.
                if (!cacheDropped && payload != null && dropCache(gatt)) {
                    cacheDropped = true
                    Log.w(TAG, "Service unknown, dropped the cache and looking again")
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Could not look again: ${e.message}")
                        giveUp(gatt)
                    }
                    return
                }

                // Die Gegenseite hat den Dienst nicht - eine aeltere Fassung
                // der App, oder ein fremdes Geraet mit derselben Kennung.
                giveUp(gatt)
                return
            }

            write(gatt, characteristic, payload)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                giveUp(gatt)
                return
            }

            // Noch nicht fertig: erst die Benachrichtigungen einschalten,
            // sonst kann die Gegenseite uns nie etwas sagen. Das ist ein
            // eigener Vorgang und muss abgewartet werden - der Stack
            // bearbeitet immer nur einen.
            // Ab hier steht die Verbindung. Die Characteristic wandert mit
            // in die Liste - spaeter ist sie ueber den Katalog nicht mehr
            // verlaesslich zu finden.
            pendingInbox = characteristic

            if (subscribe(gatt, characteristic)) return

            // Der Deskriptor liess sich nicht schreiben. Dann eben einseitig:
            // eine halbe Verbindung ist besser als gar keine.
            settle(gatt, characteristic)
        }
    }

    /**
     * Startet den Server, damit andere sich verbinden koennen.
     *
     * Laeuft auf beiden Geraeten, unabhaengig davon, wer am Ende antippt.
     */
    fun startServer() {
        if (!AppPermission.NEARBY.isGranted(context)) {
            onProblem(R.string.partner_permission_missing)
            return
        }

        if (!watchingAdapter) {
            context.registerReceiver(
                adapterReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
            watchingAdapter = true
        }

        // Ab hier hoert der geteilte Server auf uns.
        GattServerHost.delegate = serverCallback

        // Steht er schon, wird er weiterbenutzt - siehe GattServerHost.
        GattServerHost.server?.let {
            server = it
            inbox = GattServerHost.inbox
            return
        }

        try {
            val opened = manager?.openGattServer(context.applicationContext, GattServerHost.callback)
            if (opened == null) {
                onProblem(R.string.partner_link_failed)
                return
            }
            server = opened
            GattServerHost.server = opened

            // Schreiben **und** benachrichtigen: ohne Notify koennte nur
            // der Anrufende etwas sagen. Wer angerufen wurde, haette seine
            // Uhr nicht anhalten koennen, ohne dass der andere weiterlaeuft.
            val inbox = BluetoothGattCharacteristic(
                CoLocation.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                // Der genormte Deskriptor, ueber den ein Client die
                // Benachrichtigungen einschaltet. Ohne ihn nimmt der Stack
                // die Anmeldung gar nicht erst entgegen.
                addDescriptor(
                    BluetoothGattDescriptor(
                        CoLocation.CONFIG_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ or
                                BluetoothGattDescriptor.PERMISSION_WRITE
                    )
                )
            }
            this.inbox = inbox
            GattServerHost.inbox = inbox
            val service = BluetoothGattService(
                CoLocation.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            ).apply { addCharacteristic(inbox) }

            opened.addService(service)
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not open the server: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    /**
     * Verbindet sich mit dem angetippten Geraet.
     *
     * [member] ist bereits bekannt - er stammt aus der Werbung. Gemeldet wird
     * er trotzdem erst, wenn die Verbindung steht: vorher waere es eine
     * Behauptung, keine Kopplung.
     */
    fun connectTo(device: BluetoothDevice, member: UserProfile) {
        if (!AppPermission.NEARBY.isGranted(context)) {
            onProblem(R.string.partner_permission_missing)
            return
        }
        if (clients.containsKey(device.address)) return
        // Schon in Arbeit oder schon vorgemerkt. Ohne diese Sperre legte der
        // Wiederholversuch bei jedem Anlauf einen weiteren Wunsch in die
        // Schlange, und nach einer Minute standen dort fuenfzehn.
        if (connecting?.first?.address == device.address) return
        if (queue.any { it.first.address == device.address }) return

        queue.addLast(device to member)
        // Laeuft schon einer, kommt dieser hier hinterher. Zwei
        // Verbindungsaufbauten zugleich bringt den Stack durcheinander.
        if (pending == null && connecting == null) next()
    }

    /** Ob gerade ein Aufbau laeuft - der Wiederholversuch haelt sich daran. */
    fun isBusy(): Boolean = pending != null || connecting != null

    /** Nimmt den naechsten Wunsch aus der Schlange, wenn gerade nichts laeuft. */
    private fun next() {
        if (pending != null || connecting != null) return
        val (device, member) = queue.removeFirstOrNull() ?: return
        beginWith(device, member)
    }

    /**
     * Beginnt mit der Kopplung - aber sie darf den Aufbau nicht aufhalten.
     *
     * Gekoppelt wird weiterhin: das ist das Merkmal, um das es geht, und die
     * Verbindung steht danach in den Bluetooth-Einstellungen beider Geraete.
     * Nur **warten** darf die App nicht mehr darauf. Bluetooth Low Energy
     * wirbt mit wechselnden Zufallsadressen; bei jedem Suchlauf sieht die App
     * eine andere Adresse desselben Geraets, und `bondState` steht darauf auf
     * BOND_NONE - auch wenn die beiden laengst gekoppelt sind. Mal loest der
     * Stack die Adresse zur bekannten Identitaet auf, mal nicht. Im zweiten
     * Fall verlangte die App eine neue Kopplung, die binnen Sekunden auf
     * **beiden** Geraeten bestaetigt sein wollte; wer das verpasste, sah nur
     * "could not connect". Das war die Ursache dafuer, dass das gemeinsame
     * Training mal ging und mal nicht.
     *
     * Jetzt laeuft eine Frist mit: wird binnen [BOND_WAIT_MILLIS] bestaetigt,
     * geht es wie bisher mit einer richtig gekoppelten Strecke weiter.
     * Bestaetigt niemand, wird trotzdem verbunden - die Characteristic ist
     * unverschluesselt, und der Nachweis der Naehe haengt am Funkkontakt und
     * am Austausch der Kennungen, nicht am Eintrag in den Einstellungen.
     */
    private fun beginWith(device: BluetoothDevice, member: UserProfile) {
        try {
            // Eine bestehende Kopplung wird geloest, bevor verbunden wird.
            //
            // Das ist der Kern der ganzen Sache, und es ist am Geraet belegt:
            // ist die Gegenseite gekoppelt, steht die Verbindung binnen einer
            // Sekunde - und `discoverServices()` liefert zwar true, ruft aber
            // nie zurueck. Zweimal sechs Sekunden gewartet, nichts. Ohne
            // Kopplung antwortet dieselbe Suche sofort.
            //
            // Ein frueherer Anlauf scheiterte daran, dass danach die alte
            // Adresse weiterverwendet wurde: die aus der Werbung ist zufaellig
            // und nur solange aufloesbar, wie die Kopplung besteht - jeder
            // Aufbau endete mit Status 133. Deshalb wird hier nicht sofort
            // weitergemacht, sondern neu gesucht; der naechste Anlauf nimmt
            // die frische Adresse und koppelt dann sauber neu, mit Abfrage auf
            // beiden Geraeten.
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                connecting = null
                if (!bondRefreshed) {
                    bondRefreshed = true
                    dropBond(device)
                    onProblem(R.string.partner_pairing)
                }
                onNeedsFreshAddress()
                return
            }

            pending = device to member
            context.registerReceiver(
                bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
            onProblem(R.string.partner_pairing)

            // Ohne diese Frist wartete die App unbegrenzt auf einen Dialog,
            // den vielleicht niemand sieht.
            bondTimer.postDelayed(giveUpOnBond, BOND_WAIT_MILLIS)

            if (!device.createBond()) {
                // Manche Geraete lehnen das rundheraus ab. Dann eben ohne.
                Log.w(TAG, "createBond refused, connecting without a bond")
                giveUpOnBond.run()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not pair: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    /**
     * Die Frist ist um: verbinden, auch ohne Kopplung.
     *
     * Der Empfaenger bleibt angemeldet - bestaetigt jemand die Abfrage spaeter
     * doch noch, sind die Geraete beim naechsten Mal gekoppelt und der Dialog
     * bleibt aus.
     */
    private val giveUpOnBond = Runnable {
        val waitingFor = pending ?: return@Runnable
        pending = null
        Log.w(TAG, "Nobody confirmed the pairing, connecting anyway")
        openConnection(waitingFor.first, waitingFor.second)
    }

    /**
     * Die Verbindung selbst - erst nach erfolgreicher Kopplung.
     *
     * Gemeldet wird hier noch nichts. Wer sich verbindet, weiss zwar, wen er
     * angetippt hat, aber die Gegenseite weiss noch nichts von ihm - und ohne
     * das ist es keine Kopplung, sondern eine Absicht.
     */
    private fun openConnection(device: BluetoothDevice, member: UserProfile) {
        try {
            connecting = device to member
            device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not connect: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    /**
     * Schaltet die Benachrichtigungen der Gegenseite ein.
     *
     * @return true, wenn der Vorgang laeuft - dann geht es in [onDescriptorWrite]
     *         weiter. false, wenn er gar nicht erst begonnen werden konnte.
     */
    private fun subscribe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val descriptor = characteristic.getDescriptor(CoLocation.CONFIG_UUID) ?: return false
        return try {
            if (!gatt.setCharacteristicNotification(characteristic, true)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not subscribe: ${e.message}")
            false
        }
    }

    /** Die Kopplung gilt: eintragen, melden, den naechsten aus der Schlange. */
    private fun settle(gatt: BluetoothGatt, inbox: BluetoothGattCharacteristic) {
        weCalled = true

        // Bewusst **keine** MTU-Anfrage mehr.
        //
        // Androids Stack beantwortet sie auf diesen Geraeten nicht, und ein
        // unbeantworteter Vorgang blockiert die Warteschlange: jeder Schreib-
        // versuch danach lief ins Leere. Die Sportart kam beim anderen nie an,
        // obwohl die Verbindung stand und send() aufgerufen wurde.
        //
        // Mit der Voreinstellung passen 20 Byte in eine Nachricht. Das reicht
        // fuer Sportart, Start und Stopp und fuer eine Runde zu zweit; erst
        // eine groessere Gruppe waere knapp - und eine Verbindung, die
        // funktioniert, ist mehr wert als eine, die groessere Pakete koennte.

        connecting?.let { (device, member) ->
            clients[device.address] = Peer(gatt, inbox)
            connecting = null
            confirm(member)
        }
        next()
    }

    /**
     * Bricht den laufenden Verbindungsaufbau ab und macht den Weg frei.
     *
     * Frueher meldeten die Fehlerpfade nur das Problem und kehrten zurueck.
     * [connecting] blieb dabei belegt, und weil [next] nichts anfaengt,
     * solange dort etwas steht, verschluckte das Geraet **jeden weiteren
     * Versuch stillschweigend** - bis die App neu gestartet wurde. Auf dem
     * Bildschirm stand dann noch die alte Meldung, und es sah aus, als
     * reagiere das Antippen nicht mehr.
     */
    private fun giveUp(gatt: BluetoothGatt) {
        mtuTimer.removeCallbacks(mtuGaveUp)
        awaitingMtu = null
        discoveryTimer.removeCallbacksAndMessages(null)
        awaitingDiscovery = null
        closeQuietly(gatt)
        onProblem(R.string.partner_link_failed)
        connecting = null
        retried = false
        next()
    }

    /**
     * Sucht die Dienste der Gegenseite - und gibt nicht stillschweigend auf.
     *
     * `discoverServices()` liefert false, wenn der Stack gerade beschaeftigt
     * ist, und manchmal true, ohne dass je ein Rueckruf kommt. Beides sah
     * bisher gleich aus: nichts. Darum eine Frist, und danach ein zweiter
     * Anlauf, bevor die Verbindung sauber beendet wird.
     */
    private fun lookForServices(gatt: BluetoothGatt) {
        awaitingDiscovery = gatt
        val started = try {
            gatt.discoverServices()
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not look for services: ${e.message}")
            onProblem(R.string.partner_permission_missing)
            return
        }
        if (!started) Log.w(TAG, "The stack refused the service lookup")
        discoveryTimer.removeCallbacks(discoveryGaveUp)
        discoveryTimer.postDelayed(discoveryGaveUp, DISCOVERY_WAIT_MILLIS)
    }

    /**
     * Auf die Dienstsuche kam keine Antwort.
     *
     * Ein zweiter Anlauf - inzwischen ist die Strecke verschluesselt und der
     * Stack wieder frei. Hilft auch der nicht, wird abgebrochen statt dreissig
     * Sekunden lang auf einen Rueckruf zu warten, der nicht kommt.
     */
    private val discoveryGaveUp = Runnable {
        val gatt = awaitingDiscovery ?: return@Runnable
        if (!discoveryRetried) {
            discoveryRetried = true
            Log.w(TAG, "No answer to the service lookup, asking once more")
            lookForServices(gatt)
            return@Runnable
        }
        Log.w(TAG, "The services stayed silent, dropping this connection")
        awaitingDiscovery = null
        giveUp(gatt)
    }

    /**
     * Die Antwort auf die MTU-Anfrage bleibt aus - trotzdem weitermachen.
     *
     * Mit der voreingestellten Paketgroesse passen zwanzig Byte in eine
     * Nachricht. Das reicht fuer Sportart, Start und Stopp und fuer eine
     * Runde zu zweit; erst eine groessere Gruppe braucht mehr. Eine
     * Verbindung, die etwas kann, ist besser als eine, die dasteht.
     */
    private val mtuGaveUp = Runnable {
        val gatt = awaitingMtu ?: return@Runnable
        awaitingMtu = null
        Log.w(TAG, "No answer to the MTU request, looking for services anyway")
        try {
            gatt.discoverServices()
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not look for services: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    /**
     * Loest eine bestehende Kopplung.
     *
     * `removeBond()` gehoert nicht zur oeffentlichen Schnittstelle - es gibt
     * dafuer keinen Ersatz. Klappt es nicht, wird false zurueckgegeben und
     * ganz normal mit der bestehenden Kopplung verbunden.
     */
    private fun dropBond(device: BluetoothDevice): Boolean = try {
        val remove = device.javaClass.getMethod("removeBond")
        (remove.invoke(device) as? Boolean ?: false).also {
            if (it) Log.i(TAG, "Dropped the old bond so the services are read again")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not drop the bond: ${e.message}")
        false
    }

    /**
     * Wirft den zwischengespeicherten Dienstkatalog eines Geraets weg.
     *
     * `BluetoothGatt.refresh()` gehoert nicht zur oeffentlichen Schnittstelle;
     * es gibt dafuer auch keinen Ersatz. Klappt der Aufruf nicht - etwa weil
     * eine neuere Android-Fassung ihn versperrt -, wird false zurueckgegeben
     * und der Aufrufer macht ohne weiter.
     */
    private fun dropCache(gatt: BluetoothGatt): Boolean = try {
        val refresh = gatt.javaClass.getMethod("refresh")
        refresh.invoke(gatt) as? Boolean ?: false
    } catch (e: Exception) {
        Log.w(TAG, "Could not drop the service cache: ${e.message}")
        false
    }

    /**
     * Gibt eine Verbindung samt ihrer Registrierung im GATT-Stack frei.
     *
     * `disconnect()` allein genuegt nicht: es trennt die Funkverbindung, laesst
     * die Anmeldung des Clients aber stehen. Erst `close()` gibt sie zurueck.
     */
    private fun closeQuietly(gatt: BluetoothGatt) {
        try {
            gatt.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not release the connection: ${e.message}")
        }
    }

    private fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val r = gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                val ok = gatt.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not write: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    /**
     * Schickt eine Nachricht an die Gegenseite.
     *
     * Nur von der verbindenden Seite aus. Der Angetippte antwortet nicht mit
     * Nachrichten, sondern legt seinen Stand ueber [publish] bereit - dort
     * holt der andere ihn ab.
     */
    fun send(message: ByteArray) {
        // Als Anrufender: schreiben - an die beim Verbinden gemerkte Stelle.
        clients.values.forEach { peer -> write(peer.gatt, peer.inbox, message) }

        // Als Angerufener: benachrichtigen. Frueher fehlte das, und wer
        // angerufen worden war, konnte nichts zurueckschicken - sein "Stopp"
        // erreichte den anderen nie, dessen Uhr lief weiter.
        notifyListeners(message)
    }

    private fun notifyListeners(message: ByteArray) {
        val characteristic = inbox ?: return
        val open = server ?: return

        listeners.toList().forEach { address ->
            val device = open.getService(CoLocation.SERVICE_UUID)
                ?.let { manager?.adapter?.getRemoteDevice(address) } ?: return@forEach
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    open.notifyCharacteristicChanged(device, characteristic, false, message)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = message
                    @Suppress("DEPRECATION")
                    open.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Could not notify: ${e.message}")
            }
        }
    }

    /** Jeden hoechstens einmal melden - Rueckrufe koennen sich wiederholen. */
    private fun confirm(member: UserProfile) {
        if (!confirmed.add(member.id)) return
        onPartner(member)
    }

    /**
     * Beendet Verbindung und Server.
     *
     * Beides muss geschlossen werden, sonst bleibt die Verbindung offen und
     * zieht auf beiden Geraeten Akku - auch wenn niemand mehr hinsieht.
     */
    fun close(client: Boolean = true, server: Boolean = true) {
        bondTimer.removeCallbacks(giveUpOnBond)
        mtuTimer.removeCallbacks(mtuGaveUp)
        awaitingMtu = null
        discoveryTimer.removeCallbacksAndMessages(null)
        awaitingDiscovery = null
        if (server && watchingAdapter) {
            runCatching { context.unregisterReceiver(adapterReceiver) }
            watchingAdapter = false
        }
        if (pending != null || server) {
            // Abmelden, auch wenn nie etwas ankam: ein Empfaenger, der die
            // Activity ueberlebt, ist ein Leck und faellt spaeter als Absturz
            // beim naechsten Rundruf auf.
            runCatching { context.unregisterReceiver(bondReceiver) }
            pending = null
        }
        try {
            if (client) {
                clients.values.forEach {
                    it.gatt.disconnect()
                    it.gatt.close()
                }
                clients.clear()
                queue.clear()
                connecting = null
            }
            if (server) {
                // Die Strecken werden gekappt, der Server bleibt stehen.
                //
                // `close()` allein gibt nur das Server-Objekt frei - die
                // Funkverbindung zur Gegenseite bleibt bestehen. Beim naechsten
                // gemeinsamen Training traf `connectGatt` dann auf ein Geraet,
                // zu dem die Strecke noch stand, und der Aufbau lief ins Leere.
                // Dafuer gibt es cancelConnection.
                val open = this.server
                if (open != null) {
                    peers.toList().forEach { address ->
                        manager?.adapter?.getRemoteDevice(address)?.let { device ->
                            open.cancelConnection(device)
                        }
                    }
                }

                // Und hier wird **nicht** geschlossen. Warum, steht bei
                // GattServerHost: geschlossen und neu geoeffnet wandern die
                // Attributnummern, und die Gegenseite schreibt aus ihrem
                // Zwischenspeicher an die alte Nummer ins Leere.
                GattServerHost.delegate = null
                this.server = null
                this.inbox = null
                listeners.clear()
            }

            // Ohne dieses Aufraeumen haelt eine neue Sitzung die alten
            // Bekanntschaften fuer erledigt: `confirmed` sperrt jede zweite
            // Meldung desselben Partners, und der Trainingsbildschirm ginge
            // nie auf.
            peers.clear()
            confirmed.clear()
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not close cleanly: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PartnerLink"

        /**
         * Wie lange auf die Bestaetigung der Kopplung gewartet wird.
         *
         * Lang genug, dass zwei Leute, die nebeneinander stehen, beide OK
         * tippen koennen - und kurz genug, dass es nicht nach einem Fehler
         * aussieht, wenn niemand hinschaut.
         */
        const val BOND_WAIT_MILLIS = 12_000L

        /**
         * Wie lange auf die ausgehandelte Paketgroesse gewartet wird.
         *
         * Kommt die Antwort, dauert es Millisekunden. Kommt sie nicht, soll
         * niemand vor einem Bildschirm sitzen, auf dem sich nichts regt.
         */
        const val MTU_WAIT_MILLIS = 3000L

        /**
         * Wie lange nach dem Verbinden gewartet wird, bevor gesucht wird.
         *
         * Nur bei einem bereits gekoppelten Geraet: so lange braucht der
         * Stack, um die Strecke zu verschluesseln. Wer frueher fragt, bekommt
         * keine Antwort - und merkt es nicht einmal.
         */
        const val ENCRYPTION_SETTLE_MILLIS = 900L

        /**
         * Wie lange auf das Ergebnis der Dienstsuche gewartet wird.
         *
         * Gelingt sie, dauert sie ein bis zwei Sekunden; kommt sie aus dem
         * Zwischenspeicher, Millisekunden. Wer danach noch nichts gehoert hat,
         * wird auch nichts mehr hoeren.
         */
        const val DISCOVERY_WAIT_MILLIS = 6000L

        /** Reicht fuer jede Nachricht aus [TrainingProtocol]. */
        const val MTU = 128
    }
}

/**
 * Haelt den GATT-Server ueber die einzelne Sitzung hinaus.
 *
 * Ein GATT-Dienst ist nach aussen nicht die UUID, sondern eine Nummer: der
 * Stack vergibt beim Anlegen fortlaufende Attributnummern, und die Gegenseite
 * merkt sich, unter welcher Nummer unsere Characteristic zu erreichen war.
 * Diesen Zwischenspeicher raeumt Android nur beim Koppeln - `refresh()` half
 * nachweislich nicht, die zweite Suche kam nach elf Millisekunden unveraendert
 * zurueck.
 *
 * Wurde der Server also beim Verlassen des Bildschirms geschlossen und beim
 * naechsten Mal neu geoeffnet, konnten die Nummern andere sein. Das Ergebnis
 * war die Fehlerbeschreibung, die sich durch die ganze Fehlersuche zog: die
 * Verbindung stand, `writeCharacteristic` meldete Erfolg - und beim anderen
 * kam nie etwas an. Nur nach einer frischen Kopplung ging es, weil die den
 * Zwischenspeicher neu aufbaut.
 *
 * Darum wird er einmal geoeffnet und bleibt offen. Die Verbindungen werden
 * beim Beenden sehr wohl gekappt; stehen bleibt nur der Dienst, und der
 * kostet nichts, solange niemand verbunden ist. Der Rueckruf reicht an die
 * gerade offene [PartnerLink] weiter - oder an niemanden, wenn keine offen
 * ist.
 */
private object GattServerHost {

    /** Der eine Server, einmal geoeffnet. */
    var server: BluetoothGattServer? = null

    /** Seine Characteristic - ebenso einmalig wie ihre Nummer. */
    var inbox: BluetoothGattCharacteristic? = null

    /** Die Sitzung, die gerade zuhoert. Ausserhalb eines Trainings: keine. */
    var delegate: BluetoothGattServerCallback? = null

    val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            delegate?.onConnectionStateChange(device, status, newState)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            delegate?.onDescriptorWriteRequest(
                device, requestId, descriptor, preparedWrite, responseNeeded, offset, value
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            delegate?.onCharacteristicWriteRequest(
                device, requestId, characteristic, preparedWrite, responseNeeded, offset, value
            )
        }
    }
}
