package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsTest {

    private fun reaction(userId: String, emoji: String) =
        ActivityReaction(activityId = "a1", userId = userId, emoji = emoji)

    @Test
    fun `counts each emoji separately`() {
        val counts = Reactions.countsOf(
            listOf(
                reaction("u1", Reactions.STRONG),
                reaction("u2", Reactions.STRONG),
                reaction("u3", Reactions.FIRE)
            )
        )

        assertEquals(2, counts[Reactions.STRONG])
        assertEquals(1, counts[Reactions.FIRE])
    }

    /** Zeichen ohne Reaktion tauchen gar nicht auf, nicht als Null. */
    @Test
    fun `leaves out emoji nobody chose`() {
        val counts = Reactions.countsOf(listOf(reaction("u1", Reactions.CLAP)))

        assertEquals(1, counts.size)
        assertNull(counts[Reactions.PARTY])
    }

    /**
     * Ein Zeichen, das die App nicht kennt, faellt heraus. Sonst brechen
     * Zeilen, die auf die feste Anzahl Spalten ausgelegt sind.
     */
    @Test
    fun `ignores emoji outside the fixed set`() {
        val counts = Reactions.countsOf(
            listOf(
                reaction("u1", "🦄"),
                reaction("u2", Reactions.FIRE)
            )
        )

        assertEquals(1, counts.size)
        assertEquals(1, counts[Reactions.FIRE])
    }

    @Test
    fun `empty list counts nothing`() {
        assertTrue(Reactions.countsOf(emptyList()).isEmpty())
    }

    @Test
    fun `finds what this person chose`() {
        val reactions = listOf(
            reaction("u1", Reactions.STRONG),
            reaction("u2", Reactions.FIRE)
        )

        assertEquals(Reactions.FIRE, Reactions.chosenBy(reactions, "u2"))
    }

    @Test
    fun `nothing chosen when this person did not react`() {
        assertNull(Reactions.chosenBy(listOf(reaction("u1", Reactions.STRONG)), "u2"))
    }

    /** Ohne angemeldeten Nutzer darf nichts als "meins" markiert werden. */
    @Test
    fun `nothing chosen without a user`() {
        assertNull(Reactions.chosenBy(listOf(reaction("u1", Reactions.STRONG)), null))
    }

    @Test
    fun `knows its own emoji and nothing else`() {
        assertTrue(Reactions.ALL.all { Reactions.isKnown(it) })
        assertTrue(!Reactions.isKnown("🦄"))
    }

    /** Fuenf Spalten im Layout - die Liste darf nicht unbemerkt wachsen. */
    @Test
    fun `offers five reactions`() {
        assertEquals(5, Reactions.ALL.size)
        assertEquals(Reactions.ALL.size, Reactions.ALL.distinct().size)
    }
}
