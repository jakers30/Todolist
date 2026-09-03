package com.example.todolist.data.repo

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.todolist.data.PasswordHasher
import com.example.todolist.data.db.CursorRow
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.db.queryList
import com.example.todolist.data.model.BatchTask
import com.example.todolist.data.model.Role
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.data.model.User

/**
 * Authentication & user management. Synchronous methods: call from a background
 * dispatcher (ViewModels use Dispatchers.IO).
 */
class AuthRepository(private val db: TodoDbHelper) {

    fun findUserByUsername(username: String): User? {
        return db.readableDatabase.queryList(
            "SELECT * FROM users WHERE username = ?",
            arrayOf(username)
        ) { row -> row.toUser() }.firstOrNull()
    }

    fun getUserById(id: Long): User? {
        return db.readableDatabase.queryList(
            "SELECT * FROM users WHERE id = ?",
            arrayOf(id)
        ) { row -> row.toUser() }.firstOrNull()
    }

    fun login(username: String, password: String): User? {
        val user = findUserByUsername(username.trim()) ?: return null
        return if (PasswordHasher.verify(password, user.salt, user.passwordHash)) user else null
    }

    /** Returns a Result message; success when username is available and account is created. */
    fun register(username: String, password: String, batchId: Long): Result<Unit> {
        val clean = username.trim()
        if (clean.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Username and password are required."))
        }
        if (findUserByUsername(clean) != null) {
            return Result.failure(IllegalArgumentException("Username already taken."))
        }
        val (salt, hash) = PasswordHasher.hash(password)
        val now = System.currentTimeMillis()
        val id = db.writableDatabase.insertOrThrow(
            "users", null,
            ContentValues().apply {
                put("username", clean)
                put("password_hash", hash)
                put("salt", salt)
                put("role", Role.USER.name)
                // Fix #2: batchId <= 0 means the user registered with "No Batch".
                if (batchId > 0) put("batch_id", batchId) else putNull("batch_id")
                put("created_at", now)
            }
        )
        // A new member receives every task already assigned to the batch (skip for No Batch).
        if (batchId > 0) {
            assignExistingBatchTasks(id, batchId)
        }
        return Result.success(Unit)
    }

    /**
     * Fix #2: assign a user to a batch (or to "No Batch" when batchId <= 0).
     * Joining a real batch also grants that batch's existing tasks.
     */
    fun updateUserBatch(userId: Long, batchId: Long) {
        db.writableDatabase.update(
            "users",
            ContentValues().apply {
                if (batchId > 0) put("batch_id", batchId) else putNull("batch_id")
            },
            "id = ?",
            arrayOf(userId.toString())
        )
        if (batchId > 0) {
            assignExistingBatchTasks(userId, batchId)
        }
    }

    fun resetPassword(username: String, newPassword: String): Boolean {
        if (newPassword.isEmpty()) return false
        val user = findUserByUsername(username.trim()) ?: return false
        val (salt, hash) = PasswordHasher.hash(newPassword)
        val values = ContentValues().apply {
            put("password_hash", hash)
            put("salt", salt)
        }
        return db.writableDatabase.update("users", values, "id = ?", arrayOf(user.id.toString())) > 0
    }

    fun countUsersInBatch(batchId: Long): Int {
        val row = db.readableDatabase.queryList(
            "SELECT COUNT(*) AS c FROM users WHERE batch_id = ?",
            arrayOf(batchId)
        ) { it.int("c") }
        return row.firstOrNull() ?: 0
    }

    /** Fix #3: full user records for everyone in a batch (for the batch detail view). */
    fun getUsersInBatch(batchId: Long): List<User> {
        return db.readableDatabase.queryList(
            "SELECT * FROM users WHERE batch_id = ? ORDER BY created_at ASC",
            arrayOf(batchId)
        ) { row -> row.toUser() }
    }

    /** Users in a batch (used when assigning tasks). */
    fun getUserIdsInBatch(batchId: Long): List<Long> {
        return db.readableDatabase.queryList(
            "SELECT id FROM users WHERE batch_id = ?",
            arrayOf(batchId)
        ) { it.long("id") }
    }

    private fun assignExistingBatchTasks(userId: Long, batchId: Long) {
        val tasks = db.readableDatabase.queryList(
            "SELECT * FROM batch_tasks WHERE batch_id = ?",
            arrayOf(batchId)
        ) { row ->
            BatchTask(
                id = row.long("id"),
                title = row.string("title"),
                description = row.string("description"),
                dueDate = row.long("due_date"),
                priority = row.enum("priority", com.example.todolist.data.model.Priority.entries.toTypedArray()),
                batchId = row.long("batch_id"),
                createdAt = row.long("created_at"),
                updatedAt = row.long("updated_at")
            )
        }
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            for (task in tasks) {
                w.insertWithOnConflict(
                    "user_tasks", null,
                    ContentValues().apply {
                        put("user_id", userId)
                        put("task_id", task.id)
                        put("status", TaskStatus.ACCEPTED.name)
                        put("completed_at", null as String?)
                        put("has_proof", 0)
                        put("last_notified_due", 0)
                        put("last_notified_overdue", 0)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private fun CursorRow.toUser() = User(
        id = long("id"),
        username = string("username"),
        passwordHash = string("password_hash"),
        salt = string("salt"),
        role = Role.valueOf(string("role")),
        batchId = longOrNull("batch_id"),
        createdAt = long("created_at")
    )
}
