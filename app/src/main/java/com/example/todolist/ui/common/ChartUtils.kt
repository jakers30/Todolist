package com.example.todolist.ui.common

import android.content.res.ColorStateList
import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

object ChartUtils {

    /** Vertical bar chart of completion percentages. */
    fun setBarChart(
        chart: BarChart,
        labels: List<String>,
        values: List<Float>,
        accentColor: Int,
        textColor: Int
    ) {
        val percentFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
        }

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 100f
        chart.axisLeft.textColor = textColor
        chart.axisLeft.granularity = 25f
        chart.axisLeft.valueFormatter = percentFormatter

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = textColor
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.labelCount = labels.size
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return labels.getOrNull(i)?.take(10) ?: ""
            }
        }

        val entries = values.mapIndexed { index, v -> BarEntry(index.toFloat(), v) }
        val dataSet = BarDataSet(entries, "Completion").apply {
            color = accentColor
            valueTextColor = textColor
            valueFormatter = percentFormatter
        }
        chart.data = BarData(dataSet).apply { barWidth = 0.6f }
        chart.invalidate()
    }

    /** Donut-style pie chart of counts by category.
     *  Fix #1: no value/percentage text is drawn inside the circle — the
     *  percentage is shown as a label underneath instead. */
    fun setPieChart(
        chart: PieChart,
        entries: List<PieEntry>,
        colors: IntArray
    ) {
        chart.description.isEnabled = false
        chart.setUsePercentValues(true)
        chart.isDrawHoleEnabled = true
        chart.setHoleColor(Color.TRANSPARENT)
        chart.holeRadius = 55f
        chart.transparentCircleRadius = 58f
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors.toMutableList()
            // Fix #1: never draw text on the ring — the legend below identifies
            // each segment, so the chart stays clean colored segments only.
            setDrawValues(false)
        }
        chart.data = PieData(dataSet)
        chart.invalidate()
    }

    /** Fix #2: thin 7-day bar chart. Y axis = number of tasks completed,
     *  X axis = day of week formatted as MM/DD. */
    fun setDayCountBarChart(
        chart: BarChart,
        labels: List<String>,
        values: List<Float>,
        accentColor: Int,
        textColor: Int
    ) {
        val countFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = (values.maxOrNull() ?: 1f) + 1f
        chart.axisLeft.textColor = textColor
        chart.axisLeft.granularity = 1f
        chart.axisLeft.valueFormatter = countFormatter

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = textColor
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.labelCount = labels.size
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return labels.getOrNull(i) ?: ""
            }
        }

        val entries = values.mapIndexed { index, v -> BarEntry(index.toFloat(), v) }
        val dataSet = BarDataSet(entries, "Completed").apply {
            color = accentColor
            valueTextColor = textColor
            valueTextSize = 10f
            valueFormatter = countFormatter
        }
        chart.data = BarData(dataSet).apply { barWidth = 0.25f }
        chart.invalidate()
    }

    /** Fix #6: one weekly bar graph joining General (admin-submitted) and Personal
     *  completed-task series per day. Bar colors match the on-chart legend. */
    fun setGroupedDayBarChart(
        chart: BarChart,
        labels: List<String>,
        generalValues: List<Float>,
        personalValues: List<Float>,
        generalColor: Int,
        personalColor: Int,
        textColor: Int
    ) {
        val countFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.legend.textColor = textColor
        chart.legend.textSize = 12f
        chart.setScaleEnabled(false)
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false

        val maxValue = maxOf(generalValues.maxOrNull() ?: 0f, personalValues.maxOrNull() ?: 0f)
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = maxValue + 1f
        chart.axisLeft.textColor = textColor
        chart.axisLeft.granularity = 1f
        chart.axisLeft.valueFormatter = countFormatter

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = textColor
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.labelCount = labels.size
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return labels.getOrNull(i) ?: ""
            }
        }

        val generalEntries = generalValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val personalEntries = personalValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val generalSet = BarDataSet(generalEntries, "General").apply {
            color = generalColor
            valueTextColor = textColor
            valueTextSize = 9f
            valueFormatter = countFormatter
        }
        val personalSet = BarDataSet(personalEntries, "Personal").apply {
            color = personalColor
            valueTextColor = textColor
            valueTextSize = 9f
            valueFormatter = countFormatter
        }

        chart.data = BarData(generalSet, personalSet)
        val groupSpace = 0.3f
        val barSpace = 0.05f
        chart.data.barWidth = 0.3f
        chart.data.groupBars(-0.5f, groupSpace, barSpace)
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = labels.size - 0.5f
        chart.invalidate()
    }

    fun tint(view: android.view.View, color: Int) {
        view.backgroundTintList = ColorStateList.valueOf(color)
    }
}
