package com.example.todolist.data.repo

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import com.example.todolist.data.db.CursorRow
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.db.queryList
import com.example.todolist.data.model.Batch

/** Dynamic batch management (Admins create batches; registration picks one). */
class BatchRepository(private val db: TodoDbHelper) {

    fun getBatches(): List<Batch> {
        return db.readableDatabase.queryList(
            "SELECT * FROM batches ORDER BY created_at ASC"
        ) { row -> row.toBatch() }
    }

    fun getBatch(id: Long): Batch? {
        return db.readableDatabase.queryList(
            "SELECT * FROM batches WHERE id = ?",
            arrayOf(id)
        ) { row -> row.toBatch() }.firstOrNull()
    }

    fun getBatchName(id: Long): String? = getBatch(id)?.name

    /**
     * Fix #1: delete a batch. Mirrors the "deleted admin task disappears from every
     * user's view" rule — all tasks in the batch and their per-user assignments are
     * removed, and every member is automatically moved to "No Batch" (batch_id NULL).
     */
    fun deleteBatch(batchId: Long) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            // Remove each member's copy of every task in this batch.
            w.execSQL(
                "DELETE FROM user_tasks WHERE task_id IN (SELECT id FROM batch_tasks WHERE batch_id = ?)",
                arrayOf(batchId)
            )
            // Remove the tasks themselves.
            w.delete("batch_tasks", "batch_id = ?", arrayOf(batchId.toString()))
            // Fix #2: members automatically move to No Batch.
            w.update("users", ContentValues().apply { putNull("batch_id") }, "batch_id = ?", arrayOf(batchId.toString()))
            w.delete("batches", "id = ?", arrayOf(batchId.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun getMemberCount(batchId: Long): Int {
        return db.readableDatabase.queryList(
            "SELECT COUNT(*) AS c FROM users WHERE batch_id = ?",
            arrayOf(batchId)
        ) { it.int("c") }.firstOrNull() ?: 0
    }

    fun getTaskCount(batchId: Long): Int {
        return db.readableDatabase.queryList(
            "SELECT COUNT(*) AS c FROM batch_tasks WHERE batch_id = ?",
            arrayOf(batchId)
        ) { it.int("c") }.firstOrNull() ?: 0
    }

    fun createBatch(name: String): Result<Batch> {
        val clean = name.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Batch name cannot be empty."))
        }
        if (getBatches().any { it.name.equals(clean, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A batch with that name already exists."))
        }
        return try {
            val id = db.writableDatabase.insertOrThrow(
                "batches", null,
                ContentValues().apply {
                    put("name", clean)
                    put("created_at", System.currentTimeMillis())
                }
            )
            Result.success(getBatch(id)!!)
        } catch (e: SQLiteConstraintException) {
            Result.failure(IllegalArgumentException("A batch with that name already exists."))
        }
    }

    private fun CursorRow.toBatch() = Batch(
        id = long("id"),
        name = string("name"),
        createdAt = long("created_at")
    )
}
