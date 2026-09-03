package com.example.todolist.data.repo

import android.content.ContentValues
import com.example.todolist.data.db.CursorRow
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.db.queryList
import com.example.todolist.data.model.BatchProgressStat
import com.example.todolist.data.model.BatchTask
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.ReminderRow
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskCompletionStat
import com.example.todolist.data.model.TaskSource
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.data.model.UserTask

/** Batch tasks + per-user assignments + admin progress tracking. */
class TaskRepository(private val db: TodoDbHelper) {

    // ---------- Admin: create / edit / delete ----------

    fun createBatchTask(
        title: String,
        description: String,
        dueDate: Long,
        priority: Priority,
        batchId: Long
    ): Long {
        val now = System.currentTimeMillis()
        val w = db.writableDatabase
        w.beginTransaction()
        return try {
            val taskId = w.insertOrThrow(
                "batch_tasks", null,
                ContentValues().apply {
                    put("title", title.trim())
                    put("description", description.trim())
                    put("due_date", dueDate)
                    put("priority", priority.name)
                    put("batch_id", batchId)
                    put("created_at", now)
                    put("updated_at", now)
                }
            )
            val userIds = w.queryList(
                "SELECT id FROM users WHERE batch_id = ?",
                arrayOf(batchId)
            ) { it.long("id") }
            for (userId in userIds) {
                w.insert(
                    "user_tasks", null,
                    ContentValues().apply {
                        put("user_id", userId)
                        put("task_id", taskId)
                        put("status", TaskStatus.ACCEPTED.name)
                        put("completed_at", null as String?)
                        put("has_proof", 0)
                        put("last_notified_due", 0)
                        put("last_notified_overdue", 0)
                    }
                )
            }
            w.setTransactionSuccessful()
            taskId
        } finally {
            w.endTransaction()
        }
    }

    /** Edits keep the task in its original batch. */
    fun updateBatchTask(taskId: Long, title: String, description: String, dueDate: Long, priority: Priority) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("description", description.trim())
            put("due_date", dueDate)
            put("priority", priority.name)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update("batch_tasks", values, "id = ?", arrayOf(taskId.toString()))
    }

    /** Deleting removes it from every user's view in that batch (FK cascade). */
    fun deleteBatchTask(taskId: Long) {
        db.writableDatabase.delete("batch_tasks", "id = ?", arrayOf(taskId.toString()))
    }

    fun getBatchTask(taskId: Long): BatchTask? {
        return db.readableDatabase.queryList(
            "SELECT * FROM batch_tasks WHERE id = ?",
            arrayOf(taskId)
        ) { row -> row.toBatchTask() }.firstOrNull()
    }

    fun getBatchTasks(): List<BatchTask> {
        return db.readableDatabase.queryList(
            "SELECT * FROM batch_tasks ORDER BY due_date ASC"
        ) { row -> row.toBatchTask() }
    }

    // ---------- User: board cards ----------

    fun getUserTaskCards(userId: Long): List<TaskCard> {
        return db.readableDatabase.queryList(
            """SELECT ut.id AS ref_id, bt.id AS task_id, bt.title, bt.description,
                      bt.due_date, bt.priority, ut.status, ut.completed_at, ut.has_proof,
                      b.name AS batch_name
               FROM user_tasks ut
               JOIN batch_tasks bt ON bt.id = ut.task_id
               JOIN batches b ON b.id = bt.batch_id
               WHERE ut.user_id = ?
               ORDER BY bt.due_date ASC""",
            arrayOf(userId)
        ) { row -> row.toTaskCard(TaskSource.BATCH) }
    }

    fun updateUserTaskStatus(userTaskId: Long, status: TaskStatus, hasProof: Boolean) {
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
        db.writableDatabase.update("user_tasks", values, "id = ?", arrayOf(userTaskId.toString()))
    }

    fun setUserTaskNotified(taskId: Long, due: Long, overdue: Long) {
        val values = ContentValues().apply {
            put("last_notified_due", due)
            put("last_notified_overdue", overdue)
        }
        db.writableDatabase.update("user_tasks", values, "id = ?", arrayOf(taskId.toString()))
    }

    fun findUserTaskByUserAndTask(userId: Long, batchTaskId: Long): UserTask? {
        return db.readableDatabase.queryList(
            "SELECT * FROM user_tasks WHERE user_id = ? AND task_id = ?",
            arrayOf(userId, batchTaskId)
        ) { row ->
            UserTask(
                id = row.long("id"),
                userId = row.long("user_id"),
                taskId = row.long("task_id"),
                status = row.enum("status", TaskStatus.entries.toTypedArray()),
                completedAt = row.longOrNull("completed_at"),
                hasProof = row.bool("has_proof"),
                lastNotifiedDue = row.long("last_notified_due"),
                lastNotifiedOverdue = row.long("last_notified_overdue")
            )
        }.firstOrNull()
    }

    /** All batch-task assignments across users, for the reminder worker. */
    fun getAllForReminders(): List<ReminderRow> {
        return db.readableDatabase.queryList(
            """SELECT ut.id AS id, ut.user_id AS user_id, bt.title AS title,
                      bt.due_date AS due_date, ut.status AS status,
                      ut.last_notified_due AS last_notified_due,
                      ut.last_notified_overdue AS last_notified_overdue
               FROM user_tasks ut JOIN batch_tasks bt ON bt.id = ut.task_id"""
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

    /** Fix #5: number of admin-submitted (General) tasks assigned to a user. */
    fun countUserBatchTasks(userId: Long): Int {
        return db.readableDatabase.queryList(
            "SELECT COUNT(*) AS c FROM user_tasks WHERE user_id = ?",
            arrayOf(userId)
        ) { it.int("c") }.firstOrNull() ?: 0
    }

    /** Fix #6: admin-submitted (General) tasks marked Completed in the current
     *  calendar week — exactly 7 entries, one per day. */
    fun completedPerDayOfWeek(userId: Long): List<Pair<Long, Int>> {
        val now = System.currentTimeMillis()
        val start = weekStartMillis(now)
        val day = 24L * 60L * 60L * 1000L
        val end = start + 7 * day

        val completed = db.readableDatabase.queryList(
            """SELECT ut.completed_at FROM user_tasks ut
               WHERE ut.user_id = ? AND ut.status = 'COMPLETED' AND ut.completed_at IS NOT NULL
                 AND ut.completed_at >= ? AND ut.completed_at < ?""",
            arrayOf(userId, start, end)
        ) { it.long("completed_at") }

        return (0 until 7).map { i ->
            val dayStart = start + i * day
            val count = completed.count { it >= dayStart && it < dayStart + day }
            dayStart to count
        }
    }

    private fun weekStartMillis(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    // ---------- Admin: progress tracking ----------

    fun getTaskCompletionStats(): List<TaskCompletionStat> {
        return db.readableDatabase.queryList(
            """SELECT bt.id AS task_id, bt.title, b.name AS batch_name,
                      COUNT(ut.id) AS total_users,
                      SUM(CASE WHEN ut.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
               FROM batch_tasks bt
               JOIN batches b ON b.id = bt.batch_id
               LEFT JOIN user_tasks ut ON ut.task_id = bt.id
               GROUP BY bt.id
               ORDER BY bt.due_date ASC"""
        ) { row ->
            val total = row.int("total_users")
            val completed = row.int("completed")
            TaskCompletionStat(
                taskId = row.long("task_id"),
                title = row.string("title"),
                batchName = row.string("batch_name"),
                totalUsers = total,
                completedCount = completed,
                completionRate = if (total == 0) 0f else completed.toFloat() / total
            )
        }
    }

    fun getBatchProgressStats(): List<BatchProgressStat> {
        return db.readableDatabase.queryList(
            """SELECT b.id AS batch_id, b.name AS batch_name,
                      COUNT(ut.id) AS total_assignments,
                      SUM(CASE WHEN ut.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed
               FROM batches b
               LEFT JOIN batch_tasks bt ON bt.batch_id = b.id
               LEFT JOIN user_tasks ut ON ut.task_id = bt.id
               GROUP BY b.id
               ORDER BY b.created_at ASC"""
        ) { row ->
            val total = row.int("total_assignments")
            val completed = row.int("completed")
            BatchProgressStat(
                batchId = row.long("batch_id"),
                batchName = row.string("batch_name"),
                totalAssignments = total,
                completedAssignments = completed,
                completionRate = if (total == 0) 0f else completed.toFloat() / total
            )
        }
    }

    // ---------- Mappers ----------

    private fun CursorRow.toBatchTask() = BatchTask(
        id = long("id"),
        title = string("title"),
        description = string("description"),
        dueDate = long("due_date"),
        priority = enum("priority", Priority.entries.toTypedArray()),
        batchId = long("batch_id"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at")
    )

    private fun CursorRow.toTaskCard(source: TaskSource) = TaskCard(
        refId = long("ref_id"),
        taskId = long("task_id"),
        title = string("title"),
        description = string("description"),
        dueDate = long("due_date"),
        priority = enum("priority", Priority.entries.toTypedArray()),
        status = enum("status", TaskStatus.entries.toTypedArray()),
        source = source,
        batchName = stringOrNull("batch_name"),
        completedAt = longOrNull("completed_at"),
        hasProof = bool("has_proof"),
        isOverdue = long("due_date") < System.currentTimeMillis() && enum("status", TaskStatus.entries.toTypedArray()) != TaskStatus.COMPLETED
    )
}
