package com.example.mobilese

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-Tests fuer die Wahl des Erscheinungsbilds.
 *
 * Wichtig ist hier vor allem, was bei einem unbrauchbaren gespeicherten Wert
 * passiert: die App liest ihn beim Start, noch bevor irgendein Bildschirm
 * steht. Eine Ausnahme an dieser Stelle waere kein falsches Aussehen, sondern
 * eine App, die sich nicht mehr oeffnen laesst.
 */
class ThemeModeTest {

    @Test
    fun `a stored mode is read back`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStored(mode.storedName))
        }
    }

    @Test
    fun `without a stored value the device setting wins`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStored(null))
    }

    /** Etwa nach einem Update, das einen Namen nicht mehr kennt. */
    @Test
    fun `an unknown value falls back instead of failing`() {
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStored("sepia"))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStored("LIGHT"))
    }

    /** Die gespeicherten Namen sind Teil der Ablage und duerfen nicht wandern. */
    @Test
    fun `the stored names are stable and distinct`() {
        assertEquals("system", ThemeMode.SYSTEM.storedName)
        assertEquals("light", ThemeMode.LIGHT.storedName)
        assertEquals("dark", ThemeMode.DARK.storedName)

        val names = ThemeMode.entries.map { it.storedName }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `each mode maps to the matching AppCompat value`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.delegateValue)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.delegateValue)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.delegateValue)
    }
}
