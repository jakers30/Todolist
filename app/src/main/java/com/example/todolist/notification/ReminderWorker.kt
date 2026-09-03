package com.example.todolist.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todolist.MainActivity
import com.example.todolist.R
import com.example.todolist.data.db.TodoDbHelper
import com.example.todolist.data.model.NotificationRecord
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.data.repo.NotificationRepository
import com.example.todolist.data.repo.PersonalTaskRepository
import com.example.todolist.data.repo.TaskRepository
import com.example.todolist.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Daily check for due-soon and overdue tasks. Posts a system notification and
 * stores an in-app record. Re-notification is throttled to once per 24h per task.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val db = TodoDbHelper(applicationContext)
            val taskRepo = TaskRepository(db)
            val personalRepo = PersonalTaskRepository(db)
            val notificationRepo = NotificationRepository(db)

            checkBatchTasks(taskRepo, notificationRepo)
            checkPersonalTasks(personalRepo, notificationRepo)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    private fun checkBatchTasks(taskRepo: TaskRepository, notificationRepo: NotificationRepository) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        for (row in taskRepo.getAllForReminders()) {
            if (row.statusCompleted) continue
            val dueSoon = row.dueDate - now in 0..day
            val overdue = row.dueDate < now

            if (dueSoon && now - row.lastNotifiedDue >= day) {
                post(notificationRepo, row.userId, "Due soon",
                    "\u201C${row.title}\u201D is ${DateUtils.dueLabel(row.dueDate, false)}.",
                    "DUE_SOON", row.title)
                taskRepo.setUserTaskNotified(row.id, now, row.lastNotifiedOverdue)
            } else if (overdue && now - row.lastNotifiedOverdue >= day) {
                post(notificationRepo, row.userId, "Overdue",
                    "\u201C${row.title}\u201D is overdue.",
                    "OVERDUE", row.title)
                taskRepo.setUserTaskNotified(row.id, row.lastNotifiedDue, now)
            }
        }
    }

    private fun checkPersonalTasks(
        personalRepo: PersonalTaskRepository,
        notificationRepo: NotificationRepository
    ) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        for (t in personalRepo.getAllForReminders()) {
            if (t.statusCompleted) continue
            val dueSoon = t.dueDate - now in 0..day
            val overdue = t.dueDate < now

            if (dueSoon && now - t.lastNotifiedDue >= day) {
                post(notificationRepo, t.userId, "Due soon",
                    "\u201C${t.title}\u201D is ${DateUtils.dueLabel(t.dueDate, false)}.",
                    "DUE_SOON", t.title)
                personalRepo.setNotified(t.id, now, t.lastNotifiedOverdue)
            } else if (overdue && now - t.lastNotifiedOverdue >= day) {
                post(notificationRepo, t.userId, "Overdue",
                    "\u201C${t.title}\u201D is overdue.",
                    "OVERDUE", t.title)
                personalRepo.setNotified(t.id, t.lastNotifiedDue, now)
            }
        }
    }
    private fun post(repo: NotificationRepository, userId: Long, title: String, message: String, type: String, taskTitle: String) {
        repo.insert(
            NotificationRecord(
                userId = userId,
                message = message,
                type = type,
                taskTitle = taskTitle
            )
        )
        notifySystem(userId, title, message)
    }

    private fun notifySystem(userId: Long, title: String, message: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, userId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(userId.toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Due date and overdue reminders" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val UNIQUE_NAME = "task_reminder_worker"
        private const val CHANNEL_ID = "task_reminders"
    }
}
