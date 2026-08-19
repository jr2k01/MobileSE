package com.example.mobilese

/**
 * Anforderungen an das Passwort bei der Registrierung.
 *
 * Die Regeln werden dem Nutzer waehrend der Eingabe unter dem Feld angezeigt
 * und einzeln abgehakt, statt ihn nach dem Absenden mit einer Sammelmeldung
 * abzuweisen.
 *
 * Wichtig: geprueft wird nur beim **Registrieren**. Bei der Anmeldung darf
 * nichts davon gelten - sonst sperrt man Konten aus, die vor der Einfuehrung
 * dieser Regeln mit einem kuerzeren Passwort angelegt wurden.
 */
object PasswordPolicy {

    /** Supabase verlangt serverseitig sechs Zeichen, wir sind bewusst strenger. */
    const val MIN_LENGTH = 8

    enum class Rule { LENGTH, UPPERCASE, LOWERCASE, DIGIT, SPECIAL }

    /** Prueft eine einzelne Regel. */
    fun isMet(rule: Rule, password: String): Boolean = when (rule) {
        Rule.LENGTH -> password.length >= MIN_LENGTH
        Rule.UPPERCASE -> password.any { it.isUpperCase() }
        Rule.LOWERCASE -> password.any { it.isLowerCase() }
        Rule.DIGIT -> password.any { it.isDigit() }
        // Alles, was weder Buchstabe noch Ziffer noch Leerraum ist.
        Rule.SPECIAL -> password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    }

    fun unmetRules(password: String): Set<Rule> =
        Rule.entries.filterNot { isMet(it, password) }.toSet()

    fun isValid(password: String): Boolean = unmetRules(password).isEmpty()
}

/**
 * Plausibilitaetsgrenzen fuer die uebrigen Eingabefelder.
 *
 * Zweck ist nicht, dem Nutzer etwas zu verbieten, sondern offensichtlichen
 * Unsinn abzufangen, der die Auswertung verzerrt: In den Testdaten stand etwa
 * ein Workout ueber 5000 Minuten - das sind dreieinhalb Tage am Stueck und es
 * bringt fast 1500 Punkte in der Rangliste.
 *
 * Alle Funktionen geben den geparsten Wert zurueck oder null, wenn die Eingabe
 * unbrauchbar ist. So muss der Aufrufer nicht zweimal parsen.
 */
object InputRules {

    const val NAME_MIN_LENGTH = 2
    const val NAME_MAX_LENGTH = 40

    const val CREW_NAME_MIN_LENGTH = 3
    const val CREW_NAME_MAX_LENGTH = 20

    /** Ein Mindestalter, damit Geburtsdaten von vor zwei Jahren nicht durchgehen. */
    const val MIN_AGE_YEARS = 13

    const val MIN_HEIGHT_CM = 50
    const val MAX_HEIGHT_CM = 250

    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 300.0

    /** Ein Workout dauert hoechstens einen Tag. */
    const val MIN_DURATION_MINUTES = 1
    const val MAX_DURATION_MINUTES = 24 * 60

    const val MIN_DISTANCE_KM = 0.1
    const val MAX_DISTANCE_KM = 300.0

    const val MIN_CHALLENGE_GOAL = 1
    const val MAX_CHALLENGE_GOAL = 10_000

    /**
     * Ein Name muss mindestens zwei Zeichen haben und wenigstens einen
     * Buchstaben enthalten. Bewusst nicht enger: Bindestriche, Apostrophe und
     * Umlaute kommen in echten Namen vor.
     */
    fun isValidName(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length in NAME_MIN_LENGTH..NAME_MAX_LENGTH &&
                trimmed.any { it.isLetter() }
    }

    fun isValidCrewName(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length in CREW_NAME_MIN_LENGTH..CREW_NAME_MAX_LENGTH &&
                trimmed.any { it.isLetter() || it.isDigit() }
    }

    fun heightOrNull(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it in MIN_HEIGHT_CM..MAX_HEIGHT_CM }

    fun weightOrNull(value: String): Double? =
        parseDecimal(value)?.takeIf { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG }

    fun durationOrNull(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES }

    fun distanceOrNull(value: String): Double? =
        parseDecimal(value)?.takeIf { it in MIN_DISTANCE_KM..MAX_DISTANCE_KM }

    /**
     * Prueft ein Challenge-Ziel gegen die Obergrenze der jeweiligen Art.
     *
     * Die Grenze haengt an der Einheit und steht deshalb am Typ: zwanzig
     * Trainingstage sind viel, zwanzigtausend Schritte sind ein Nachmittag.
     * Eine gemeinsame Zahl waere fuer das eine unsinnig eng und fuer das
     * andere wertlos weit.
     */
    fun challengeGoalOrNull(value: String, maxGoal: Int = MAX_CHALLENGE_GOAL): Int? =
        parseDecimal(value)?.takeIf { it >= MIN_CHALLENGE_GOAL && it <= maxGoal }
            ?.toInt()

    /** Akzeptiert Komma wie Punkt als Dezimaltrennzeichen. */
    private fun parseDecimal(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
