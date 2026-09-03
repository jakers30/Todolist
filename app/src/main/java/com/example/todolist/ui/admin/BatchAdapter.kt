package com.example.todolist.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.data.model.Batch
import com.example.todolist.databinding.ItemBatchBinding

class BatchAdapter(
    private val onClick: (Batch) -> Unit
) : RecyclerView.Adapter<BatchAdapter.VH>() {

    private val items = mutableListOf<Batch>()
    private val memberCounts = mutableMapOf<Long, Int>()
    private val taskCounts = mutableMapOf<Long, Int>()

    fun submit(list: List<Batch>, members: Map<Long, Int>, tasks: Map<Long, Int>) {
        items.clear()
        items.addAll(list)
        memberCounts.clear()
        memberCounts.putAll(members)
        taskCounts.clear()
        taskCounts.putAll(tasks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemBatchBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(batch: Batch) {
            b.batchName.text = batch.name
            val members = memberCounts[batch.id] ?: 0
            b.members.text = "$members member${if (members == 1) "" else "s"}"
            val tasks = taskCounts[batch.id] ?: 0
            b.taskCount.text = "$tasks task${if (tasks == 1) "" else "s"}"
            // Fix #3: tapping a batch opens its detail view (member list).
            b.root.setOnClickListener { onClick(batch) }
        }
    }
}

