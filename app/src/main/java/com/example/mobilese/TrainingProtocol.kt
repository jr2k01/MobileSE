package com.example.mobilese

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Was sich zwei gekoppelte Telefone waehrend eines gemeinsamen Trainings
 * sagen.
 *
 * Ein Byte fuer die Art der Nachricht, dahinter der Inhalt. Absichtlich klein
 * und ohne Textformat: die Nachrichten gehen ueber eine Bluetooth-Verbindung,
 * und dort ist jedes Byte eines, auf das gewartet wird.
 *
 * Reine Umrechnung, ohne Android und ohne Funk - und damit ohne zwei Telefone
 * pruefbar. Das Senden macht [PartnerLink].
 *
 * ## Wer sagt was
 *
 * Der Startende gibt vor: Er waehlt die Sportart, er startet, er beendet.
 * Damit kann die Sportart gar nicht auseinandergehen - sie wird nicht
 * verglichen, sondern nur von einer Seite gesetzt. Dasselbe gilt fuer die
 * Dauer: es zaehlt die Uhr des Startenden, nicht zwei getrennt laufende.
 *
 * Danach ist die Verbindung erledigt. Foto, Sprachnotiz und Ort traegt jeder
 * fuer sich ein - auch spaeter und an einem anderen Ort.
 */
object TrainingProtocol {

    /** Der Startende teilt die gewaehlte Sportart mit. */
    const val TYPE_SPORT: Byte = 1

    /** Das Training beginnt - ab jetzt laeuft auf beiden Seiten die Uhr. */
    const val TYPE_START: Byte = 2

    /** Das Training endet. Im Inhalt die Dauer in Sekunden. */
    const val TYPE_STOP: Byte = 3

    /**
     * Wer alles mittrainiert - acht Byte je Person, hintereinander.
     *
     * Nur der Fuehrende kennt die ganze Runde: Er hat sich mit jedem einzeln
     * verbunden, die anderen nur mit ihm. Damit am Ende trotzdem bei allen
     * dieselben Namen unter dem Workout stehen, schickt er die Liste herum.
     */
    const val TYPE_ROSTER: Byte = 4

    /** Sportart, gekuerzt auf das, was sicher durch eine Nachricht passt. */
    fun sportMessage(sport: String): ByteArray {
        val name = sport.take(MAX_SPORT_CHARS).toByteArray(StandardCharsets.UTF_8)
        return ByteArray(1 + name.size).also {
            it[0] = TYPE_SPORT
            name.copyInto(it, 1)
        }
    }

    fun startMessage(): ByteArray = byteArrayOf(TYPE_START)

    fun stopMessage(seconds: Int): ByteArray =
        ByteBuffer.allocate(5).put(TYPE_STOP).putInt(seconds).array()

    /**
     * Die Runde als eine Nachricht.
     *
     * Sechs Personen sind 48 Byte plus das Typbyte - das passt in ein Paket,
     * sobald die Verbindung die groessere Paketgroesse ausgehandelt hat.
     */
    fun rosterMessage(payloads: List<ByteArray>): ByteArray {
        val usable = payloads.filter { it.size == CoLocation.PAYLOAD_BYTES }
        val message = ByteArray(1 + usable.size * CoLocation.PAYLOAD_BYTES)
        message[0] = TYPE_ROSTER
        usable.forEachIndexed { index, payload ->
            payload.copyInto(message, 1 + index * CoLocation.PAYLOAD_BYTES)
        }
        return message
    }

    /**
     * Die einzelnen Kennungen aus einer [TYPE_ROSTER]-Nachricht.
     *
     * Ein angebrochener Rest am Ende faellt weg: lieber eine Person weniger
     * als eine falsche, die aus halben Bytes entstanden ist.
     */
    fun rosterFrom(message: ByteArray?): List<ByteArray> {
        if (typeOf(message) != TYPE_ROSTER) return emptyList()
        val body = message!!.size - 1
        return (0 until body / CoLocation.PAYLOAD_BYTES).map { index ->
            val from = 1 + index * CoLocation.PAYLOAD_BYTES
            message.copyOfRange(from, from + CoLocation.PAYLOAD_BYTES)
        }
    }

    /** Die Art einer empfangenen Nachricht, oder 0 wenn sie unbrauchbar ist. */
    fun typeOf(message: ByteArray?): Byte =
        if (message == null || message.isEmpty()) 0 else message[0]

    /** Die Sportart aus einer [TYPE_SPORT]-Nachricht, oder null. */
    fun sportFrom(message: ByteArray?): String? {
        if (typeOf(message) != TYPE_SPORT || message!!.size < 2) return null
        return String(message, 1, message.size - 1, StandardCharsets.UTF_8)
    }

    /** Die Dauer aus einer [TYPE_STOP]-Nachricht, oder null. */
    fun secondsFrom(message: ByteArray?): Int? {
        if (typeOf(message) != TYPE_STOP || message!!.size < 5) return null
        return ByteBuffer.wrap(message, 1, 4).int
    }

    /** Passt mit Typbyte in eine Nachricht, auch bei kleiner Paketgroesse. */
    private const val MAX_SPORT_CHARS = 20

}
