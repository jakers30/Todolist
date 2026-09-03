package com.example.todolist.data.repo

import android.content.ContentValues
import com.example.todolist.data.db.CursorRow
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.db.queryList
import com.example.todolist.data.model.NotificationRecord

/** In-app reminder / alert records (mirrors what the system posts). */
class NotificationRepository(private val db: TodoDbHelper) {

    fun insert(record: NotificationRecord): Long {
        return db.writableDatabase.insertOrThrow(
            "notifications", null,
            ContentValues().apply {
                put("user_id", record.userId)
                put("message", record.message)
                put("type", record.type)
                put("task_title", record.taskTitle)
                put("created_at", record.createdAt)
                put("read", if (record.read) 1 else 0)
            }
        )
    }

    fun getForUser(userId: Long): List<NotificationRecord> {
        return db.readableDatabase.queryList(
            "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
            arrayOf(userId)
        ) { row ->
            NotificationRecord(
                id = row.long("id"),
                userId = row.long("user_id"),
                message = row.string("message"),
                type = row.string("type"),
                taskTitle = row.string("task_title"),
                createdAt = row.long("created_at"),
                read = row.bool("read")
            )
        }
    }

    fun getUnreadCount(userId: Long): Int {
        return db.readableDatabase.queryList(
            "SELECT COUNT(*) AS c FROM notifications WHERE user_id = ? AND read = 0",
            arrayOf(userId)
        ) { it.int("c") }.firstOrNull() ?: 0
    }

    fun markAllRead(userId: Long) {
        val values = ContentValues().apply { put("read", 1) }
        db.writableDatabase.update("notifications", values, "user_id = ?", arrayOf(userId.toString()))
    }

    fun deleteAll(userId: Long) {
        db.writableDatabase.delete("notifications", "user_id = ?", arrayOf(userId.toString()))
    }
}
