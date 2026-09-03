package com.example.todolist.ui.admin

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.R
import com.example.todolist.data.model.TaskCompletionStat
import com.example.todolist.databinding.ItemProgressBinding

class ProgressAdapter : RecyclerView.Adapter<ProgressAdapter.VH>() {

    private val items = mutableListOf<TaskCompletionStat>()

    /** Fix #3: only animate a bar once per fresh load, so scroll rebinds don't re-run. */
    private val animatedIds = mutableSetOf<Long>()

    fun submit(list: List<TaskCompletionStat>) {
        items.clear()
        items.addAll(list)
        animatedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemProgressBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(stat: TaskCompletionStat) {
            val pct = (stat.completionRate * 100).toInt()
            b.title.text = stat.title
            b.batchName.text = "Batch: ${stat.batchName}"
            b.percentText.text = "$pct%"
            b.countText.text = "${stat.completedCount} of ${stat.totalUsers} users completed"
            val colorRes = if (stat.completionRate >= 1f) R.color.status_completed else R.color.primary
            b.percentText.setTextColor(
                ContextCompat.getColor(b.root.context, colorRes)
            )

            // Fix #3: smooth 0 -> value fill on tab entry, synced with the chart.
            if (animatedIds.add(stat.taskId)) {
                b.progressBar.progress = 0
                b.progressBar.post {
                    ValueAnimator.ofInt(0, pct).apply {
                        duration = 900
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { b.progressBar.progress = it.animatedValue as Int }
                        start()
                    }
                }
            } else {
                b.progressBar.progress = pct
            }
        }
    }
}

