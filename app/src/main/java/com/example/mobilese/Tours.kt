package com.example.mobilese

/**
 * Die Einfuehrungen aller Bildschirme, an einer Stelle.
 *
 * Jeder Bildschirm hat seine eigene Tour und seinen eigenen Merker: sie laeuft
 * beim **ersten Oeffnen genau dieses Bildschirms** und danach nie wieder. So
 * bekommt niemand vierzig Hinweise am Stueck, sondern die drei bis sieben, die
 * gerade zu sehen sind - und wer einen Bildschirm nie oeffnet, sieht auch nie
 * eine Erklaerung dazu.
 *
 * Warum hier und nicht in den Activities: so steht der gesamte Text, den ein
 * neuer Nutzer zu lesen bekommt, untereinander und laesst sich als Ganzes
 * lesen. Verdopplungen und Widersprueche zwischen zwei Bildschirmen faellt man
 * dabei auf, in vierzehn Dateien verteilt nicht.
 *
 * Ein Halt, dessen Ziel es auf diesem Geraet nicht gibt oder der gerade
 * ausgeblendet ist, wird uebersprungen ([CoachTour]). Deshalb duerfen hier
 * auch Bereiche stehen, die nur manchmal da sind - die Beitrittsanfragen etwa,
 * oder die Karte eines Workouts ohne Ort.
 */
object Tours {

    /** Eine Tour: ihr Merker und ihre Halte. */
    data class Tour(val id: String, val steps: List<CoachTour.Step>)

    private fun step(target: Int, title: Int, text: Int) = CoachTour.Step(target, title, text)

    /**
     * Ein Halt, der weiterfuehrt: hier gibt es keinen Weiter-Knopf, sondern
     * nur das freigestellte Ziel. Wer es antippt, ist damit schon auf der
     * naechsten Station - so bricht der Weg zwischen zwei Bildschirmen nicht
     * ab.
     */
    private fun handOver(target: Int, title: Int, text: Int) =
        CoachTour.Step(target, title, text, tapTarget = true)

    /**
     * Startbildschirm.
     *
     * Der Bildschirm von oben nach unten, und am Ende der Weg zur naechsten
     * Station.
     *
     * Hier standen einmal fuenf weitere Halte, einer je Reiter der unteren
     * Leiste. Sie erklaerten alle Reiter auf einmal - und danach lief der Weg
     * durch dieselben fuenf Bildschirme noch einmal, in derselben Reihenfolge.
     * Man bekam also alles zweimal: erst kurz, dann ausfuehrlich. Was die
     * Halte sagten, steht jetzt in dem Wegweiser, der zum jeweiligen Reiter
     * schickt - eine Erklaerung an der Stelle, an der sie gebraucht wird.
     */
    val HOME = Tour("home", listOf(
        step(R.id.llCrewSwitch, R.string.coach_crew_title, R.string.coach_crew_text),
        step(R.id.stepsCard, R.string.coach_steps_title, R.string.coach_steps_text),
        step(R.id.streakCard, R.string.coach_streak_title, R.string.coach_streak_text),
        step(R.id.podium, R.string.coach_podium_title, R.string.coach_podium_text),
        handOver(R.id.btnAllActivities, R.string.coach_all_title, R.string.coach_all_text),
        handOver(R.id.navCrew, R.string.coach_go_crew_title, R.string.coach_go_crew_text)
    ))

    /** Das Formular - der Bildschirm mit den meisten Entscheidungen. */
    val WORKOUT = Tour("workout", listOf(
        step(R.id.tilSport, R.string.coach_w_sport_title, R.string.coach_w_sport_text),
        step(R.id.tilDuration, R.string.coach_w_duration_title, R.string.coach_w_duration_text),
        step(R.id.tilDistance, R.string.coach_w_distance_title, R.string.coach_w_distance_text),
        step(R.id.cvPhoto, R.string.coach_w_photo_title, R.string.coach_w_photo_text),
        step(R.id.cvLocation, R.string.coach_w_place_title, R.string.coach_w_place_text),
        step(R.id.cvVoice, R.string.coach_w_voice_title, R.string.coach_w_voice_text),
        step(R.id.btnSaveActivity, R.string.coach_w_save_title, R.string.coach_w_save_text),
        handOver(R.id.btnTopBarBack, R.string.coach_go_ranking_title, R.string.coach_go_ranking_text)
    ))

    val CREW = Tour("crew", listOf(
        step(R.id.cvCrewImage, R.string.coach_c_image_title, R.string.coach_c_image_text),
        step(R.id.crewLevelCard, R.string.coach_c_level_title, R.string.coach_c_level_text),
        step(R.id.cvCrewCodeSection, R.string.coach_c_code_title, R.string.coach_c_code_text),
        step(R.id.llMembersList, R.string.coach_c_members_title, R.string.coach_c_members_text),
        step(R.id.cvJoinRequests, R.string.coach_c_requests_title, R.string.coach_c_requests_text),
        step(R.id.llCrewChallenges, R.string.coach_c_challenges_title, R.string.coach_c_challenges_text),
        step(R.id.llCrewBattles, R.string.coach_c_battles_title, R.string.coach_c_battles_text),
        step(R.id.btnLeaveCrew, R.string.coach_c_leave_title, R.string.coach_c_leave_text),
        handOver(R.id.btnTopBarBack, R.string.coach_go_workout_title, R.string.coach_go_workout_text)
    ))

    val RANKING = Tour("ranking", listOf(
        step(R.id.tgLeaderboardTab, R.string.coach_r_tabs_title, R.string.coach_r_tabs_text),
        step(R.id.llLeaderboardContainer, R.string.coach_r_list_title, R.string.coach_r_list_text),
        step(R.id.crewMeme, R.string.coach_r_meme_title, R.string.coach_r_meme_text),
        handOver(R.id.btnTopBarBack, R.string.coach_go_me_title, R.string.coach_go_me_text)
    ))

    val ME = Tour("me", listOf(
        step(R.id.levelCard, R.string.coach_m_level_title, R.string.coach_m_level_text),
        step(R.id.tvMeStreakTitle, R.string.coach_m_streak_title, R.string.coach_m_streak_text),
        step(R.id.tilCrewFilter, R.string.coach_m_filter_title, R.string.coach_m_filter_text),
        step(R.id.statMeWorkouts, R.string.coach_m_stats_title, R.string.coach_m_stats_text),
        step(R.id.glMeMedals, R.string.coach_m_medals_title, R.string.coach_m_medals_text),
        handOver(R.id.btnTopBarBack, R.string.coach_go_settings_title, R.string.coach_go_settings_text)
    ))

    val SETTINGS = Tour("settings", listOf(
        step(R.id.llSettingsProfile, R.string.coach_s_profile_title, R.string.coach_s_profile_text),
        step(R.id.llTheme, R.string.coach_s_theme_title, R.string.coach_s_theme_text),
        step(R.id.llNotifications, R.string.coach_s_push_title, R.string.coach_s_push_text),
        step(R.id.llVisibility, R.string.coach_s_private_title, R.string.coach_s_private_text),
        step(R.id.llPermissions, R.string.coach_s_permissions_title, R.string.coach_s_permissions_text),
        step(R.id.llHealthConnect, R.string.coach_s_health_title, R.string.coach_s_health_text),
        step(R.id.llDeleteProfile, R.string.coach_s_delete_title, R.string.coach_s_delete_text),
        step(R.id.llShowIntro, R.string.coach_go_done_title, R.string.coach_go_done_text)
    ))

    val CHALLENGES = Tour("challenges", listOf(
        step(R.id.llChallengesContainer, R.string.coach_ch_list_title, R.string.coach_ch_list_text),
        step(R.id.btnLaunchChallenge, R.string.coach_ch_new_title, R.string.coach_ch_new_text)
    ))

    val BATTLES = Tour("battles", listOf(
        step(R.id.btnStartBattle, R.string.coach_b_start_title, R.string.coach_b_start_text),
        step(R.id.llBattleReceived, R.string.coach_b_received_title, R.string.coach_b_received_text),
        step(R.id.llBattleSent, R.string.coach_b_sent_title, R.string.coach_b_sent_text)
    ))

    val HISTORY = Tour("history", listOf(
        step(R.id.tgActivityScope, R.string.coach_h_scope_title, R.string.coach_h_scope_text),
        step(R.id.llActivitiesContainer, R.string.coach_h_rows_title, R.string.coach_h_rows_text),
        handOver(R.id.btnTopBarBack, R.string.coach_back_home_title, R.string.coach_back_home_text)
    ))

    val DETAIL = Tour("detail", listOf(
        step(R.id.cvDetailPhoto, R.string.coach_d_photo_title, R.string.coach_d_photo_text),
        step(R.id.statPoints, R.string.coach_d_stats_title, R.string.coach_d_stats_text),
        step(R.id.cvDetailMap, R.string.coach_d_map_title, R.string.coach_d_map_text),
        step(R.id.btnDetailVoice, R.string.coach_d_voice_title, R.string.coach_d_voice_text),
        step(R.id.llReactions, R.string.coach_d_reactions_title, R.string.coach_d_reactions_text),
        step(R.id.tilComment, R.string.coach_d_comment_title, R.string.coach_d_comment_text)
    ))

    /**
     * Gemeinsames Training.
     *
     * Der Bildschirm hat zwei Zustaende - suchen und verbunden -, und beim
     * ersten Oeffnen ist nur der erste zu sehen. Die Halte zum laufenden
     * Training werden dann uebersprungen; sie stehen hier trotzdem, damit sie
     * erklaert sind, wenn jemand die Einfuehrung spaeter im verbundenen
     * Zustand noch einmal aufruft.
     */
    val PARTNER = Tour("partner", listOf(
        step(R.id.tvPartnerStatus, R.string.coach_p_search_title, R.string.coach_p_search_text),
        step(R.id.llPartnerFound, R.string.coach_p_found_title, R.string.coach_p_found_text),
        step(R.id.tvSessionTimer, R.string.coach_p_clock_title, R.string.coach_p_clock_text),
        step(R.id.btnSessionAction, R.string.coach_p_stop_title, R.string.coach_p_stop_text),
        step(R.id.btnPartnerSkip, R.string.coach_p_alone_title, R.string.coach_p_alone_text)
    ))

    val LANDING = Tour("landing", listOf(
        step(R.id.btnCreateCrew, R.string.coach_l_create_title, R.string.coach_l_create_text),
        step(R.id.btnJoinCrew, R.string.coach_l_scan_title, R.string.coach_l_scan_text),
        step(R.id.btnJoinByCode, R.string.coach_l_code_title, R.string.coach_l_code_text)
    ))

    val SEARCH = Tour("search", listOf(
        step(R.id.tilSearch, R.string.coach_se_field_title, R.string.coach_se_field_text),
        step(R.id.llSearchResults, R.string.coach_se_results_title, R.string.coach_se_results_text)
    ))

    val PROFILE = Tour("profile", listOf(
        step(R.id.ivProfilePicture, R.string.coach_pr_photo_title, R.string.coach_pr_photo_text),
        step(R.id.etDisplayName, R.string.coach_pr_name_title, R.string.coach_pr_name_text),
        step(R.id.etHeight, R.string.coach_pr_body_title, R.string.coach_pr_body_text),
        step(R.id.llFollowing, R.string.coach_pr_following_title, R.string.coach_pr_following_text),
        step(R.id.glMedals, R.string.coach_pr_medals_title, R.string.coach_pr_medals_text)
    ))

    val MEMBER = Tour("member", listOf(
        step(R.id.btnFollow, R.string.coach_mb_follow_title, R.string.coach_mb_follow_text),
        step(R.id.llMemberSteps, R.string.coach_mb_steps_title, R.string.coach_mb_steps_text),
        step(R.id.llMemberStats, R.string.coach_mb_stats_title, R.string.coach_mb_stats_text)
    ))

    /**
     * Die Wegweiser auf dem Startbildschirm.
     *
     * Fuenf der sechs Stationen liegen hinter einem Reiter der unteren Leiste,
     * und die Leiste gibt es nur hier. Kommt jemand von einer Station zurueck,
     * steht deshalb der Wegweiser zur naechsten da - ein einzelner Halt auf
     * ihrem Reiter, ohne Knopf. Damit ist der Weg an keiner Stelle zu Ende,
     * bevor er wirklich zu Ende ist.
     *
     * Jeder Wegweiser sagt zuerst, was hinter dem Reiter liegt, und dann, dass
     * man ihn antippen soll. So wird jeder Reiter genau einmal erklaert,
     * naemlich in dem Moment, in dem er an der Reihe ist.
     */
    val BRIDGE = mapOf(
        CREW.id to handOver(R.id.navCrew,
            R.string.coach_to_crew_title, R.string.coach_to_crew_text),
        WORKOUT.id to handOver(R.id.navAddWorkout,
            R.string.coach_to_workout_title, R.string.coach_to_workout_text),
        RANKING.id to handOver(R.id.navLeaderboard,
            R.string.coach_to_ranking_title, R.string.coach_to_ranking_text),
        ME.id to handOver(R.id.navMe,
            R.string.coach_to_me_title, R.string.coach_to_me_text),
        SETTINGS.id to handOver(R.id.navSettings,
            R.string.coach_to_settings_title, R.string.coach_to_settings_text)
    )

    /**
     * Der gefuehrte Weg durch die App.
     *
     * Diese sechs Bildschirme kommen in dieser Reihenfolge - jeder erst, wenn
     * der vorige durch ist, und der letzte Halt eines jeden zeigt auf den Weg
     * zum naechsten. Wer zwischendurch woanders hinsieht, bekommt dort nichts;
     * die Einfuehrung wartet an der Stelle, an der sie stehengeblieben ist.
     *
     * Alle uebrigen Bildschirme stehen nicht im Weg: sie erklaeren sich beim
     * ersten Oeffnen von selbst, wann immer das geschieht. Sie in den Weg zu
     * nehmen hiesse, jemanden durch vierzehn Bildschirme zu schicken, bevor er
     * das erste Workout eintragen darf.
     */
    val JOURNEY = listOf(HOME.id, CREW.id, WORKOUT.id, RANKING.id, ME.id, SETTINGS.id)

    /** Alle zusammen - fuer das Zuruecksetzen in den Einstellungen. */
    val ALL = listOf(
        HOME, WORKOUT, CREW, RANKING, ME, SETTINGS, CHALLENGES, BATTLES,
        HISTORY, DETAIL, PARTNER, LANDING, SEARCH, PROFILE, MEMBER
    )
}
