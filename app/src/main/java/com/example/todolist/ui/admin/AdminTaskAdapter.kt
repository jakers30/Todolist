package com.example.todolist.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.R
import com.example.todolist.data.model.Priority
import com.example.todolist.databinding.ItemTaskAdminBinding
import com.example.todolist.util.DateUtils

class AdminTaskAdapter(
    private val onEdit: (Long) -> Unit,
    private val onDelete: (AdminTaskUi) -> Unit
) : RecyclerView.Adapter<AdminTaskAdapter.VH>() {

    private val items = mutableListOf<AdminTaskUi>()

    fun submit(list: List<AdminTaskUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTaskAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemTaskAdminBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AdminTaskUi) {
            val ctx = b.root.context
            b.title.text = item.task.title
            b.description.text = item.task.description
            b.batchBadge.text = item.batchName
            b.batchBadge.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(ctx, R.drawable.ic_group),
                null, null, null
            )
            b.dueText.text = DateUtils.formatDue(item.task.dueDate)
            b.dueText.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(ctx, R.drawable.ic_calendar),
                null, null, null
            )
            b.priorityBadge.text = item.task.priority.name.lowercase().replaceFirstChar { it.uppercase() }
            b.priorityBadge.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when (item.task.priority) {
                        Priority.HIGH -> R.color.priority_high
                        Priority.MEDIUM -> R.color.priority_medium
                        Priority.LOW -> R.color.priority_low
                    }
                )
            )
            b.editButton.setOnClickListener { onEdit(item.task.id) }
            b.deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}
