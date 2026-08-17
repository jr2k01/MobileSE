package com.example.mobilese

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Die Medaillen, die sich in CrewFit verdienen lassen.
 *
 * Die Reihenfolge im Enum ist die Reihenfolge der Anzeige: erst die, die man
 * frueh bekommt, dann die schwereren. So sieht ein neuer Nutzer vorne, was als
 * Naechstes erreichbar ist, statt zwischen lauter fernen Zielen zu suchen.
 *
 * Neue Medaillen kommen hier dazu und bekommen in [Medals] ihre Bedingung. Es
 * braucht nichts weiter - kein Eintrag in der Datenbank, keine Migration.
 */
enum class Medal(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int
) {
    FIRST_WORKOUT(
        R.string.medal_first_workout,
        R.string.medal_first_workout_desc,
        R.drawable.medal_first_workout
    ),
    FIRST_STEP_GOAL(
        R.string.medal_first_step_goal,
        R.string.medal_first_step_goal_desc,
        R.drawable.medal_step_goal
    ),
    TEN_WORKOUTS(
        R.string.medal_ten_workouts,
        R.string.medal_ten_workouts_desc,
        R.drawable.medal_ten_workouts
    ),
    CHALLENGE_WINNER(
        R.string.medal_challenge,
        R.string.medal_challenge_desc,
        R.drawable.medal_challenge
    ),
    STEP_STREAK(
        R.string.medal_step_streak,
        R.string.medal_step_streak_desc,
        R.drawable.medal_step_streak
    ),
    MARATHON(
        R.string.medal_marathon,
        R.string.medal_marathon_desc,
        R.drawable.medal_marathon
    ),
    FIFTY_WORKOUTS(
        R.string.medal_fifty_workouts,
        R.string.medal_fifty_workouts_desc,
        R.drawable.medal_fifty_workouts
    )
}
