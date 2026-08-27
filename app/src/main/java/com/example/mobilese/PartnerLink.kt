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

    /** Alle offenen Verbindungen, nach Geraeteadresse. */
    private val clients = mutableMapOf<String, BluetoothGatt>()

    /** Noch abzuarbeitende Verbindungswuensche. */
    private val queue = ArrayDeque<Pair<BluetoothDevice, UserProfile>>()

    /** Wer sich mit uns verbunden hat - fuer die Serverseite. */
    private val peers = mutableSetOf<String>()

    /** Wen wir schon gemeldet haben - Rueckrufe koennen sich wiederholen. */
    private val confirmed = mutableSetOf<String>()

    /** Worauf gerade gewartet wird, waehrend das System koppelt. */
    private var pending: Pair<BluetoothDevice, UserProfile>? = null

    /** Ob [adapterReceiver] angemeldet ist - zweimal waere ein Fehler. */
    private var watchingAdapter = false

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
            clients.values.forEach { closeQuietly(it) }
            clients.clear()
            peers.clear()
            confirmed.clear()
            connecting = null

            if (had) onLost()
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val waiting = pending ?: return
            val device = intentDevice(intent) ?: return
            if (device.address != waiting.first.address) return

            when (intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                BluetoothDevice.BOND_BONDED -> {
                    pending = null
                    openConnection(waiting.first, waiting.second)
                }
                BluetoothDevice.BOND_NONE -> {
                    // Abgelehnt, weggetippt oder fehlgeschlagen. Kein
                    // stiller Abbruch - sonst dreht sich der Kreis weiter.
                    pending = null
                    onProblem(R.string.partner_pair_failed)
                    next()
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
                        clients.values.forEach { gatt -> gatt.disconnect() }
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
                    try {
                        // Die Voreinstellung laesst nur 20 Byte je Nachricht
                        // zu - ein Standort allein braucht schon 17.
                        gatt.requestMtu(MTU)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Could not ask for a bigger packet: ${e.message}")
                        onProblem(R.string.partner_permission_missing)
                    }
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

        /** Die Anmeldung steht - ab jetzt gilt die Kopplung. */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            settle(gatt)
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

        /** Die Paketgroesse steht - jetzt duerfen die Dienste gesucht werden. */
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            try {
                gatt.discoverServices()
            } catch (e: SecurityException) {
                Log.e(TAG, "Could not look for services: ${e.message}")
                onProblem(R.string.partner_permission_missing)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt
                .getService(CoLocation.SERVICE_UUID)
                ?.getCharacteristic(CoLocation.CHARACTERISTIC_UUID)

            val payload = CoLocation.payloadFor(ownUserId)
            if (characteristic == null || payload == null) {
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
            if (subscribe(gatt, characteristic)) return

            // Der Deskriptor liess sich nicht schreiben. Dann eben einseitig:
            // eine halbe Verbindung ist besser als gar keine.
            settle(gatt)
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

        try {
            val opened = manager?.openGattServer(context, serverCallback)
            if (opened == null) {
                onProblem(R.string.partner_link_failed)
                return
            }
            server = opened

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

    private fun beginWith(device: BluetoothDevice, member: UserProfile) {
        try {
            // Schon gekoppelt - etwa vom letzten gemeinsamen Training. Dann
            // entfaellt der Dialog und es geht direkt weiter.
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                openConnection(device, member)
                return
            }

            pending = device to member
            context.registerReceiver(
                bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            )
            onProblem(R.string.partner_pairing)

            if (!device.createBond()) {
                pending = null
                onProblem(R.string.partner_pair_failed)
                next()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not pair: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
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
    private fun settle(gatt: BluetoothGatt) {
        connecting?.let { (device, member) ->
            clients[device.address] = gatt
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
        closeQuietly(gatt)
        onProblem(R.string.partner_link_failed)
        connecting = null
        retried = false
        next()
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
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
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
        // Als Anrufender: schreiben.
        clients.values.forEach { gatt ->
            val characteristic = gatt
                .getService(CoLocation.SERVICE_UUID)
                ?.getCharacteristic(CoLocation.CHARACTERISTIC_UUID)
            if (characteristic != null) write(gatt, characteristic, message)
        }

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
                    it.disconnect()
                    it.close()
                }
                clients.clear()
                queue.clear()
                connecting = null
            }
            if (server) {
                this.server?.close()
                this.server = null
                this.inbox = null
                listeners.clear()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not close cleanly: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PartnerLink"

        /** Reicht fuer jede Nachricht aus [TrainingProtocol]. */
        const val MTU = 128
    }
}
