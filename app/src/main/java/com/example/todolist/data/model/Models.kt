package com.example.todolist.data.model

/** A user of the app. Passwords are never stored in plaintext. */
data class User(
    val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: Role,
    val batchId: Long?,
    val createdAt: Long = System.currentTimeMillis()
)

enum class Role { ADMIN, USER }

/** A dynamic batch. Admins can create additional batches at any time. */
data class Batch(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class Priority(val rank: Int) {
    LOW(0), MEDIUM(1), HIGH(2);

    companion object {
        fun from(value: String): Priority = valueOf(value.uppercase())
    }
}

/** The three states of the status board. */
enum class TaskStatus(val order: Int) {
    ACCEPTED(0), ONGOING(1), COMPLETED(2);

    companion object {
        fun from(value: String): TaskStatus = valueOf(value.uppercase())
    }
}

/** Where a task came from: a batch (admin-sent) or the user's own space. */
enum class TaskSource { BATCH, PERSONAL }

/** A task created by an Admin and sent to everyone in a batch. */
data class BatchTask(
    val id: Long = 0,
    val title: String,
    val description: String,
    val dueDate: Long,
    val priority: Priority,
    val batchId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Links a batch task to a single user (one row per user per batch task). */
data class UserTask(
    val id: Long = 0,
    val userId: Long,
    val taskId: Long,
    val status: TaskStatus = TaskStatus.ACCEPTED,
    val completedAt: Long? = null,
    val hasProof: Boolean = false,
    val lastNotifiedDue: Long = 0,
    val lastNotifiedOverdue: Long = 0
)

/** A fully private task owned by a single user. */
data class PersonalTask(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val description: String,
    val dueDate: Long,
    val priority: Priority,
    val status: TaskStatus = TaskStatus.ACCEPTED,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val hasProof: Boolean = false,
    val lastNotifiedDue: Long = 0,
    val lastNotifiedOverdue: Long = 0
)

/** In-app reminder/alert record. */
data class NotificationRecord(
    val id: Long = 0,
    val userId: Long,
    val message: String,
    val type: String,
    val taskTitle: String,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

/** Combined view-model for the user status board (batch + personal tasks). */
data class TaskCard(
    val refId: Long,
    val taskId: Long,
    val title: String,
    val description: String,
    val dueDate: Long,
    val priority: Priority,
    val status: TaskStatus,
    val source: TaskSource,
    val batchName: String?,
    val completedAt: Long?,
    val hasProof: Boolean,
    val isOverdue: Boolean
)

/** Admin progress: completion of a single batch task across its batch. */
data class TaskCompletionStat(
    val taskId: Long,
    val title: String,
    val batchName: String,
    val totalUsers: Int,
    val completedCount: Int,
    val completionRate: Float
)

/** Admin progress: aggregate completion per batch. */
data class BatchProgressStat(
    val batchId: Long,
    val batchName: String,
    val totalAssignments: Int,
    val completedAssignments: Int,
    val completionRate: Float
)

/** User progress: personal task statistics. */
data class PersonalStats(
    val accepted: Int,
    val ongoing: Int,
    val completed: Int,
    val total: Int,
    val overdue: Int
)

/** Lightweight row used by the reminder worker for both batch and personal tasks. */
data class ReminderRow(
    val id: Long,
    val userId: Long,
    val title: String,
    val dueDate: Long,
    val status: TaskStatus,
    val lastNotifiedDue: Long,
    val lastNotifiedOverdue: Long
) {
    val statusCompleted: Boolean get() = status == TaskStatus.COMPLETED
}
