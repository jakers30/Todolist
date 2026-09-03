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
import com.example.todolist.TaskApp
import com.example.todolist.data.model.Batch
import com.example.todolist.data.model.Priority
import com.example.todolist.databinding.FragmentAdminTaskFormBinding
import com.example.todolist.util.DateUtils
import java.util.Calendar

class AdminTaskFormFragment : Fragment() {

    private var _binding: FragmentAdminTaskFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AdminViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private var batches: List<Batch> = emptyList()
    private var selectedBatchId: Long = -1L
    private var selectedPriority = Priority.MEDIUM
    private var selectedDueMillis: Long = System.currentTimeMillis()
    private var isEditing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTaskFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadBatches()

        binding.priorityDropdown.setSimpleItems(
            Priority.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        )
        binding.priorityDropdown.setText("Medium", false)
        binding.priorityDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPriority = Priority.entries[position]
        }

        initDueDate()
        binding.dueDate.setOnClickListener { showDatePicker() }

        binding.saveButton.setOnClickListener { save() }

        viewModel.batches.observe(viewLifecycleOwner) { list ->
            batches = list
            binding.batchDropdown.setSimpleItems(list.map { it.name }.toTypedArray())
            // Edited tasks stay in their original batch (batch is read-only).
            val task = viewModel.editingTask.value
            if (task != null) {
                selectedBatchId = task.batchId
                binding.batchDropdown.setText(list.firstOrNull { it.id == task.batchId }?.name ?: "", false)
                binding.batchDropdown.isEnabled = false
            }
        }

        // Fix #1: capture the selected batch. Without this listener the dropdown
        // only displayed the name while selectedBatchId stayed -1, so submitting
        // always failed with "Please select a batch."
        binding.batchDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedBatchId = batches.getOrNull(position)?.id ?: -1L
        }

        viewModel.editingTask.observe(viewLifecycleOwner) { task ->
            if (task != null) {
                isEditing = true
                binding.headerText.text = "Edit task"
                binding.saveButton.text = "Save changes"
                binding.title.setText(task.title)
                binding.description.setText(task.description)
                selectedPriority = task.priority
                binding.priorityDropdown.setText(
                    task.priority.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                    false
                )
                selectedDueMillis = task.dueDate
                binding.dueDate.setText(DateUtils.formatDue(task.dueDate))
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.loadingOverlay.root.isVisible = it
            binding.saveButton.isEnabled = !it
        }
        viewModel.onSaved.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { findNavController().popBackStack() }
        }
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun initDueDate() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 0)
        selectedDueMillis = cal.timeInMillis
        binding.dueDate.setText(DateUtils.formatDue(selectedDueMillis))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedDueMillis
        android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                cal.set(year, month, day, 23, 59, 59)
                cal.set(Calendar.MILLISECOND, 0)
                selectedDueMillis = cal.timeInMillis
                binding.dueDate.setText(DateUtils.formatDue(selectedDueMillis))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun save() {
        val taskId = viewModel.editingTask.value?.id ?: -1L
        viewModel.saveTask(
            taskId = taskId,
            title = binding.title.text?.toString().orEmpty(),
            description = binding.description.text?.toString().orEmpty(),
            dueDate = selectedDueMillis,
            priority = selectedPriority,
            batchId = selectedBatchId
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
