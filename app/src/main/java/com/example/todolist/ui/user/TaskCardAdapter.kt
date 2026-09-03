package com.example.todolist.ui.user

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.R
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.databinding.ItemTaskCardBinding
import com.example.todolist.util.DateUtils

class TaskCardAdapter(
    private val onStart: (TaskCard) -> Unit,
    private val onComplete: (TaskCard) -> Unit,
    private val onDelete: ((TaskCard) -> Unit)? = null,
    private val onUndo: ((TaskCard) -> Unit)? = null,
    private val onEdit: ((TaskCard) -> Unit)? = null
) : RecyclerView.Adapter<TaskCardAdapter.VH>() {

    private val items = mutableListOf<TaskCard>()

    /** Fix #10: expanded task ids (tapping a card toggles its detail view). */
    private val expandedIds = mutableSetOf<Long>()

    fun submit(list: List<TaskCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTaskCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }
    inner class VH(private val b: ItemTaskCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(card: TaskCard) {
            val ctx = b.root.context
            b.title.text = card.title
            b.description.text = card.description

            // Fix #9: Due Date | Status | Source tags at the top-right.
            b.dueText.text = DateUtils.dueLabel(card.dueDate, card.status == TaskStatus.COMPLETED)
            if (card.isOverdue) {
                b.dueText.setTextColor(ContextCompat.getColor(ctx, R.color.overdue))
                b.dueText.setBackgroundResource(R.drawable.bg_badge_error)
            } else {
                b.dueText.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                b.dueText.setBackgroundResource(R.drawable.bg_badge)
            }

            b.statusBadge.text = card.status.name.lowercase().replaceFirstChar { it.uppercase() }
            b.statusBadge.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when (card.status) {
                        TaskStatus.ACCEPTED -> R.color.status_accepted
                        TaskStatus.ONGOING -> R.color.status_ongoing
                        TaskStatus.COMPLETED -> R.color.status_completed
                    }
                )
            )

            b.sourceBadge.text = if (card.source.name == "BATCH") "General" else "Personal"
            b.sourceBadge.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (card.source.name == "BATCH") R.color.text_secondary else R.color.primary
                )
            )

            // Priority (secondary meta row).
            b.priorityBadge.text = card.priority.name.lowercase().replaceFirstChar { it.uppercase() }
            b.priorityBadge.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when (card.priority) {
                        Priority.HIGH -> R.color.priority_high
                        Priority.MEDIUM -> R.color.priority_medium
                        Priority.LOW -> R.color.priority_low
                    }
                )
            )

            b.proofIcon.isVisible = card.hasProof

            // Fix #11: Undo (Ongoing -> Accepted).
            b.btnUndo.isVisible = card.status == TaskStatus.ONGOING
            b.btnUndo.setOnClickListener { onUndo?.invoke(card) }

            b.deleteButton.isVisible = onDelete != null
            b.deleteButton.setOnClickListener { onDelete?.invoke(card) }

            // Fix #4: personal-task editing. Once a task is Completed the Edit
            // action is greyed out (viewing + delete still work). No edit button
            // on batch/admin-sent tasks (onEdit == null).
            b.btnEdit.isVisible = onEdit != null
            val canEdit = onEdit != null && card.status != TaskStatus.COMPLETED
            b.btnEdit.isEnabled = canEdit
            b.btnEdit.alpha = if (canEdit) 1f else 0.4f
            b.btnEdit.setOnClickListener { onEdit?.invoke(card) }

            val completed = card.status == TaskStatus.COMPLETED
            b.completedText.isVisible = completed
            b.actionsRow.isVisible = !completed
            b.btnStart.isVisible = card.status == TaskStatus.ACCEPTED
            b.btnComplete.isVisible = card.status == TaskStatus.ACCEPTED || card.status == TaskStatus.ONGOING

            b.btnStart.setOnClickListener { onStart(card) }
            b.btnComplete.setOnClickListener { onComplete(card) }

            // Fix #10: fill the expanded detail section.
            b.fullDescription.text = card.description
            b.detailDue.text = "Due: ${DateUtils.formatDateTime(card.dueDate)}"
            b.detailPriority.text = "Priority: ${card.priority.name.lowercase().replaceFirstChar { it.uppercase() }}"
            b.detailSource.text = when {
                card.source.name == "BATCH" -> "Source: Admin-sent (${card.batchName ?: "General"})"
                else -> "Source: Personal"
            }
            b.detailStatus.text = "Status: ${card.status.name.lowercase().replaceFirstChar { it.uppercase() }}"
            b.detailProof.isVisible = card.hasProof
            b.detailProof.text = if (card.hasProof) "Proof attached" else ""

            // Restore or collapse the detail section without re-animating on rebind.
            if (expandedIds.contains(card.taskId)) {
                b.detailsSection.isVisible = true
                b.detailsSection.alpha = 1f
            } else {
                b.detailsSection.isVisible = false
                // Fix #10: recycled cards must go back to wrap-content height.
                val lp = b.root.layoutParams
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                b.root.layoutParams = lp
            }

            b.cardRoot.setOnClickListener { toggleExpand(card) }
        }
        private fun toggleExpand(card: TaskCard) {
            val expanded = !expandedIds.contains(card.taskId)
            if (expanded) {
                expandedIds.add(card.taskId)
                b.detailsSection.isVisible = true
                b.detailsSection.alpha = 0f
                b.detailsSection.animate().alpha(1f).setDuration(220)
                    .setInterpolator(DecelerateInterpolator()).start()
                animateCardHeight(expanded = true)
            } else {
                expandedIds.remove(card.taskId)
                b.detailsSection.animate().alpha(0f).setDuration(180).withEndAction {
                    b.detailsSection.isVisible = false
                }.start()
                animateCardHeight(expanded = false)
            }
        }

        /** Smoothly grows/shrinks the card height so the expansion feels "enlarged". */
        private fun animateCardHeight(expanded: Boolean) {
            val root = b.root
            val details = b.detailsSection
            val start = root.height
            val target = if (expanded) {
                // Reveal the section first so we can measure its natural height.
                details.visibility = View.VISIBLE
                val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                details.measure(spec, spec)
                start + details.measuredHeight
            } else {
                start - details.measuredHeight
            }
            val animator = ValueAnimator.ofInt(start, target)
            animator.duration = 220
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener {
                val lp = root.layoutParams
                lp.height = it.animatedValue as Int
                root.layoutParams = lp
            }
            animator.start()
        }
    }
}
