package com.example.autouchet.Controllers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class NotificationManager(private val context: Context) {
    private val channelId = "autouchet_reminders"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания АвтоУчёт",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Напоминания о расходах и обслуживании авто"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Date().time.toInt(), notification)
    }

    suspend fun checkMileageReminders(carId: Int) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val reminders = database.reminderDao().getActiveByCar(carId)
            val car = database.carDao().getById(carId) ?: return@withContext

            for (reminder in reminders) {
                if (reminder.type == "mileage" && reminder.targetMileage != null) {
                    val kmLeft = reminder.targetMileage - car.currentMileage
                    if (kmLeft in 1..reminder.notifyKmBefore) {
                        showReminderNotification(
                            "Напоминание о пробеге",
                            "${reminder.title}. Осталось $kmLeft км"
                        )
                    }
                }
            }
        }
    }
}