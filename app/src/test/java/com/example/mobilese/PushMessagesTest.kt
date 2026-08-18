package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-Tests fuer die Zuordnung einer Push-Nachricht zu dem, was auf dem
 * Bildschirm steht.
 *
 * Die Nutzdaten kommen vom Server, also von aussen. Was hier zaehlt, ist
 * deshalb weniger der Normalfall als das, was bei fehlenden oder unbekannten
 * Angaben passiert: eine Benachrichtigung darf duerftig ausfallen, aber der
 * Empfang darf nicht abbrechen.
 */
class PushMessagesTest {

    /** Setzt statt der Ressourcen erkennbare Platzhalter ein. */
    private val strings = object : PushMessages.Strings {
        override fun unknownMember() = "Unknown"
        override fun activityTitle() = "New workout"
        override fun activityText(name: String, sport: String, duration: String) =
            "$name|$sport|$duration"

        override fun rankingTitle() = "Ranking"
        override fun overtakeText(name: String, rank: Int) = "$name overtook, now $rank"
        override fun leadText(name: String) = "$name leads"
    }

    @Test
    fun `an activity message becomes a workout notification`() {
        val content = PushMessages.from(
            mapOf(
                "type" to PushMessages.TYPE_ACTIVITY,
                "name" to "Jannik R.",
                "sport" to "Padel",
                "duration" to "60"
            ),
            strings
        )

        assertEquals(Notifications.CHANNEL_ACTIVITIES, content?.channelId)
        assertEquals("New workout", content?.title)
        assertEquals("Jannik R.|Padel|60", content?.text)
    }

    @Test
    fun `an overtake message goes to the ranking channel`() {
        val content = PushMessages.from(
            mapOf("type" to PushMessages.TYPE_OVERTAKE, "name" to "GIG", "rank" to "2"),
            strings
        )

        assertEquals(Notifications.CHANNEL_RANKING, content?.channelId)
        assertEquals("GIG overtook, now 2", content?.text)
    }

    @Test
    fun `taking the lead goes to the ranking channel too`() {
        val content = PushMessages.from(
            mapOf("type" to PushMessages.TYPE_LEAD, "name" to "GIG"),
            strings
        )

        assertEquals(Notifications.CHANNEL_RANKING, content?.channelId)
        assertEquals("GIG leads", content?.text)
    }

    /**
     * Beide Ranglisten-Meldungen teilen sich eine Kennung, die Workouts eine
     * eigene: es soll die letzte Meldung ersetzt werden und kein Stapel aus
     * zwanzig Workouts entstehen.
     */
    @Test
    fun `messages of the same kind replace each other`() {
        val first = PushMessages.from(mapOf("type" to PushMessages.TYPE_ACTIVITY), strings)
        val second = PushMessages.from(mapOf("type" to PushMessages.TYPE_ACTIVITY), strings)
        val ranking = PushMessages.from(mapOf("type" to PushMessages.TYPE_LEAD), strings)

        assertEquals(first?.id, second?.id)
        assert(first?.id != ranking?.id)
    }

    // === Unvollstaendige Daten ===

    @Test
    fun `a missing name becomes the placeholder`() {
        val content = PushMessages.from(mapOf("type" to PushMessages.TYPE_LEAD), strings)
        assertEquals("Unknown leads", content?.text)

        val blank = PushMessages.from(
            mapOf("type" to PushMessages.TYPE_LEAD, "name" to "   "),
            strings
        )
        assertEquals("Unknown leads", blank?.text)
    }

    @Test
    fun `a rank that is not a number becomes zero instead of throwing`() {
        val content = PushMessages.from(
            mapOf("type" to PushMessages.TYPE_OVERTAKE, "name" to "GIG", "rank" to "zwei"),
            strings
        )
        assertEquals("GIG overtook, now 0", content?.text)
    }

    /** Etwa wenn der Server eine Art schickt, die diese Version noch nicht kennt. */
    @Test
    fun `an unknown or missing type shows nothing at all`() {
        assertNull(PushMessages.from(mapOf("type" to "birthday"), strings))
        assertNull(PushMessages.from(emptyMap(), strings))
    }
}
