package com.example.todolist.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.todolist.data.PasswordHasher
import com.example.todolist.data.model.Role

/**
 * SQLite schema + low-level access. Temporary local data source; the Repository
 * layer sits on top so a real backend can replace it later.
 */
class TodoDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL,
                batch_id INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(batch_id) REFERENCES batches(id) ON DELETE SET NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE batch_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                due_date INTEGER NOT NULL,
                priority TEXT NOT NULL,
                batch_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(batch_id) REFERENCES batches(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE user_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                task_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACCEPTED',
                completed_at INTEGER,
                has_proof INTEGER NOT NULL DEFAULT 0,
                last_notified_due INTEGER NOT NULL DEFAULT 0,
                last_notified_overdue INTEGER NOT NULL DEFAULT 0,
                UNIQUE(user_id, task_id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(task_id) REFERENCES batch_tasks(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE personal_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                due_date INTEGER NOT NULL,
                priority TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACCEPTED',
                created_at INTEGER NOT NULL,
                completed_at INTEGER,
                has_proof INTEGER NOT NULL DEFAULT 0,
                last_notified_due INTEGER NOT NULL DEFAULT 0,
                last_notified_overdue INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                message TEXT NOT NULL,
                type TEXT NOT NULL,
                task_title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                read INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX idx_users_username ON users(username)")
        db.execSQL("CREATE INDEX idx_user_tasks_user ON user_tasks(user_id)")
        db.execSQL("CREATE INDEX idx_user_tasks_task ON user_tasks(task_id)")
        db.execSQL("CREATE INDEX idx_personal_tasks_user ON personal_tasks(user_id)")
        db.execSQL("CREATE INDEX idx_batch_tasks_batch ON batch_tasks(batch_id)")
        db.execSQL("CREATE INDEX idx_notifications_user ON notifications(user_id)")

        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS notifications")
        db.execSQL("DROP TABLE IF EXISTS personal_tasks")
        db.execSQL("DROP TABLE IF EXISTS user_tasks")
        db.execSQL("DROP TABLE IF EXISTS batch_tasks")
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS batches")
        onCreate(db)
    }

    /** Demo data so the app is usable immediately: one admin, one batch, one user. */
    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        val batchId = db.insertOrThrow(
            "batches", null,
            ContentValues().apply {
                put("name", "Batch A")
                put("created_at", now)
            }
        )

        val (adminSalt, adminHash) = PasswordHasher.hash("admin123")
        db.insertOrThrow(
            "users", null,
            ContentValues().apply {
                put("username", "admin")
                put("password_hash", adminHash)
                put("salt", adminSalt)
                put("role", Role.ADMIN.name)
                put("batch_id", null as String?)
                put("created_at", now)
            }
        )

        val (userSalt, userHash) = PasswordHasher.hash("user123")
        db.insertOrThrow(
            "users", null,
            ContentValues().apply {
                put("username", "user")
                put("password_hash", userHash)
                put("salt", userSalt)
                put("role", Role.USER.name)
                put("batch_id", batchId)
                put("created_at", now)
            }
        )
    }

    companion object {
        const val DB_NAME = "taskboard.db"
        const val DB_VERSION = 1
    }
}

/** Runs a query and maps every row. */
fun <T> SQLiteDatabase.queryList(
    sql: String,
    args: Array<Any?> = arrayOf(),
    mapper: (CursorRow) -> T
): List<T> {
    return cursorQuery(sql, args) { c ->
        val rows = ArrayList<T>()
        while (c.moveToNext()) rows.add(mapper(CursorRow(c)))
        rows
    }
}

/** Runs a query and lets the caller handle the cursor. */
fun <T> SQLiteDatabase.cursorQuery(
    sql: String,
    args: Array<Any?> = arrayOf(),
    block: (android.database.Cursor) -> T
): T {
    val cursor = rawQuery(sql, args.map { it?.toString() }.toTypedArray())
    return try {
        block(cursor)
    } finally {
        cursor.close()
    }
}

/** Lightweight row accessor over a Cursor. */
class CursorRow(private val c: android.database.Cursor) {
    fun long(col: String): Long {
        val idx = c.getColumnIndexOrThrow(col)
        return c.getLong(idx)
    }

    fun longOrNull(col: String): Long? {
        val idx = c.getColumnIndexOrThrow(col)
        return if (c.isNull(idx)) null else c.getLong(idx)
    }

    fun int(col: String): Int {
        val idx = c.getColumnIndexOrThrow(col)
        return c.getInt(idx)
    }

    fun string(col: String): String {
        val idx = c.getColumnIndexOrThrow(col)
        return c.getString(idx)
    }

    fun stringOrNull(col: String): String? {
        val idx = c.getColumnIndexOrThrow(col)
        return if (c.isNull(idx)) null else c.getString(idx)
    }

    fun bool(col: String): Boolean = int(col) == 1

    fun <E : Enum<E>> enum(col: String, values: Array<E>): E {
        val s = string(col)
        return values.firstOrNull { it.name == s } ?: values.first()
    }
}
