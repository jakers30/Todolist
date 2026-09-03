package com.example.todolist.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    private val weekFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    private val dayMonthFmt = SimpleDateFormat("MM/dd", Locale.getDefault())

    fun formatDue(millis: Long): String = dateFmt.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(Date(millis))

    fun formatWeekLabel(millis: Long): String = weekFmt.format(Date(millis))

    /** Fix #2: day-of-week axis label, e.g. 08/24. */
    fun formatDayMonth(millis: Long): String = dayMonthFmt.format(Date(millis))

    fun isOverdue(dueMillis: Long, statusCompleted: Boolean): Boolean =
        !statusCompleted && dueMillis < System.currentTimeMillis()

    /** Human friendly due text: "In 2 days", "Due today", "3 days overdue". */
    fun dueLabel(dueMillis: Long, statusCompleted: Boolean): String {
        if (statusCompleted) return "Completed"
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)

        val due = Calendar.getInstance().apply { timeInMillis = dueMillis }
        due.set(Calendar.HOUR_OF_DAY, 0)
        due.set(Calendar.MINUTE, 0)
        due.set(Calendar.SECOND, 0)
        due.set(Calendar.MILLISECOND, 0)

        val days = ((due.timeInMillis - now.timeInMillis) / 86_400_000L).toInt()
        return when {
            days < 0 -> "${-days} day${if (-days == 1) "" else "s"} overdue"
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            else -> "In $days days"
        }
    }
}
