package com.example.mobilese

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/**
 * Fuellt die Auswertung auf dem Ranglisten-Bildschirm.
 *
 * Steht getrennt von [LeaderboardActivity], weil die Activity sonst zur
 * Haelfte aus Diagrammcode bestuende. Sie bekommt weiterhin nur den Snapshot
 * und reicht ihn hierher weiter.
 */
class CrewChartsView(private val root: View) {

    /** Farben des Rings, der Reihe nach vergeben. */
    private val sportColors = listOf(
        R.color.chart_sport_1,
        R.color.chart_sport_2,
        R.color.chart_sport_3,
        R.color.chart_sport_4,
        R.color.chart_sport_5,
        R.color.chart_sport_6
    )

    fun show(snapshot: CrewSnapshot) {
        showTotals(CrewStats.totals(snapshot))
        showWeek(CrewStats.lastWeek(snapshot))
        showStepWeek(CrewStats.lastWeekSteps(snapshot))
        showMembers(CrewStats.memberShares(snapshot), snapshot.members)
        showHighlights(CrewStats.highlights(snapshot), snapshot.members)
        showSports(CrewStats.sportShares(snapshot))
    }

    private fun showTotals(totals: CrewStats.Totals) {
        setStat(R.id.statWorkouts, totals.workouts.toString(), R.string.chart_stat_workouts)
        setStat(R.id.statHours, hours(totals.minutes), R.string.chart_stat_hours)
        setStat(R.id.statKilometres, decimal(totals.kilometres), R.string.chart_stat_km)
    }

    private fun showWeek(days: List<CrewStats.DayBar>) {
        showDayBars(R.id.llWeekBars, days, R.drawable.bg_week_bar)

        root.findViewById<TextView>(R.id.tvWeekTotal).text = root.context.getString(
            R.string.chart_week_total,
            integer(days.sumOf { it.amount })
        )
    }

    /**
     * Dieselbe Reihe noch einmal, aber mit Schritten.
     *
     * Schritte waren bisher nur als heutiger Stand zu sehen. Ueber eine Woche
     * gelegt zeigen sie auch die Tage, an denen niemand trainiert hat, sich
     * aber trotzdem jemand bewegt hat.
     */
    private fun showStepWeek(days: List<CrewStats.DayBar>) {
        showDayBars(R.id.llStepBars, days, R.drawable.bg_step_bar)

        val total = days.sumOf { it.amount }
        root.findViewById<TextView>(R.id.tvStepsWeekTotal).text =
            if (total <= 0) root.context.getString(R.string.chart_steps_none)
            else root.context.getString(R.string.chart_steps_total, integer(total))
    }

    /**
     * Die sieben Tagesbalken einer Reihe.
     *
     * Die Hoehen richten sich nach dem staerksten Tag, nicht nach einer festen
     * Obergrenze: sonst waeren bei einer ruhigen Woche alle Balken kaum zu
     * sehen. Der leere Tag bekommt einen Stummel, damit die Reihe nicht
     * abreisst und die Luecke trotzdem erkennbar bleibt.
     */
    private fun showDayBars(containerId: Int, days: List<CrewStats.DayBar>, barBackground: Int) {
        val container = root.findViewById<LinearLayout>(containerId)
        container.removeAllViews()

        val inflater = LayoutInflater.from(root.context)
        val maxAmount = days.maxOfOrNull { it.amount } ?: 0
        val fullHeight = root.resources.getDimensionPixelSize(R.dimen.chart_week_height)
        val labelHeight = root.resources.getDimensionPixelSize(R.dimen.chart_bar_label_space)
        val available = fullHeight - labelHeight
        val emptyHeight = root.resources.getDimensionPixelSize(R.dimen.chart_bar_empty_height)

        days.forEach { day ->
            val view = inflater.inflate(R.layout.item_week_bar, container, false)
            val bar = view.findViewById<View>(R.id.vBar)

            bar.setBackgroundResource(barBackground)
            bar.layoutParams = bar.layoutParams.apply {
                height = if (maxAmount <= 0 || day.amount <= 0) emptyHeight
                else (available * day.amount / maxAmount).coerceAtLeast(emptyHeight)
            }
            // Ein Tag ohne Eintrag bleibt sichtbar, aber zurueckgenommen.
            bar.alpha = if (day.amount > 0) 1f else EMPTY_DAY_ALPHA

            // Fest auf Englisch, nicht in der Sprache des Geraets: die
            // uebrige Oberflaeche ist englisch, und ein deutsches "D" neben
            // "Workouts" und "Kilometres" sah nach Versehen aus. Zwei
            // deutsche Tage teilen sich ohnehin denselben Buchstaben (D fuer
            // Dienstag und Donnerstag, M fuer Montag und Mittwoch) - im
            // Englischen ist es nicht besser, aber wenigstens einheitlich.
            view.findViewById<TextView>(R.id.tvBarLabel).text =
                day.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

            (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
            container.addView(view)
        }
    }

    /**
     * Wer wie viel trainiert hat, als Balken nebeneinander.
     *
     * Die Rangliste beantwortet das nicht: dort stehen Punkte, und die
     * verrechnen Training, Challenges und Schritte miteinander. Hier geht es
     * nur um die Zeit auf der Matte.
     *
     * Die Laengen kommen aus Gewichten und beziehen sich auf den Staerksten -
     * so ist jede Zeile im Verhaeltnis zur Spitze zu lesen, nicht nur zu sich
     * selbst.
     */
    private fun showMembers(shares: List<CrewStats.MemberShare>, members: List<UserProfile>) {
        val container = root.findViewById<LinearLayout>(R.id.llMemberBars)
        container.removeAllViews()

        val profileById = members.associateBy { it.id }
        val most = shares.maxOfOrNull { it.minutes } ?: 0
        val inflater = LayoutInflater.from(root.context)

        shares.forEach { share ->
            val row = inflater.inflate(R.layout.item_member_bar, container, false)
            val profile = profileById[share.userId]

            row.findViewById<TextView>(R.id.tvMemberBarName).text =
                profile?.let { DisplayName.of(it) } ?: root.context.getString(R.string.unknown_member)

            ImageLoader.into(
                row.findViewById<ImageView>(R.id.ivMemberBarAvatar),
                profile?.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )

            setWeight(row, R.id.vMemberBarFill, share.minutes)
            setWeight(row, R.id.vMemberBarRest, most - share.minutes)

            row.findViewById<TextView>(R.id.tvMemberBarValue).text =
                root.context.getString(R.string.chart_member_hours, hours(share.minutes))

            container.addView(row)
        }
    }

    /**
     * Vier Einzelwerte, die als Zahl mehr sagen als in einem Diagramm.
     *
     * Die Serie steht bewusst dabei: sie ist der einzige Wert hier, der wieder
     * verloren gehen kann, und das ist der Grund, warum jemand heute noch
     * losgeht.
     */
    private fun showHighlights(highlights: CrewStats.Highlights, members: List<UserProfile>) {
        val container = root.findViewById<LinearLayout>(R.id.llHighlights)
        container.removeAllViews()

        val inflater = LayoutInflater.from(root.context)
        val longest = highlights.longest

        addHighlight(
            inflater, container, R.string.chart_longest,
            if (longest == null) NOTHING_YET else root.context.getString(
                R.string.chart_longest_value,
                longest.duration,
                longest.sport,
                members.firstOrNull { it.id == longest.userId }?.let { DisplayName.of(it) }
                    ?: root.context.getString(R.string.unknown_member)
            )
        )
        addHighlight(
            inflater, container, R.string.chart_streak,
            root.context.getString(R.string.chart_days_value, highlights.streakDays)
        )
        addHighlight(
            inflater, container, R.string.chart_goal_days,
            root.context.getString(R.string.chart_days_value, highlights.goalDays)
        )
        addHighlight(
            inflater, container, R.string.chart_average,
            root.context.getString(R.string.chart_minutes_value, highlights.averageMinutes)
        )
    }

    private fun addHighlight(
        inflater: LayoutInflater,
        container: LinearLayout,
        labelRes: Int,
        value: String
    ) {
        val row = inflater.inflate(R.layout.item_highlight, container, false)
        row.findViewById<TextView>(R.id.tvHighlightLabel).setText(labelRes)
        row.findViewById<TextView>(R.id.tvHighlightValue).text = value
        container.addView(row)
    }

    private fun showSports(shares: List<CrewStats.SportShare>) {
        val donut = root.findViewById<DonutChartView>(R.id.donutSports)
        val legend = root.findViewById<LinearLayout>(R.id.llSportLegend)
        val center = root.findViewById<TextView>(R.id.tvDonutCenter)
        legend.removeAllViews()

        val total = shares.sumOf { it.minutes }
        if (total <= 0) {
            donut.setSlices(emptyList())
            center.text = ""
            legend.addView(TextView(root.context).apply {
                setText(R.string.chart_no_data)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })
            return
        }

        donut.setSlices(
            shares.mapIndexed { index, share ->
                DonutChartView.Slice(share.minutes, colorAt(index))
            }
        )
        center.text = root.context.getString(R.string.chart_donut_center, hours(total))

        val inflater = LayoutInflater.from(root.context)
        val percentages = CrewStats.sharePercentages(shares)
        shares.forEachIndexed { index, share ->
            val row = inflater.inflate(R.layout.item_sport_legend, legend, false)
            row.findViewById<View>(R.id.vLegendColor).background.setTint(colorAt(index))
            row.findViewById<TextView>(R.id.tvLegendSport).text = share.sport
            row.findViewById<TextView>(R.id.tvLegendShare).text =
                root.context.getString(R.string.chart_share_percent, percentages[index])
            legend.addView(row)
        }
    }

    private fun colorAt(index: Int): Int = ContextCompat.getColor(
        root.context,
        sportColors[index % sportColors.size]
    )

    private fun setWeight(row: View, id: Int, value: Int) {
        val part = row.findViewById<View>(id)
        part.layoutParams = (part.layoutParams as LinearLayout.LayoutParams).apply {
            weight = value.coerceAtLeast(0).toFloat()
        }
    }

    private fun setStat(containerId: Int, value: String, labelRes: Int) {
        val card = root.findViewById<View>(containerId)
        card.findViewById<TextView>(R.id.tvStatValue).text = value
        card.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
    }

    /** Stunden mit einer Nachkommastelle; Minuten waeren als Summe unlesbar. */
    private fun hours(minutes: Int): String = decimal(minutes / 60.0)

    private fun decimal(value: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)

    private fun integer(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    private companion object {
        const val EMPTY_DAY_ALPHA = 0.22f

        /** Steht in einer Bestwert-Zeile, solange es noch keinen Wert gibt. */
        const val NOTHING_YET = "–"
    }
}
