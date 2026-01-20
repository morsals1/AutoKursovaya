package com.example.autouchet.Services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Reminder
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.Views.RemindersActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class ReminderService : Service() {
    private val channelId = "reminders_service_channel"
    private val notificationId = 9999

    override fun onCreate() {
        super.onCreate()
        Log.d("ReminderService", "Service created")
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ReminderService", "Service started with action: ${intent?.action}")

        when (intent?.action) {
            "RESCHEDULE_ALL" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    rescheduleAllReminders()
                }
            }
            else -> {
                CoroutineScope(Dispatchers.IO).launch {
                    rescheduleAllReminders()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ReminderService", "Service destroyed")
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Служба напоминаний",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Служба для работы напоминаний в фоне"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("АвтоУчёт")
            .setContentText("Служба напоминаний активна")
            .setSmallIcon(R.drawable.ic_car)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(notificationId, notification)
    }

    private suspend fun rescheduleAllReminders() {
        Log.d("ReminderService", "Rescheduling all reminders")

        try {
            val database = AppDatabase.getDatabase(this)
            val currentCarId = SharedPrefsHelper.getCurrentCarId(this)

            if (currentCarId != -1) {
                val reminders = database.reminderDao().getAllByCar(currentCarId)
                val activeReminders = reminders.filter { !it.isCompleted }

                Log.d("ReminderService", "Found ${activeReminders.size} active reminders")
                scheduleReminders(activeReminders)

                Log.d("ReminderService", "All reminders rescheduled")
            }
        } catch (e: Exception) {
            Log.e("ReminderService", "Error rescheduling reminders", e)
        } finally {
            stopSelf()
        }
    }

    private suspend fun scheduleReminders(reminders: List<Reminder>) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val currentCarMileage = getCurrentCarMileage()

        for (reminder in reminders) {
            when (reminder.type) {
                "date" -> {
                    reminder.targetDate?.let { targetDate ->
                        if (System.currentTimeMillis() < targetDate.time) {
                            scheduleDateReminder(reminder, targetDate, 7, alarmManager)
                            scheduleDateReminder(reminder, targetDate, 3, alarmManager)
                            scheduleDateReminder(reminder, targetDate, 1, alarmManager)
                            scheduleDateReminder(reminder, targetDate, 0, alarmManager)
                        }
                    }
                }
                "mileage" -> {
                    reminder.targetMileage?.let { targetMileage ->
                        val kmLeft = targetMileage - currentCarMileage
                        if (kmLeft > 0) {
                            scheduleMileageCheck(reminder, alarmManager)
                        }
                    }
                }
                "periodic" -> {
                    reminder.targetDate?.let { targetDate ->
                        if (System.currentTimeMillis() < targetDate.time) {
                            scheduleDateReminder(reminder, targetDate, 7, alarmManager)
                            scheduleDateReminder(reminder, targetDate, 0, alarmManager)
                        }
                    }
                }
            }
        }
    }

    private suspend fun getCurrentCarMileage(): Int {
        return try {
            val currentCarId = SharedPrefsHelper.getCurrentCarId(this)
            if (currentCarId != -1) {
                val database = AppDatabase.getDatabase(this)
                database.carDao().getById(currentCarId)?.currentMileage ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun scheduleDateReminder(reminder: Reminder, targetDate: Date, daysBefore: Int, alarmManager: AlarmManager) {
        val calendar = Calendar.getInstance().apply {
            time = targetDate
            add(Calendar.DAY_OF_MONTH, -daysBefore)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(this, com.example.autouchet.Receivers.ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("days_before", daysBefore)
            putExtra("target_date", targetDate.time)
            action = "SHOW_REMINDER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
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

    private fun scheduleMileageCheck(reminder: Reminder, alarmManager: AlarmManager) {
        val intent = Intent(this, com.example.autouchet.Receivers.ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            action = "CHECK_MILEAGE"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id * 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

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
}