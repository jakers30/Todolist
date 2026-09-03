package com.example.todolist.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.databinding.FragmentUserBoardBinding

class UserBoardFragment : Fragment() {

    private var _binding: FragmentUserBoardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                UserViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private lateinit var adapter: TaskCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBoardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TaskCardAdapter(
            onStart = { card -> viewModel.updateStatus(card, TaskStatus.ONGOING, false) },
            onComplete = { card -> showCompleteDialog(card) },
            onUndo = { card -> viewModel.updateStatus(card, TaskStatus.ACCEPTED, false) }
        )
        binding.tasksList.adapter = adapter

        setupTabs()
        setupFilters()

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.boardCards.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.emptyState.isVisible = list.isEmpty()
            binding.emptyTitle.text =
                if (viewModel.statusFilter == TaskStatus.COMPLETED) "Nothing completed yet" else "All caught up!"
            binding.emptySubtitle.text = "No tasks in this state"
        }

        // Fix #2: No-Batch banner — join a batch from here.
        viewModel.inNoBatch.observe(viewLifecycleOwner) { noBatch ->
            binding.noBatchBanner.isVisible = noBatch
        }
        binding.joinBatchButton.setOnClickListener { showJoinBatchDialog() }

        viewModel.loadBoard()
        viewModel.loadBatchState()
    }

    /** Fix #2: pick a batch to join (user currently in No Batch). */
    private fun showJoinBatchDialog() {
        viewModel.loadAvailableBatches()
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_join_batch, null)
        val dropdownLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.batchDropdownLayout)
        val dropdown = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.batchDropdown)

        // Populate the dropdown once the batch list arrives.
        viewModel.availableBatches.observe(viewLifecycleOwner) { batches ->
            dropdown.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    batches.map { it.name }
                )
            )
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Join a batch")
            .setView(dialogView)
            .setPositiveButton("Join", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = dropdown.text?.toString()?.trim()
                val batch = viewModel.availableBatches.value?.firstOrNull { it.name == name }
                if (batch != null) {
                    viewModel.joinBatch(batch.id)
                    dialog.dismiss()
                } else {
                    dropdownLayout.error = "Select a batch"
                }
            }
        }
        dialog.show()
    }
    private fun setupTabs() {
        binding.statusTabs.addTab(binding.statusTabs.newTab().setText("Accepted"))
        binding.statusTabs.addTab(binding.statusTabs.newTab().setText("Ongoing"))
        binding.statusTabs.addTab(binding.statusTabs.newTab().setText("Completed"))
        binding.statusTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                viewModel.setStatusFilter(
                    when (tab.position) {
                        0 -> TaskStatus.ACCEPTED
                        1 -> TaskStatus.ONGOING
                        else -> TaskStatus.COMPLETED
                    }
                )
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupFilters() {
        binding.sourceChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(binding.chipBatch.id) -> SourceFilter.BATCH
                checkedIds.contains(binding.chipPersonal.id) -> SourceFilter.PERSONAL
                else -> SourceFilter.ALL
            }
            viewModel.setSourceFilter(filter)
        }
        binding.sortChip.setOnClickListener {
            val options = arrayOf("Due date", "Priority", "Status")
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sort by")
                .setItems(options) { _, which ->
                    val sort = when (which) {
                        0 -> SortBy.DUE_DATE
                        1 -> SortBy.PRIORITY
                        else -> SortBy.STATUS
                    }
                    binding.sortChip.text = "Sort: ${options[which]}"
                    viewModel.setSort(sort)
                }
                .show()
        }
    }

    /** Proof of completion: optional placeholder image (real picker implemented later). */
    private fun showCompleteDialog(card: TaskCard) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Complete \u201C${card.title}\u201D?")
            .setMessage("You can attach a proof image for your own record.")
            .setIcon(R.drawable.ic_check_circle)
            .setPositiveButton("Attach placeholder proof") { _, _ ->
                viewModel.updateStatus(card, TaskStatus.COMPLETED, true)
            }
            .setNegativeButton("Complete without proof") { _, _ ->
                viewModel.updateStatus(card, TaskStatus.COMPLETED, false)
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadBoard()
        viewModel.loadBatchState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
