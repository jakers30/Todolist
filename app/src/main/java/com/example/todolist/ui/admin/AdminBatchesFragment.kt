package com.example.todolist.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todolist.TaskApp
import com.example.todolist.databinding.FragmentAdminBatchesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminBatchesFragment : Fragment() {

    private var _binding: FragmentAdminBatchesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AdminViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private val adapter = BatchAdapter(
        onClick = { batch ->
            BatchMembersDialog.newInstance(batch.id, batch.name)
                .show(childFragmentManager, "batch_members")
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.batchesList.adapter = adapter

        binding.fabAddBatch.setOnClickListener { showCreateBatchDialog() }

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.batches.observe(viewLifecycleOwner) { list ->
            binding.emptyState.isVisible = list.isEmpty()
            reloadCounts(list)
        }

        viewModel.loadBatches()
    }

    private fun reloadCounts(batches: List<com.example.todolist.data.model.Batch>) {
        val container = (requireActivity().application as TaskApp).container
        viewLifecycleOwner.lifecycleScope.launch {
            val members = withContext(Dispatchers.IO) {
                batches.associate { it.id to container.batchRepository.getMemberCount(it.id) }
            }
            val tasks = withContext(Dispatchers.IO) {
                batches.associate { it.id to container.batchRepository.getTaskCount(it.id) }
            }
            adapter.submit(batches, members, tasks)
        }
    }

    private fun showCreateBatchDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Batch name"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create batch")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                viewModel.createBatch(input.text?.toString().orEmpty())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Fix #4: refresh on return so data changes (e.g. batch deletion) show
        // immediately without switching tabs away and back.
        viewModel.loadBatches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
