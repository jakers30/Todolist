package com.example.todolist

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.repo.AuthRepository
import com.example.todolist.data.repo.BatchRepository
import com.example.todolist.data.repo.NotificationRepository
import com.example.todolist.data.repo.PersonalTaskRepository
import com.example.todolist.data.repo.TaskRepository
import com.example.todolist.notification.ReminderWorker
import com.example.todolist.util.SessionManager
import com.example.todolist.util.ThemeManager
import java.util.concurrent.TimeUnit

class TaskApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Apply persisted theme preference before any activity is created.
        container.themeManager.apply()
        // Schedule the daily due-date/overdue reminder check.
        scheduleReminderWorker()
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

/** Simple manual dependency graph (no DI framework needed for this scope). */
class AppContainer(app: Application) {

    val dbHelper: TodoDbHelper = TodoDbHelper(app)

    val authRepository = AuthRepository(dbHelper)
    val batchRepository = BatchRepository(dbHelper)
    val taskRepository = TaskRepository(dbHelper)
    val personalTaskRepository = PersonalTaskRepository(dbHelper)
    val notificationRepository = NotificationRepository(dbHelper)

    val sessionManager = SessionManager(app)
    val themeManager = ThemeManager(app)
}
