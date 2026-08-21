package com.example.mobilese

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingProtocolTest {

    // --- Sportart ---

    @Test
    fun `carries the sport there and back`() {
        val message = TrainingProtocol.sportMessage(Sports.GYM)

        assertEquals(TrainingProtocol.TYPE_SPORT, TrainingProtocol.typeOf(message))
        assertEquals(Sports.GYM, TrainingProtocol.sportFrom(message))
    }

    /** Eine selbst eingetragene Sportart darf Umlaute haben. */
    @Test
    fun `survives non-ascii in the sport name`() {
        val message = TrainingProtocol.sportMessage("Kraftraum Übung")

        assertEquals("Kraftraum Übung", TrainingProtocol.sportFrom(message))
    }

    /** Lange Namen werden gekuerzt, statt die Nachricht zu sprengen. */
    @Test
    fun `shortens an overlong sport name`() {
        val long = "A".repeat(200)
        val back = TrainingProtocol.sportFrom(TrainingProtocol.sportMessage(long))

        assertTrue(back!!.length < long.length)
        assertTrue(long.startsWith(back))
    }

    // --- Start und Ende ---

    @Test
    fun `start carries nothing but its type`() {
        assertEquals(TrainingProtocol.TYPE_START, TrainingProtocol.typeOf(TrainingProtocol.startMessage()))
    }

    @Test
    fun `carries the duration there and back`() {
        assertEquals(3661, TrainingProtocol.secondsFrom(TrainingProtocol.stopMessage(3661)))
    }

    @Test
    fun `carries a duration of zero`() {
        assertEquals(0, TrainingProtocol.secondsFrom(TrainingProtocol.stopMessage(0)))
    }

    // --- Die Teilnehmerliste ---

    /**
     * Wer verbindet, kennt alle; die anderen kennen nur ihn. Damit auch sie
     * die ganze Gruppe eintragen koennen, schickt er die Liste herum.
     */
    @Test
    fun `carries the whole group there and back`() {
        val payloads = listOfNotNull(
            CoLocation.payloadFor("11111111-1111-1111-1111-111111111111"),
            CoLocation.payloadFor("22222222-2222-2222-2222-222222222222"),
            CoLocation.payloadFor("33333333-3333-3333-3333-333333333333")
        )
        val message = TrainingProtocol.rosterMessage(payloads)

        assertEquals(TrainingProtocol.TYPE_ROSTER, TrainingProtocol.typeOf(message))

        val back = TrainingProtocol.rosterFrom(message)
        assertEquals(3, back.size)
        payloads.forEachIndexed { index, payload ->
            assertArrayEquals(payload, back[index])
        }
    }

    @Test
    fun `an empty group stays empty`() {
        assertTrue(TrainingProtocol.rosterFrom(TrainingProtocol.rosterMessage(emptyList())).isEmpty())
    }

    /**
     * Ein angebrochener Eintrag am Ende - etwa weil die Nachricht nicht ganz
     * durchkam - darf keine erfundene Kennung ergeben.
     */
    @Test
    fun `drops a truncated entry at the end`() {
        val payloads = listOfNotNull(
            CoLocation.payloadFor("11111111-1111-1111-1111-111111111111"),
            CoLocation.payloadFor("22222222-2222-2222-2222-222222222222")
        )
        val cut = TrainingProtocol.rosterMessage(payloads).copyOf(1 + CoLocation.PAYLOAD_BYTES + 3)

        assertEquals(1, TrainingProtocol.rosterFrom(cut).size)
    }

    @Test
    fun `reads no group out of another message`() {
        assertTrue(TrainingProtocol.rosterFrom(TrainingProtocol.startMessage()).isEmpty())
    }

    // --- Unbrauchbare Nachrichten ---

    @Test
    fun `reads nothing out of an empty or wrong message`() {
        assertEquals(0.toByte(), TrainingProtocol.typeOf(null))
        assertEquals(0.toByte(), TrainingProtocol.typeOf(ByteArray(0)))
        assertNull(TrainingProtocol.sportFrom(TrainingProtocol.startMessage()))
        assertNull(TrainingProtocol.secondsFrom(TrainingProtocol.sportMessage("Gym")))
    }

    /** Eine abgeschnittene Nachricht darf nichts Falsches ergeben. */
    @Test
    fun `refuses a truncated message`() {
        assertNull(TrainingProtocol.secondsFrom(TrainingProtocol.stopMessage(60).copyOf(3)))
    }
}
