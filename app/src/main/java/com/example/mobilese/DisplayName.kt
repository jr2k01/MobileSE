package com.example.mobilese

/**
 * Der Name, unter dem ein Mitglied in der Crew erscheint.
 *
 * In Rangliste, Top drei, Crew-Uebersicht und Verlauf steht neben Punkten,
 * Bildern und Zeiten wenig Platz zur Verfuegung. Ein voller Name wie
 * "Maximilian Mustermann" draengt dort alles andere weg oder wird abgeschnitten.
 * Deshalb legt jeder im Profil selbst ein Kuerzel fest, das an all diesen
 * Stellen benutzt wird.
 *
 * Wer keines hinterlegt hat - alle bestehenden Konten - soll deswegen nicht
 * schlechter dastehen. Dann wird der volle Name selbst gekuerzt, und erst wenn
 * auch der fehlt, bleibt der vordere Teil der Mailadresse.
 *
 * Bewusst reine Logik ohne Android-Bezug: so ist die Regel an einer Stelle
 * festgelegt und laesst sich ohne Emulator testen.
 */
object DisplayName {

    /** Kurz genug fuer eine Ranglistenzeile, lang genug fuer "Jannik R.". */
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 12

    /**
     * Ein Kuerzel muss in die Zeile passen und wenigstens ein Zeichen haben,
     * das man lesen kann. Sonst absichtlich keine Einschraenkung: Ziffern,
     * Punkte und Emojis darf jeder nehmen.
     */
    fun isValid(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length in MIN_LENGTH..MAX_LENGTH &&
                trimmed.any { !it.isWhitespace() } &&
                trimmed.none { it == '\n' || it == '\r' }
    }

    /** Das Kuerzel eines Profils, leer wenn sich keines bilden laesst. */
    fun of(profile: UserProfile): String =
        resolve(profile.displayName, profile.name, profile.email)

    /**
     * Die Reihenfolge: selbst gewaehltes Kuerzel, sonst der gekuerzte volle
     * Name, sonst der Teil der Mailadresse vor dem At-Zeichen.
     */
    fun resolve(displayName: String?, fullName: String?, email: String?): String {
        displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        shorten(fullName)?.let { return it }
        return email.orEmpty().substringBefore('@').trim()
    }

    /**
     * Kuerzt "Jannik Rikazewski" zu "Jannik R.".
     *
     * Ein einzelner Name bleibt, wie er ist - daraus laesst sich nichts
     * kuerzen, ohne ihn zu verstuemmeln. Gibt null zurueck, wenn nichts
     * Brauchbares uebrig bleibt.
     */
    fun shorten(fullName: String?): String? {
        val parts = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> null
            parts.size == 1 -> parts.first()
            else -> {
                val initial = parts.last().first()
                // Ein bereits abgekuerzter Nachname wie "R." soll nicht "R.." werden.
                if (initial.isLetterOrDigit()) "${parts.first()} $initial."
                else "${parts.first()} ${parts.last()}"
            }
        }
    }
}
