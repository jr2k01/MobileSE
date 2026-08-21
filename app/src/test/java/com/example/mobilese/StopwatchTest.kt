package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {

    private val watch = Stopwatch()

    private fun seconds(n: Long) = n * 1000L

    @Test
    fun `counts nothing before it starts`() {
        assertEquals(0, watch.elapsedSeconds(seconds(100)))
        assertFalse(watch.isRunning)
        assertFalse(watch.hasStarted)
    }

    @Test
    fun `counts while it runs`() {
        watch.start(seconds(10))

        assertTrue(watch.isRunning)
        assertEquals(30, watch.elapsedSeconds(seconds(40)))
    }

    /**
     * Der Kern der Anforderung: reisst die Verbindung ab, zaehlt die Pause
     * nicht mit. Sonst waere ein Training laenger als es war.
     */
    @Test
    fun `the pause does not count`() {
        watch.start(seconds(0))
        watch.pause(seconds(60))          // eine Minute gelaufen
        // fuenf Minuten getrennt
        watch.start(seconds(360))
        watch.pause(seconds(420))         // noch eine Minute

        assertEquals(120, watch.elapsedSeconds(seconds(420)))
        assertEquals(2, watch.elapsedMinutes(seconds(420)))
    }

    @Test
    fun `keeps counting after it resumes`() {
        watch.start(seconds(0))
        watch.pause(seconds(30))
        watch.start(seconds(100))

        assertTrue(watch.isRunning)
        assertEquals(50, watch.elapsedSeconds(seconds(120)))
    }

    @Test
    fun `standing still does not add anything`() {
        watch.start(seconds(0))
        watch.pause(seconds(60))

        assertEquals(60, watch.elapsedSeconds(seconds(600)))
    }

    /** Zweimal starten darf die Uhr nicht zurueckwerfen. */
    @Test
    fun `starting twice changes nothing`() {
        watch.start(seconds(0))
        watch.start(seconds(30))

        assertEquals(60, watch.elapsedSeconds(seconds(60)))
    }

    @Test
    fun `pausing twice changes nothing`() {
        watch.start(seconds(0))
        watch.pause(seconds(60))
        watch.pause(seconds(600))

        assertEquals(60, watch.elapsedSeconds(seconds(600)))
    }

    /** Eine Zeit, die rueckwaerts laeuft, darf nichts abziehen. */
    @Test
    fun `ignores a clock that jumps backwards`() {
        watch.start(seconds(100))

        assertEquals(0, watch.elapsedSeconds(seconds(50)))
    }

    @Test
    fun `rounds minutes down`() {
        watch.start(0)
        watch.pause(seconds(119))

        assertEquals(1, watch.elapsedMinutes(seconds(119)))
    }

    @Test
    fun `knows that it has run at all`() {
        watch.start(seconds(0))
        watch.pause(seconds(5))

        assertFalse(watch.isRunning)
        assertTrue(watch.hasStarted)
    }

    @Test
    fun `starts over after a reset`() {
        watch.start(seconds(0))
        watch.pause(seconds(600))
        watch.reset()

        assertEquals(0, watch.elapsedSeconds(seconds(600)))
        assertFalse(watch.hasStarted)
    }
}
