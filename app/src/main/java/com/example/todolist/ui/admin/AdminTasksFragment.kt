package com.example.todolist.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.databinding.FragmentAdminTasksBinding
import com.example.todolist.ui.common.navAnimOptions

class AdminTasksFragment : Fragment() {

    private var _binding: FragmentAdminTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AdminViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private lateinit var adapter: AdminTaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminTaskAdapter(
            onEdit = { taskId ->
                findNavController().navigate(R.id.adminTaskFormFragment, null, navAnimOptions(), null)
                viewModel.loadTaskForEdit(taskId)
            },
            onDelete = { item ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete task?")
                    .setMessage("\u201C${item.task.title}\u201D will be removed from every user's view in ${item.batchName}.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteTask(item.task.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.tasksList.adapter = adapter

        binding.fabAddTask.setOnClickListener {
            viewModel.editingTask.value = null
            findNavController().navigate(R.id.adminTaskFormFragment, null, navAnimOptions(), null)
        }

        // Fix #4: tap the chip to cycle the batch filter (All → Batch A → Batch B → …).
        binding.batchFilterChip.setOnClickListener { viewModel.cycleBatchFilter() }

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.tasks.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.emptyState.isVisible = list.isEmpty()
        }
        viewModel.batchFilterId.observe(viewLifecycleOwner) { filterId ->
            val name = viewModel.batches.value?.firstOrNull { it.id == filterId }?.name
            binding.batchFilterChip.text = name ?: "All batches"
        }

        viewModel.loadBatches()
        viewModel.loadTasks()
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning from the form screen.
        viewModel.loadTasks()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
