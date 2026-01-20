package com.example.autouchet.Views

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Reminder
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityRemindersBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RemindersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRemindersBinding
    private var currentCarId: Int = -1
    private var currentCarMileage: Int = 0
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private val channelId = "reminders_channel"
    private val notificationIdBase = 1000

    private val activeRemindersAdapter = ReminderAdapter(true)
    private val completedRemindersAdapter = ReminderAdapter(false)

    private val dateFormatDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateFormatInput = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private var remindersAlreadyScheduled = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_CODE_EXACT_ALARM = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNotificationSystem()
        setupUI()
        checkAndRequestPermissions()
        currentCarId = SharedPrefsHelper.getCurrentCarId(this)
        loadCarData()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешение на уведомления получено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Разрешение на уведомления отклонено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNotificationSystem() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
    }

    private fun setupUI() {
        binding.activeRemindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RemindersActivity)
            adapter = activeRemindersAdapter
        }

        binding.completedRemindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RemindersActivity)
            adapter = completedRemindersAdapter
        }

        val reminderTypes = listOf("По дате", "По пробегу", "Периодическое")
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            reminderTypes
        )
        binding.typeAutoCompleteTextView.setAdapter(typeAdapter)

        val currentDate = dateFormatInput.format(Date())
        binding.dateEditText.setText(currentDate)

        binding.dateEditText.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, day, 9, 0)
                }
                binding.dateEditText.setText(dateFormatInput.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadCarData() {
        if (currentCarId == -1) {
            showNoCarMessage()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            val car = database.carDao().getById(currentCarId)

            withContext(Dispatchers.Main) {
                if (car != null) {
                    currentCarMileage = car.currentMileage
                    loadReminders()
                } else {
                    showNoCarMessage()
                }
            }
        }
    }

    private fun loadReminders() {
        if (currentCarId == -1) {
            showNoCarMessage()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            val reminders = database.reminderDao().getAllByCar(currentCarId)

            val activeReminders = reminders.filter { !it.isCompleted }
            val completedReminders = reminders.filter { it.isCompleted }

            withContext(Dispatchers.Main) {
                activeRemindersAdapter.submitList(activeReminders)
                completedRemindersAdapter.submitList(completedReminders)

                if (activeReminders.isEmpty() && completedReminders.isEmpty()) {
                    binding.activeRemindersTitle.visibility = View.GONE
                    binding.activeRemindersRecyclerView.visibility = View.GONE
                    binding.completedRemindersTitle.visibility = View.GONE
                    binding.completedRemindersRecyclerView.visibility = View.GONE
                    binding.addReminderCard.visibility = View.VISIBLE
                } else {
                    binding.addReminderCard.visibility = View.VISIBLE

                    if (activeReminders.isNotEmpty()) {
                        binding.activeRemindersTitle.visibility = View.VISIBLE
                        binding.activeRemindersRecyclerView.visibility = View.VISIBLE
                    } else {
                        binding.activeRemindersTitle.visibility = View.GONE
                        binding.activeRemindersRecyclerView.visibility = View.GONE
                    }

                    if (completedReminders.isNotEmpty()) {
                        binding.completedRemindersTitle.visibility = View.VISIBLE
                        binding.completedRemindersRecyclerView.visibility = View.VISIBLE
                    } else {
                        binding.completedRemindersTitle.visibility = View.GONE
                        binding.completedRemindersRecyclerView.visibility = View.GONE
                    }
                }
            }

            if (!remindersAlreadyScheduled || checkForChanges(reminders)) {
                scheduleAllReminders(reminders)
                remindersAlreadyScheduled = true
            }
        }
    }

    private fun checkForChanges(currentReminders: List<Reminder>): Boolean {
        return true
    }

    private fun scheduleAllReminders(reminders: List<Reminder>) {
        val activeReminders = reminders.filter { !it.isCompleted }

        cancelAllScheduledNotifications()

        for (reminder in activeReminders) {
            when (reminder.type) {
                "date" -> {
                    reminder.targetDate?.let { targetDate ->
                        if (System.currentTimeMillis() < targetDate.time) {
                            scheduleDateReminder(reminder, targetDate, 7)
                            scheduleDateReminder(reminder, targetDate, 3)
                            scheduleDateReminder(reminder, targetDate, 1)
                            scheduleDateReminder(reminder, targetDate, 0)
                        }
                    }
                }
                "mileage" -> {
                    reminder.targetMileage?.let { targetMileage ->
                        val kmLeft = targetMileage - currentCarMileage
                        if (kmLeft > 0) {
                            scheduleMileageCheck(reminder)
                        }
                    }
                }
                "periodic" -> {
                    reminder.targetDate?.let { targetDate ->
                        if (System.currentTimeMillis() < targetDate.time) {
                            scheduleDateReminder(reminder, targetDate, 7)
                            scheduleDateReminder(reminder, targetDate, 0)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDateReminder(reminder: Reminder, targetDate: Date, daysBefore: Int) {
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

    private fun scheduleMileageCheck(reminder: Reminder) {
        val intent = Intent(this, com.example.autouchet.Receivers.ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_title", reminder.title)
            putExtra("type", "mileage_check")
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
        val checkTime = calendar.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    checkTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    checkTime,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                checkTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                checkTime,
                pendingIntent
            )
        }
    }

    private fun cancelAllScheduledNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            val reminders = database.reminderDao().getAllByCar(currentCarId)

            for (reminder in reminders) {
                cancelReminderNotifications(reminder.id)
            }
        }
    }

    private fun cancelReminderNotifications(reminderId: Int) {
        for (daysBefore in listOf(0, 1, 3, 7)) {
            val intent = Intent(this, com.example.autouchet.Receivers.ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                getUniqueRequestCode(reminderId, daysBefore),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.cancel()
        }

        val mileageIntent = Intent(this, com.example.autouchet.Receivers.ReminderReceiver::class.java)
        val mileagePendingIntent = PendingIntent.getBroadcast(
            this,
            reminderId * 2000,
            mileageIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        mileagePendingIntent?.cancel()
        notificationManager.cancel(reminderId + notificationIdBase)
    }

    private fun showNotification(reminderId: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(reminderId + notificationIdBase, notification)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.typeAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            updateFormVisibility(position)
        }

        binding.createReminderButton.setOnClickListener {
            createReminder()
        }
    }

    private fun updateFormVisibility(position: Int) {
        when (position) {
            0 -> {
                binding.dateLayout.visibility = View.VISIBLE
                binding.mileageLayout.visibility = View.GONE
                binding.periodLayout.visibility = View.GONE
            }
            1 -> {
                binding.dateLayout.visibility = View.GONE
                binding.mileageLayout.visibility = View.VISIBLE
                binding.periodLayout.visibility = View.GONE
                binding.mileageEditText.setText((currentCarMileage + 5000).toString())
            }
            2 -> {
                binding.dateLayout.visibility = View.VISIBLE
                binding.mileageLayout.visibility = View.GONE
                binding.periodLayout.visibility = View.VISIBLE
                binding.periodEditText.setText("12")
            }
        }
    }

    private fun createReminder() {
        if (currentCarId == -1) {
            Toast.makeText(this, "Сначала добавьте автомобиль", Toast.LENGTH_SHORT).show()
            return
        }

        val typeText = binding.typeAutoCompleteTextView.text.toString()
        val title = binding.titleEditText.text.toString().trim()

        if (typeText.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Заполните тип и название", Toast.LENGTH_SHORT).show()
            return
        }

        val type = when(typeText) {
            "По дате" -> "date"
            "По пробегу" -> "mileage"
            "Периодическое" -> "periodic"
            else -> "date"
        }

        var targetDate: Date? = null
        var targetMileage: Int? = null
        var periodMonths: Int? = null

        when(type) {
            "date", "periodic" -> {
                val dateText = binding.dateEditText.text.toString()
                if (dateText.isNotEmpty()) {
                    targetDate = dateFormatInput.parse(dateText)
                }
            }
            "mileage" -> {
                val mileageText = binding.mileageEditText.text.toString()
                if (mileageText.isNotEmpty()) {
                    targetMileage = mileageText.toIntOrNull()
                    if (targetMileage == null || targetMileage <= currentCarMileage) {
                        Toast.makeText(this, "Введите пробег больше текущего ($currentCarMileage км)", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
        }

        if (type == "periodic") {
            val periodText = binding.periodEditText.text.toString()
            periodMonths = periodText.toIntOrNull() ?: 12
        }

        val reminder = Reminder(
            carId = currentCarId,
            title = title,
            type = type,
            targetDate = targetDate,
            targetMileage = targetMileage,
            periodMonths = periodMonths,
            isCompleted = false
        )

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            val newId = database.reminderDao().insert(reminder).toInt()

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RemindersActivity, "Напоминание создано", Toast.LENGTH_SHORT).show()

                binding.titleEditText.text?.clear()
                binding.typeAutoCompleteTextView.text?.clear()
                binding.mileageEditText.text?.clear()
                binding.periodEditText.text?.clear()

                loadReminders()

                showNotification(
                    newId,
                    "Создано напоминание",
                    "$title\n${if (targetDate != null) "На ${dateFormatDisplay.format(targetDate)}" else "По пробегу"}"
                )
            }
        }
    }

    private fun markReminderAsCompleted(reminder: Reminder) {
        cancelReminderNotifications(reminder.id)

        val updatedReminder = reminder.copy(
            isCompleted = true,
            completedDate = Date(),
            completedMileage = currentCarMileage
        )

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            database.reminderDao().update(updatedReminder)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RemindersActivity, "Напоминание отмечено как выполненное", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
        }
    }

    private fun deleteReminder(reminder: Reminder) {
        cancelReminderNotifications(reminder.id)

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            database.reminderDao().delete(reminder)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RemindersActivity, "Напоминание удалено", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
        }
    }

    private fun postponeReminderByWeek(reminder: Reminder) {
        if (reminder.type != "date") return

        cancelReminderNotifications(reminder.id)

        val newDate = reminder.targetDate?.let {
            val calendar = Calendar.getInstance()
            calendar.time = it
            calendar.add(Calendar.DAY_OF_MONTH, 7)
            calendar.time
        }

        val updatedReminder = reminder.copy(targetDate = newDate)

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            database.reminderDao().update(updatedReminder)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RemindersActivity, "Напоминание отложено на неделю", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
        }
    }

    private fun showNoCarMessage() {
        binding.addReminderCard.visibility = View.GONE
        binding.activeRemindersTitle.visibility = View.GONE
        binding.activeRemindersRecyclerView.visibility = View.GONE
        binding.completedRemindersTitle.visibility = View.GONE
        binding.completedRemindersRecyclerView.visibility = View.GONE

        val message = "Сначала добавьте автомобиль в настройках"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    inner class ReminderAdapter(private val showActive: Boolean) :
        androidx.recyclerview.widget.ListAdapter<Reminder, ReminderViewHolder>(ReminderDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reminder, parent, false)
            return ReminderViewHolder(view, showActive)
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            val reminder = getItem(position)
            holder.bind(reminder)
        }
    }

    inner class ReminderViewHolder(itemView: View, private val showActive: Boolean) :
        RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val completeButton: TextView = itemView.findViewById(R.id.completeButton)
        private val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        private val postponeButton: TextView = itemView.findViewById(R.id.postponeButton)

        fun bind(reminder: Reminder) {
            titleTextView.text = reminder.title

            when(reminder.type) {
                "date" -> {
                    reminder.targetDate?.let {
                        dateTextView.text = "📅 ${dateFormatDisplay.format(it)}"

                        val today = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        val targetCalendar = Calendar.getInstance().apply {
                            time = it
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val diffDays = (targetCalendar.timeInMillis - today) / (1000 * 60 * 60 * 24)

                        statusTextView.text = when {
                            diffDays > 0 -> "Осталось $diffDays ${getDayWord(diffDays)}"
                            diffDays == 0L -> "Сегодня!"
                            else -> "Просрочено ${-diffDays} ${getDayWord(-diffDays)}"
                        }

                        statusTextView.setTextColor(
                            when {
                                diffDays > 0 -> getColor(R.color.green)
                                diffDays == 0L -> getColor(R.color.orange)
                                else -> getColor(R.color.red)
                            }
                        )
                    } ?: run {
                        dateTextView.text = "📅 Нет даты"
                        statusTextView.text = ""
                    }
                }
                "mileage" -> {
                    reminder.targetMileage?.let {
                        val kmLeft = it - currentCarMileage
                        dateTextView.text = "🚗 $it км"
                        statusTextView.text = if (kmLeft > 0) "Осталось $kmLeft км" else "Просрочено ${-kmLeft} км"
                        statusTextView.setTextColor(
                            if (kmLeft > 0) getColor(R.color.green) else getColor(R.color.red)
                        )
                    } ?: run {
                        dateTextView.text = "🚗 Нет пробега"
                        statusTextView.text = ""
                    }
                }
                "periodic" -> {
                    reminder.targetDate?.let {
                        dateTextView.text = "🔄 ${dateFormatDisplay.format(it)}"
                        reminder.periodMonths?.let { period ->
                            statusTextView.text = "Повтор каждые $period мес."
                            statusTextView.setTextColor(getColor(R.color.text_secondary))
                        }
                    } ?: run {
                        dateTextView.text = "🔄 Нет даты"
                        statusTextView.text = ""
                    }
                }
            }

            if (showActive) {
                completeButton.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE

                if (reminder.type == "date") {
                    postponeButton.visibility = View.VISIBLE
                } else {
                    postponeButton.visibility = View.GONE
                }

                completeButton.setOnClickListener {
                    markReminderAsCompleted(reminder)
                }

                deleteButton.setOnClickListener {
                    deleteReminder(reminder)
                }

                postponeButton.setOnClickListener {
                    postponeReminderByWeek(reminder)
                }
            } else {
                completeButton.visibility = View.GONE
                deleteButton.visibility = View.GONE
                postponeButton.visibility = View.GONE

                reminder.completedDate?.let {
                    statusTextView.text = "Выполнено: ${dateFormatDisplay.format(it)}"
                    statusTextView.setTextColor(getColor(R.color.text_secondary))
                }
            }
        }

        private fun getDayWord(days: Long): String {
            return when {
                days % 10 == 1L && days % 100 != 11L -> "день"
                days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
                else -> "дней"
            }
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem == newItem
        }
    }
}