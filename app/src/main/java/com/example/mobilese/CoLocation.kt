package com.example.mobilese

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Wie zwei Telefone erkennen, dass sie zusammen trainieren.
 *
 * Beide Geraete senden ueber Bluetooth Low Energy ein kleines Paket aus und
 * hoeren zugleich auf die Pakete der anderen. Steht im Paket die Kennung
 * eines Crew-Mitglieds, sind die beiden beieinander - und das Workout zaehlt
 * doppelt.
 *
 * Hier steht nur die Rechnung: was in das Paket kommt und wie man es wieder
 * einer Person zuordnet. Das Senden und Empfangen macht [PartnerBeacon]. Die
 * Trennung ist Absicht - so laesst sich der heikle Teil, das Zuordnen, ohne
 * zwei Telefone pruefen.
 *
 * ## Warum nur acht Bytes
 *
 * Ein BLE-Werbepaket fasst **31 Byte**, und davon geht einiges fuer die
 * Kopfdaten drauf. Eine Nutzerkennung ist eine UUID mit 16 Byte; zusammen mit
 * der Dienstkennung und den Flags passt sie nicht mehr hinein. Deshalb gehen
 * nur die ersten acht Byte auf die Reise.
 *
 * Das genuegt, weil nicht gegen alle Menschen verglichen wird, sondern gegen
 * die eigene Crew - ein paar Dutzend Personen. Dass zwei davon in den ersten
 * acht Byte uebereinstimmen, ist bei zufaellig vergebenen UUIDs so
 * unwahrscheinlich, dass es vor der Abgabe niemand erlebt.
 */
object CoLocation {

    /**
     * Die Dienstkennung, nach der gesucht wird.
     *
     * Bewusst eine **16-Bit-Kennung** in der Bluetooth-Grundform: Android
     * schreibt sie dann mit zwei Byte ins Paket statt mit sechzehn. Eine
     * eigene 128-Bit-UUID waere sauberer, liesse aber keinen Platz mehr fuer
     * die Nutzerkennung.
     */
    val SERVICE_UUID: UUID = UUID.fromString("0000c7f1-0000-1000-8000-00805f9b34fb")

    /** So viele Byte der Nutzerkennung reisen mit. */
    const val PAYLOAD_BYTES = 8

    /**
     * Das Paket fuer diese Person, oder null wenn die Kennung keine UUID ist.
     *
     * Null statt einer Ausnahme: die Kennung kommt aus der Datenbank, und ein
     * unlesbarer Wert dort darf nicht den Bildschirm mitreissen - dann gibt es
     * eben keine Kopplung.
     */
    fun payloadFor(userId: String): ByteArray? {
        val uuid = uuidOrNull(userId) ?: return null
        return ByteBuffer.allocate(PAYLOAD_BYTES).putLong(uuid.mostSignificantBits).array()
    }

    /** Ob dieses Paket zu dieser Person gehoert. */
    fun matches(payload: ByteArray?, userId: String): Boolean {
        if (payload == null || payload.size != PAYLOAD_BYTES) return false
        return payload.contentEquals(payloadFor(userId))
    }

    /**
     * Das Crew-Mitglied, das dieses Paket gesendet hat.
     *
     * Nur Mitglieder der eigenen Crew: ein fremdes CrewFit in Reichweite - im
     * Fitnessstudio durchaus denkbar - soll keine doppelten Punkte
     * verschaffen. Die eigene Person faellt heraus, denn das eigene Paket
     * empfaengt man auf manchen Geraeten selbst.
     */
    fun memberFor(
        payload: ByteArray?,
        members: List<UserProfile>,
        ownUserId: String?
    ): UserProfile? {
        if (payload == null || payload.size != PAYLOAD_BYTES) return null
        return members.firstOrNull { it.id != ownUserId && matches(payload, it.id) }
    }

    private fun uuidOrNull(value: String): UUID? = try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}
