package com.example.todolist.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.TaskApp
import com.example.todolist.data.model.User
import com.example.todolist.databinding.DialogBatchMembersBinding
import com.example.todolist.databinding.ItemBatchMemberBinding
import com.example.todolist.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fix #3: batch detail view — tapping a batch container opens this dialog
 * listing every member in that batch.
 */
class BatchMembersDialog : DialogFragment() {

    private var _binding: DialogBatchMembersBinding? = null
    private val binding get() = _binding!!

    private val adapter = MemberAdapter()

    private val viewModel: AdminViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AdminViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBatchMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.list.adapter = adapter

        val args = requireArguments()
        binding.title.text = "Members — ${args.getString(ARG_BATCH_NAME, "")}"
        val batchId = args.getLong(ARG_BATCH_ID, -1L)

        val repo = (requireActivity().application as TaskApp).container.authRepository
        viewLifecycleOwner.lifecycleScope.launch {
            val members = withContext(Dispatchers.IO) { repo.getUsersInBatch(batchId) }
            adapter.submit(members)
            binding.emptyText.isVisible = members.isEmpty()
        }

        // Fix #1: delete the batch (tasks vanish from members' views, members → No Batch).
        binding.deleteBatchButton.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete batch?")
                .setMessage("All tasks in this batch will be removed from every member's view, and members will be moved to No Batch.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteBatch(batchId)
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class MemberAdapter : RecyclerView.Adapter<MemberAdapter.VH>() {

        private val items = mutableListOf<User>()

        fun submit(list: List<User>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemBatchMemberBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(private val b: ItemBatchMemberBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(user: User) {
                b.username.text = user.username
                b.joinedDate.text = "Joined ${DateUtils.formatDue(user.createdAt)}"
            }
        }
    }

    companion object {
        private const val ARG_BATCH_ID = "batch_id"
        private const val ARG_BATCH_NAME = "batch_name"

        fun newInstance(batchId: Long, batchName: String): BatchMembersDialog =
            BatchMembersDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BATCH_ID, batchId)
                    putString(ARG_BATCH_NAME, batchName)
                }
            }
    }
}
