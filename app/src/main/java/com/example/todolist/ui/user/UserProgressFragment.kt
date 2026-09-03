package com.example.todolist.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.databinding.FragmentUserProgressBinding
import com.example.todolist.ui.common.ChartUtils
import com.example.todolist.util.DateUtils
import com.github.mikephil.charting.data.PieEntry

class UserProgressFragment : Fragment() {

    private var _binding: FragmentUserProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                UserViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            // Fix #3: summary card (rate / total / in progress) with count-up numbers.
            renderSummary(stats)
            renderStatusChart(stats)
        }
        viewModel.dailyCompletions.observe(viewLifecycleOwner) { daily ->
            renderWeekChart(daily)
        }
        viewModel.sourceBreakdown.observe(viewLifecycleOwner) { (general, personal) ->
            renderSourceChart(general, personal)
        }

        viewModel.loadStats()
    }

    /** Fix #3: count-up animation for a summary number (with optional suffix like "%"). */
    private fun animateCount(textView: android.widget.TextView, target: Int, suffix: String = "") {
        val animator = android.animation.ValueAnimator.ofInt(0, target)
        animator.duration = 900
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener {
            textView.text = "${it.animatedValue as Int}$suffix"
        }
        animator.start()
    }

    /** Fix #3: clean summary card — Completion rate, Total tasks, In progress. */
    private fun renderSummary(stats: com.example.todolist.data.model.PersonalStats) {
        val ratePct = if (stats.total == 0) 0 else Math.round(stats.completed * 100f / stats.total)
        animateCount(binding.summaryRateValue, ratePct, "%")
        animateCount(binding.summaryTotalValue, stats.total)
        animateCount(binding.summaryProgressValue, stats.ongoing)

        binding.summaryRateSub.text = "${stats.completed} of ${stats.total} tasks completed"
        binding.summaryTotalSub.text = "${stats.total - stats.completed} remaining"
        binding.summaryProgressSub.text = "${stats.overdue} overdue"
    }

    private fun renderStatusChart(stats: com.example.todolist.data.model.PersonalStats) {
        if (stats.total == 0) {
            binding.chartEmpty.isVisible = true
            binding.statusChart.isVisible = false
            binding.legendRow.isVisible = false
            return
        }
        binding.chartEmpty.isVisible = false
        binding.statusChart.isVisible = true
        binding.legendRow.isVisible = true

        // Fix #1: percentages sit below the circle as labels (not drawn on/inside it).
        binding.legendPctAccepted.text = legendText(stats.accepted, stats.total)
        binding.legendPctOngoing.text = legendText(stats.ongoing, stats.total)
        binding.legendPctCompleted.text = legendText(stats.completed, stats.total)

        // Fix #2: build entries AND their colors in lockstep so every non-zero
        // status (Accepted, Ongoing, Completed) renders with its own consistent
        // color — the legend and chart can never disagree on a slice's color.
        // Labels are passed empty so no "Accepted"/"Completed" text can ever be
        // rendered on the ring (Fix #1); the legend below identifies each segment.
        val ctx = requireContext()
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        if (stats.accepted > 0) {
            entries += PieEntry(stats.accepted.toFloat(), "")
            colors += ContextCompat.getColor(ctx, R.color.status_accepted)
        }
        if (stats.ongoing > 0) {
            entries += PieEntry(stats.ongoing.toFloat(), "")
            colors += ContextCompat.getColor(ctx, R.color.status_ongoing)
        }
        if (stats.completed > 0) {
            entries += PieEntry(stats.completed.toFloat(), "")
            colors += ContextCompat.getColor(ctx, R.color.status_completed)
        }

        ChartUtils.setPieChart(binding.statusChart, entries, colors.toIntArray())
        // keep the count-up/fill animation when the Progress tab is opened.
        binding.statusChart.animateY(900)
    }

    private fun legendText(count: Int, total: Int): String {
        val pct = if (total == 0) 0 else Math.round(count * 100f / total)
        return "$count · $pct%"
    }

    private fun renderWeekChart(daily: List<com.example.todolist.ui.user.DayCompletion>) {
        if (daily.isEmpty()) {
            binding.weekEmpty.isVisible = true
            binding.weekChart.isVisible = false
            return
        }
        binding.weekEmpty.isVisible = false
        binding.weekChart.isVisible = true

        // Fix #6: one joined graph — General and Personal completed tasks per day,
        // bar colors match the on-chart legend (General = secondary, Personal = primary).
        val labels = daily.map { DateUtils.formatDayMonth(it.dayStart) }
        val general = daily.map { it.general.toFloat() }
        val personal = daily.map { it.personal.toFloat() }
        ChartUtils.setGroupedDayBarChart(
            binding.weekChart,
            labels,
            general,
            personal,
            ContextCompat.getColor(requireContext(), R.color.secondary),
            ContextCompat.getColor(requireContext(), R.color.primary),
            ContextCompat.getColor(requireContext(), R.color.text_secondary)
        )
        // keep the count-up/fill animation when the Progress tab is opened.
        binding.weekChart.animateY(900)
    }

    /** Fix #5: donut separating admin-submitted (General) vs Personal tasks. */
    private fun renderSourceChart(general: Int, personal: Int) {
        val total = general + personal
        if (total == 0) {
            binding.sourceChart.isVisible = false
            binding.sourceLegendRow.isVisible = false
            return
        }
        binding.sourceChart.isVisible = true
        binding.sourceLegendRow.isVisible = true

        binding.legendPctGeneral.text = legendText(general, total)
        binding.legendPctPersonal.text = legendText(personal, total)

        val ctx = requireContext()
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        if (general > 0) {
            entries += PieEntry(general.toFloat(), "")
            colors += ContextCompat.getColor(ctx, R.color.secondary)
        }
        if (personal > 0) {
            entries += PieEntry(personal.toFloat(), "")
            colors += ContextCompat.getColor(ctx, R.color.primary)
        }
        ChartUtils.setPieChart(binding.sourceChart, entries, colors.toIntArray())
        binding.sourceChart.animateY(900)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
