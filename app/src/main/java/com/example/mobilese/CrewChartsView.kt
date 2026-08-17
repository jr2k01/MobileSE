package com.example.mobilese

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/**
 * Fuellt die Auswertung ueber der Rangliste.
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
        showSports(CrewStats.sportShares(snapshot))
    }

    private fun showTotals(totals: CrewStats.Totals) {
        setStat(R.id.statWorkouts, totals.workouts.toString(), R.string.chart_stat_workouts)
        setStat(R.id.statHours, hours(totals.minutes), R.string.chart_stat_hours)
        setStat(R.id.statKilometres, decimal(totals.kilometres), R.string.chart_stat_km)
    }

    /**
     * Die sieben Tagesbalken.
     *
     * Die Hoehen richten sich nach dem staerksten Tag, nicht nach einer festen
     * Obergrenze: sonst waeren bei einer ruhigen Woche alle Balken kaum zu
     * sehen. Der leere Tag bekommt einen Stummel, damit die Reihe nicht
     * abreisst und die Luecke trotzdem erkennbar bleibt.
     */
    private fun showWeek(days: List<CrewStats.DayBar>) {
        val container = root.findViewById<LinearLayout>(R.id.llWeekBars)
        container.removeAllViews()

        val inflater = LayoutInflater.from(root.context)
        val maxMinutes = days.maxOfOrNull { it.minutes } ?: 0
        val fullHeight = root.resources.getDimensionPixelSize(R.dimen.chart_week_height)
        val labelHeight = root.resources.getDimensionPixelSize(R.dimen.chart_bar_label_space)
        val available = fullHeight - labelHeight
        val emptyHeight = root.resources.getDimensionPixelSize(R.dimen.chart_bar_empty_height)

        days.forEach { day ->
            val view = inflater.inflate(R.layout.item_week_bar, container, false)
            val bar = view.findViewById<View>(R.id.vBar)

            bar.layoutParams = bar.layoutParams.apply {
                height = if (maxMinutes <= 0 || day.minutes <= 0) emptyHeight
                else (available * day.minutes / maxMinutes).coerceAtLeast(emptyHeight)
            }
            // Ein Tag ohne Training bleibt sichtbar, aber zurueckgenommen.
            bar.alpha = if (day.minutes > 0) 1f else EMPTY_DAY_ALPHA

            view.findViewById<TextView>(R.id.tvBarLabel).text =
                day.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

            (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
            container.addView(view)
        }

        root.findViewById<TextView>(R.id.tvWeekTotal).text = root.context.getString(
            R.string.chart_week_total,
            NumberFormat.getIntegerInstance().format(days.sumOf { it.minutes })
        )
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

    private fun setStat(containerId: Int, value: String, labelRes: Int) {
        val card = root.findViewById<View>(containerId)
        card.findViewById<TextView>(R.id.tvStatValue).text = value
        card.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
    }

    /** Stunden mit einer Nachkommastelle; Minuten waeren als Summe unlesbar. */
    private fun hours(minutes: Int): String = decimal(minutes / 60.0)

    private fun decimal(value: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)

    private companion object {
        const val EMPTY_DAY_ALPHA = 0.22f
    }
}
