package com.example.autouchet.Receivers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReminderReceiver : BroadcastReceiver() {
    private val channelId = "reminders_channel"
    private val dateFormatDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderReceiver", "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                // При перезагрузке телефона запускаем сервис для перепланирования напоминаний
                Log.d("ReminderReceiver", "Device rebooted, rescheduling reminders")
                rescheduleAllReminders(context)
            }
            "SHOW_REMINDER" -> {
                handleDateReminder(context, intent)
            }
            "CHECK_MILEAGE" -> {
                handleMileageReminder(context, intent)
            }
        }
    }

    private fun handleDateReminder(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", 0)
        val reminderTitle = intent.getStringExtra("reminder_title") ?: "Напоминание"
        val daysBefore = intent.getIntExtra("days_before", 0)
        val targetDate = Date(intent.getLongExtra("target_date", System.currentTimeMillis()))

        val message = when (daysBefore) {
            7 -> "Через неделю: $reminderTitle"
            3 -> "Через 3 дня: $reminderTitle"
            1 -> "Завтра: $reminderTitle"
            0 -> "Сегодня: $reminderTitle"
            else -> "Через $daysBefore ${getDayWord(daysBefore)}: $reminderTitle"
        }

        showNotification(context, reminderId, "Напоминание", message)

        // Если это периодическое напоминание и оно на сегодня, перепланируем на следующий период
        if (daysBefore == 0) {
            checkAndReschedulePeriodicReminder(context, reminderId)
        }
    }

    private fun checkAndReschedulePeriodicReminder(context: Context, reminderId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val reminder = database.reminderDao().getById(reminderId)

            if (reminder != null && reminder.type == "periodic" && !reminder.isCompleted) {
                reminder.targetDate?.let { targetDate ->
                    val calendar = Calendar.getInstance().apply {
                        time = targetDate
                    }

                    reminder.periodMonths?.let { periodMonths ->
                        calendar.add(Calendar.MONTH, periodMonths)
                        val newDate = calendar.time

                        // Обновляем дату в базе данных
                        val updatedReminder = reminder.copy(targetDate = newDate)
                        database.reminderDao().update(updatedReminder)

                        // Перепланируем уведомления для новой даты
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                        // Планируем уведомление за 7 дней
                        scheduleSingleReminder(context, alarmManager, updatedReminder, newDate, 7)

                        // Планируем уведомление на сам день
                        scheduleSingleReminder(context, alarmManager, updatedReminder, newDate, 0)
                    }
                }
            }
        }
    }

    private fun scheduleSingleReminder(
        context: Context,
        alarmManager: AlarmManager,
        reminder: com.example.autouchet.Models.Reminder,
        targetDate: Date,
        daysBefore: Int
    ) {
        val calendar = Calendar.getInstance().apply {
            time = targetDate
            add(Calendar.DAY_OF_MONTH, -daysBefore)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("days_before", daysBefore)
            putExtra("target_date", targetDate.time)
            action = "SHOW_REMINDER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getUniqueRequestCode(reminder.id, daysBefore),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun getUniqueRequestCode(reminderId: Int, daysBefore: Int): Int {
        return reminderId * 1000 + daysBefore
    }

    private fun handleMileageReminder(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", 0)
        val reminderTitle = intent.getStringExtra("reminder_title") ?: "Напоминание"

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val reminder = database.reminderDao().getById(reminderId)

            if (reminder != null && !reminder.isCompleted && reminder.type == "mileage") {
                val car = reminder.carId?.let { database.carDao().getById(it) }
                car?.let {
                    val kmLeft = reminder.targetMileage!! - it.currentMileage

                    if (kmLeft > 0) {
                        // Показываем уведомление если осталось мало км
                        val message = when {
                            kmLeft <= 100 -> "Осталось $kmLeft км: $reminderTitle"
                            kmLeft <= 500 -> "Осталось $kmLeft км: $reminderTitle"
                            kmLeft <= 1000 -> "Осталось $kmLeft км: $reminderTitle"
                            else -> null
                        }

                        if (message != null) {
                            showNotification(context, reminderId, "Напоминание по пробегу", message)
                        }

                        // Планируем следующую проверку на завтра
                        scheduleNextMileageCheck(context, reminderId, reminderTitle)
                    } else if (kmLeft <= 0) {
                        // Целевой пробег достигнут
                        showNotification(context, reminderId, "Напоминание по пробегу",
                            "Достигнут целевой пробег: $reminderTitle")

                        // Отмечаем как выполненное
                        val updatedReminder = reminder.copy(isCompleted = true)
                        database.reminderDao().update(updatedReminder)
                    }
                }
            }
        }
    }

    private fun scheduleNextMileageCheck(context: Context, reminderId: Int, title: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            action = "CHECK_MILEAGE"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId * 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val nextCheckTime = calendar.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextCheckTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextCheckTime,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextCheckTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                nextCheckTime,
                pendingIntent
            )
        }
    }

    private fun rescheduleAllReminders(context: Context) {
        Log.d("ReminderReceiver", "Starting to reschedule all reminders")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val currentCarId = SharedPrefsHelper.getCurrentCarId(context)

                if (currentCarId != -1) {
                    val reminders = database.reminderDao().getAllByCar(currentCarId)
                    val activeReminders = reminders.filter { !it.isCompleted }

                    Log.d("ReminderReceiver", "Found ${activeReminders.size} active reminders to reschedule")

                    // Запускаем сервис для перепланирования
                    val serviceIntent = Intent(context, com.example.autouchet.Services.ReminderService::class.java).apply {
                        putExtra("action", "reschedule")
                        putExtra("reminders_count", activeReminders.size)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Error rescheduling reminders", e)
            }
        }
    }

    private fun showNotification(context: Context, reminderId: Int, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Напоминания АвтоУчёт",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления о напоминаниях по автомобилю"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_car)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()

            notificationManager.notify(reminderId + 1000, notification)

            Log.d("ReminderReceiver", "Notification shown: $title - $message")
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Error showing notification", e)
        }
    }

    private fun getDayWord(days: Int): String {
        return when {
            days % 10 == 1 && days % 100 != 11 -> "день"
            days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
            else -> "дней"
        }
    }
}