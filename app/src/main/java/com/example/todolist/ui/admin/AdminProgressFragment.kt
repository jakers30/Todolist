package com.example.todolist.ui.admin

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
import com.example.todolist.databinding.FragmentAdminProgressBinding
import com.example.todolist.ui.common.ChartUtils

class AdminProgressFragment : Fragment() {

    private var _binding: FragmentAdminProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AdminViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private val adapter = ProgressAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.taskList.adapter = adapter

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.taskStats.observe(viewLifecycleOwner) { stats ->
            adapter.submit(stats)
            binding.emptyState.isVisible = stats.isEmpty()
        }
        viewModel.batchStats.observe(viewLifecycleOwner) { stats ->
            renderBatchChart(stats)
        }

        viewModel.loadProgress()
    }

    private fun renderBatchChart(stats: List<com.example.todolist.data.model.BatchProgressStat>) {
        val labels = stats.map { it.batchName }
        val values = stats.map { it.completionRate * 100f }
        val accent = ContextCompat.getColor(requireContext(), R.color.primary)
        val text = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        if (stats.isEmpty()) {
            binding.batchChartEmpty.isVisible = true
            binding.batchChart.isVisible = false
        } else {
            binding.batchChartEmpty.isVisible = false
            binding.batchChart.isVisible = true
            ChartUtils.setBarChart(binding.batchChart, labels, values, accent, text)
            // Fix #3: animate the bars from 0% to their real values on tab entry.
            binding.batchChart.animateY(900)
        }
    }

    override fun onResume() {
        super.onResume()
        // Fix #4: refresh on return so completion stats show immediately.
        viewModel.loadProgress()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
