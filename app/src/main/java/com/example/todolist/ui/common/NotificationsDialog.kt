package com.example.todolist.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.todolist.TaskApp
import com.example.todolist.R
import com.example.todolist.data.model.NotificationRecord
import com.example.todolist.databinding.DialogNotificationsBinding
import com.example.todolist.databinding.ItemNotificationBinding
import com.example.todolist.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bottom-sheet list of in-app due/overdue reminders. */
class NotificationsDialog : DialogFragment() {

    private var _binding: DialogNotificationsBinding? = null
    private val binding get() = _binding!!

    private val adapter = NotificationAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.list.adapter = adapter

        binding.markAllRead.setOnClickListener {
            val userId = (requireActivity().application as TaskApp).container.sessionManager.userId
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    (requireActivity().application as TaskApp).container.notificationRepository.markAllRead(userId)
                }
                load()
            }
        }

        load()
    }

    private fun load() {
        val userId = (requireActivity().application as TaskApp).container.sessionManager.userId
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                (requireActivity().application as TaskApp).container.notificationRepository.getForUser(userId)
            }
            adapter.submit(items)
            binding.emptyText.isVisible = items.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {

        private val items = mutableListOf<NotificationRecord>()

        fun submit(list: List<NotificationRecord>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: NotificationRecord) {
                val ctx = b.root.context
                b.title.text = if (item.type == "OVERDUE") "⚠ Overdue" else "🔔 Due soon"
                b.message.text = item.message
                b.time.text = DateUtils.formatDateTime(item.createdAt)
                b.root.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(
                        ctx,
                        if (item.read) R.color.surface else R.color.surface_variant
                    )
                )
            }
        }
    }
}
