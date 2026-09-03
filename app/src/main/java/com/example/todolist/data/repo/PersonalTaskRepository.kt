package com.example.todolist.data.repo

import android.content.ContentValues
import com.example.todolist.data.db.CursorRow
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.db.queryList
import com.example.todolist.data.model.PersonalStats
import com.example.todolist.data.model.PersonalTask
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.ReminderRow
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskSource
import com.example.todolist.data.model.TaskStatus

/** A user's private personal tasks. Fully private — not even the Admin can see them. */
class PersonalTaskRepository(private val db: TodoDbHelper) {

    fun createPersonalTask(
        userId: Long,
        title: String,
        description: String,
        dueDate: Long,
        priority: Priority
    ): Long {
        return db.writableDatabase.insertOrThrow(
            "personal_tasks", null,
            ContentValues().apply {
                put("user_id", userId)
                put("title", title.trim())
                put("description", description.trim())
                put("due_date", dueDate)
                put("priority", priority.name)
                put("status", TaskStatus.ACCEPTED.name)
                put("created_at", System.currentTimeMillis())
                put("completed_at", null as String?)
                put("has_proof", 0)
                put("last_notified_due", 0)
                put("last_notified_overdue", 0)
            }
        )
    }

    fun getPersonalTasks(userId: Long): List<PersonalTask> {
        return db.readableDatabase.queryList(
            "SELECT * FROM personal_tasks WHERE user_id = ? ORDER BY due_date ASC",
            arrayOf(userId)
        ) { row -> row.toPersonalTask() }
    }

    fun deletePersonalTask(id: Long) {
        db.writableDatabase.delete("personal_tasks", "id = ?", arrayOf(id.toString()))
    }

    fun updateStatus(id: Long, status: TaskStatus, hasProof: Boolean) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("status", status.name)
            if (status == TaskStatus.COMPLETED) {
                put("completed_at", now)
            } else {
                putNull("completed_at")
            }
            put("has_proof", if (hasProof) 1 else 0)
        }
        db.writableDatabase.update("personal_tasks", values, "id = ?", arrayOf(id.toString()))
    }

    fun setNotified(id: Long, due: Long, overdue: Long) {
        val values = ContentValues().apply {
            put("last_notified_due", due)
            put("last_notified_overdue", overdue)
        }
        db.writableDatabase.update("personal_tasks", values, "id = ?", arrayOf(id.toString()))
    }

    /** All personal tasks across users, for the reminder worker. */
    fun getAllForReminders(): List<ReminderRow> {
        return db.readableDatabase.queryList(
            "SELECT * FROM personal_tasks"
        ) { row ->
            ReminderRow(
                id = row.long("id"),
                userId = row.long("user_id"),
                title = row.string("title"),
                dueDate = row.long("due_date"),
                status = row.enum("status", TaskStatus.entries.toTypedArray()),
                lastNotifiedDue = row.long("last_notified_due"),
                lastNotifiedOverdue = row.long("last_notified_overdue")
            )
        }
    }
    fun getPersonalCards(userId: Long): List<TaskCard> {
        return db.readableDatabase.queryList(
            "SELECT * FROM personal_tasks WHERE user_id = ? ORDER BY due_date ASC",
            arrayOf(userId)
        ) { row ->
            TaskCard(
                refId = row.long("id"),
                taskId = row.long("id"),
                title = row.string("title"),
                description = row.string("description"),
                dueDate = row.long("due_date"),
                priority = row.enum("priority", Priority.entries.toTypedArray()),
                status = row.enum("status", TaskStatus.entries.toTypedArray()),
                source = TaskSource.PERSONAL,
                batchName = null,
                completedAt = row.longOrNull("completed_at"),
                hasProof = row.bool("has_proof"),
                isOverdue = row.long("due_date") < System.currentTimeMillis() &&
                    row.enum("status", TaskStatus.entries.toTypedArray()) != TaskStatus.COMPLETED
            )
        }
    }

    fun getPersonalStats(userId: Long): PersonalStats {
        return db.readableDatabase.queryList(
            """SELECT
                 COUNT(*) AS total,
                 SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted,
                 SUM(CASE WHEN status = 'ONGOING' THEN 1 ELSE 0 END) AS ongoing,
                 SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                 SUM(CASE WHEN due_date < ? AND status != 'COMPLETED' THEN 1 ELSE 0 END) AS overdue
               FROM personal_tasks WHERE user_id = ?""",
            arrayOf(System.currentTimeMillis(), userId)
        ) { r ->
            PersonalStats(
                accepted = r.int("accepted"),
                ongoing = r.int("ongoing"),
                completed = r.int("completed"),
                total = r.int("total"),
                overdue = r.int("overdue")
            )
        }.firstOrNull() ?: PersonalStats(0, 0, 0, 0, 0)
    }

    /** Fix #4: edit a personal task's contents (title, description, due date, priority). */
    fun updatePersonalTask(
        id: Long,
        title: String,
        description: String,
        dueDate: Long,
        priority: Priority
    ) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("description", description.trim())
            put("due_date", dueDate)
            put("priority", priority.name)
        }
        db.writableDatabase.update("personal_tasks", values, "id = ?", arrayOf(id.toString()))
    }

    /** Fix #2 + #4: completed personal tasks for the current calendar week — always
     *  exactly 7 entries, one per day (empty days are 0). Only tasks whose status
     *  is COMPLETED are counted, so Accepted/Ongoing tasks never appear in the bars. */
    fun completedPerDayOfWeek(userId: Long): List<Pair<Long, Int>> {
        val now = System.currentTimeMillis()
        val start = weekStart(now)
        val day = 24L * 60L * 60L * 1000L
        val end = start + 7 * day

        val completed = db.readableDatabase.queryList(
            """SELECT completed_at FROM personal_tasks
               WHERE user_id = ? AND status = 'COMPLETED' AND completed_at IS NOT NULL
                 AND completed_at >= ? AND completed_at < ?""",
            arrayOf(userId, start, end)
        ) { it.long("completed_at") }

        return (0 until 7).map { i ->
            val dayStart = start + i * day
            val count = completed.count { it >= dayStart && it < dayStart + day }
            dayStart to count
        }
    }

    private fun weekStart(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun CursorRow.toPersonalTask() = PersonalTask(
        id = long("id"),
        userId = long("user_id"),
        title = string("title"),
        description = string("description"),
        dueDate = long("due_date"),
        priority = enum("priority", Priority.entries.toTypedArray()),
        status = enum("status", TaskStatus.entries.toTypedArray()),
        createdAt = long("created_at"),
        completedAt = longOrNull("completed_at"),
        hasProof = bool("has_proof"),
        lastNotifiedDue = long("last_notified_due"),
        lastNotifiedOverdue = long("last_notified_overdue")
    )
}
