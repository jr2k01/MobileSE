package com.example.mobilese

/**
 * Zerlegt einen Namen in Vor- und Nachname und setzt ihn wieder zusammen.
 *
 * In der Datenbank steht weiterhin ein einziges Feld. Das Profil fragt seit
 * dieser Aenderung getrennt nach Vor- und Nachname, aber daraus zwei Spalten zu
 * machen haette eine Migration und eine Umstellung aller Stellen bedeutet, die
 * den Namen lesen - fuer eine reine Frage der Eingabe zu viel.
 *
 * Der letzte Wortteil gilt als Nachname, alles davor als Vorname. Damit werden
 * "Ana Maria Silva" zu "Ana Maria" und "Silva" - dieselbe Annahme, nach der
 * [DisplayName] den Anfangsbuchstaben fuer das Kuerzel bildet.
 *
 * Reine Logik ohne Android-Bezug, also ohne Emulator testbar.
 */
object PersonName {

    /** Alles ausser dem letzten Wortteil. Bei einem einzelnen Wort dieses selbst. */
    fun firstOf(fullName: String?): String {
        val parts = partsOf(fullName)
        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts.first()
            else -> parts.dropLast(1).joinToString(" ")
        }
    }

    /** Der letzte Wortteil. Leer, wenn nur ein Wort vorliegt. */
    fun lastOf(fullName: String?): String {
        val parts = partsOf(fullName)
        return if (parts.size < 2) "" else parts.last()
    }

    /**
     * Setzt beide Felder zusammen.
     *
     * Ein leeres Feld faellt weg, statt ein doppeltes Leerzeichen oder einen
     * Namen mit Leerzeichen am Ende zu hinterlassen - der stuende sonst so in
     * der Datenbank und wuerde in der Crew mit angezeigt.
     */
    fun join(first: String, last: String): String =
        listOf(first.trim(), last.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun partsOf(fullName: String?): List<String> =
        fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
