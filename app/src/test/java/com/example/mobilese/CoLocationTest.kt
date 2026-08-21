package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CoLocationTest {

    private fun member(id: String, name: String = "Someone") =
        UserProfile(id = id, name = name)

    private val anna = UUID.randomUUID().toString()
    private val ben = UUID.randomUUID().toString()

    @Test
    fun `packs eight bytes`() {
        assertEquals(CoLocation.PAYLOAD_BYTES, CoLocation.payloadFor(anna)?.size)
    }

    @Test
    fun `the same person always packs the same bytes`() {
        assertTrue(CoLocation.payloadFor(anna).contentEquals(CoLocation.payloadFor(anna)))
    }

    @Test
    fun `different people pack different bytes`() {
        assertFalse(CoLocation.payloadFor(anna).contentEquals(CoLocation.payloadFor(ben)))
    }

    /** Eine Kennung, die keine UUID ist, darf nichts mitreissen. */
    @Test
    fun `refuses an id that is not a uuid`() {
        assertNull(CoLocation.payloadFor("not-a-uuid"))
        assertNull(CoLocation.payloadFor(""))
    }

    @Test
    fun `recognises its own packet`() {
        assertTrue(CoLocation.matches(CoLocation.payloadFor(anna), anna))
    }

    @Test
    fun `does not recognise someone else's packet`() {
        assertFalse(CoLocation.matches(CoLocation.payloadFor(ben), anna))
    }

    /** Ein verstuemmeltes Paket aus der Luft darf nicht zufaellig passen. */
    @Test
    fun `refuses a packet of the wrong length`() {
        assertFalse(CoLocation.matches(ByteArray(4), anna))
        assertFalse(CoLocation.matches(ByteArray(16), anna))
        assertFalse(CoLocation.matches(null, anna))
    }

    @Test
    fun `finds the crew member behind a packet`() {
        val members = listOf(member(anna, "Anna"), member(ben, "Ben"))
        val found = CoLocation.memberFor(CoLocation.payloadFor(ben), members, ownUserId = anna)

        assertNotNull(found)
        assertEquals(ben, found?.id)
    }

    /**
     * Ein fremdes CrewFit in Reichweite - im Fitnessstudio durchaus denkbar -
     * darf keine doppelten Punkte verschaffen.
     */
    @Test
    fun `ignores someone outside the crew`() {
        val stranger = UUID.randomUUID().toString()
        val members = listOf(member(anna, "Anna"), member(ben, "Ben"))

        assertNull(CoLocation.memberFor(CoLocation.payloadFor(stranger), members, ownUserId = anna))
    }

    /** Manche Geraete empfangen das eigene Paket - das ist kein Partner. */
    @Test
    fun `never pairs with oneself`() {
        val members = listOf(member(anna, "Anna"), member(ben, "Ben"))

        assertNull(CoLocation.memberFor(CoLocation.payloadFor(anna), members, ownUserId = anna))
    }

    @Test
    fun `finds nobody in an empty crew`() {
        assertNull(CoLocation.memberFor(CoLocation.payloadFor(ben), emptyList(), ownUserId = anna))
    }

    /**
     * Die Dienstkennung muss in der Bluetooth-Grundform bleiben, sonst
     * schreibt Android sie mit sechzehn statt zwei Byte ins Paket - und dann
     * passt die Nutzerkennung nicht mehr dazu.
     */
    @Test
    fun `keeps the service uuid in the short bluetooth form`() {
        val text = CoLocation.SERVICE_UUID.toString()

        assertTrue(text.startsWith("0000"))
        assertTrue(text.endsWith("-0000-1000-8000-00805f9b34fb"))
    }
}
