package com.example.todolist.util

import android.content.Context
import android.content.SharedPreferences
import com.example.todolist.data.model.Role

/** Persists the signed-in user so the app restores its session across launches. */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(userId: Long, username: String, role: Role) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role.name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    val userId: Long get() = prefs.getLong(KEY_USER_ID, -1L)

    val username: String? get() = prefs.getString(KEY_USERNAME, null)

    val role: Role?
        get() = prefs.getString(KEY_ROLE, null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }

    val isLoggedIn: Boolean get() = userId > 0

    companion object {
        private const val PREFS_NAME = "taskboard_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
    }
}
