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
import com.example.todolist.TaskApp
import com.example.todolist.R
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.databinding.FragmentUserPersonalBinding
import com.example.todolist.util.DateUtils
import java.util.Calendar

class UserPersonalFragment : Fragment() {

    private var _binding: FragmentUserPersonalBinding? = null
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
        _binding = FragmentUserPersonalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TaskCardAdapter(
            onStart = { card -> viewModel.updateStatus(card, TaskStatus.ONGOING, false) },
            onComplete = { card -> showCompleteDialog(card) },
            onUndo = { card -> viewModel.updateStatus(card, TaskStatus.ACCEPTED, false) },
            onEdit = { card -> showEditDialog(card) },
            onDelete = { card ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete personal task?")
                    .setMessage("\u201C${card.title}\u201D will be permanently removed.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePersonal(card.taskId) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.personalList.adapter = adapter

        binding.fabAddPersonal.setOnClickListener { showAddDialog() }

        // Fix #4: no circular spinner on tab switch; the page content-loading
        // animation (navigation enter transition) covers this transition.
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.personalCards.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.emptyState.isVisible = list.isEmpty()
        }

        viewModel.loadPersonal()
    }
    private fun showAddDialog() = showTaskDialog(null)

    /** Fix #4: edit an existing personal task (card UI greys Edit once Completed). */
    private fun showEditDialog(task: TaskCard) = showTaskDialog(task)

    private fun showTaskDialog(task: TaskCard?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_personal_task, null)
        val titleLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.titleLayout)
        val descriptionLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.descriptionLayout)
        val title = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.title)
        val description = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description)
        val dueDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dueDate)
        val priority = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.priorityDropdown)

        priority.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                Priority.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            )
        )
        priority.setText(
            task?.priority?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Medium",
            false
        )
        val calendar = Calendar.getInstance().apply {
            if (task != null) timeInMillis = task.dueDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }
        var dueMillis = calendar.timeInMillis
        dueDate.setText(DateUtils.formatDue(dueMillis))
        // Fix #4: prefill the fields when editing.
        if (task != null) {
            title.setText(task.title)
            description.setText(task.description)
        }
        dueDate.setOnClickListener {
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day, 23, 59, 59)
                    calendar.set(Calendar.MILLISECOND, 0)
                    dueMillis = calendar.timeInMillis
                    dueDate.setText(DateUtils.formatDue(dueMillis))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Fix #5: clear the inline error as soon as the user types.
        title.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.isNotBlank() == true) titleLayout.error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        description.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.isNotBlank() == true) descriptionLayout.error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Fix #5: "Create" must NOT close the dialog while required fields are
        // empty/invalid — validate first, show inline messages, only then create.
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (task == null) "New personal task" else "Edit personal task")
            .setView(dialogView)
            .setPositiveButton(if (task == null) "Create" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val titleText = title.text?.toString()?.trim().orEmpty()
                val descText = description.text?.toString()?.trim().orEmpty()

                var valid = true
                if (titleText.isEmpty()) {
                    titleLayout.error = "Title is required"
                    valid = false
                }
                if (descText.isEmpty()) {
                    descriptionLayout.error = "Description is required"
                    valid = false
                }

                if (valid) {
                    val priorityValue = when (priority.text?.toString()?.lowercase()) {
                        "low" -> Priority.LOW
                        "high" -> Priority.HIGH
                        else -> Priority.MEDIUM
                    }
                    if (task == null) {
                        viewModel.createPersonal(titleText, descText, dueMillis, priorityValue)
                    } else {
                        viewModel.updatePersonal(task.taskId, titleText, descText, dueMillis, priorityValue)
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
        viewModel.loadPersonal()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
