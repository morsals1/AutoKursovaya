package com.example.autouchet.Views

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Reminder
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityRemindersBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RemindersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRemindersBinding
    private var currentCarId: Int = 1
    private val remindersAdapter = RemindersAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadReminders()
        setupClickListeners()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // Настройка RecyclerView
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RemindersActivity)
            adapter = remindersAdapter
        }

        // Настройка выпадающих списков
        val reminderTypes = listOf("По дате", "По пробегу", "Периодическое")
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            reminderTypes
        )
        binding.typeAutoCompleteTextView.setAdapter(typeAdapter)

        val reminderEvents = listOf(
            "Замена масла",
            "Замена фильтров",
            "Замена шин",
            "Страховка ОСАГО",
            "Техосмотр",
            "Транспортный налог",
            "Плановое ТО"
        )
        val eventAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            reminderEvents
        )
        binding.eventAutoCompleteTextView.setAdapter(eventAdapter)

        // Установка текущей даты
        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        binding.dateEditText.setText(currentDate)
    }

    private fun loadReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            val cars = database.carDao().getAll()
            if (cars.isNotEmpty()) {
                currentCarId = cars.first().id
                val reminders = database.reminderDao().getAllByCar(currentCarId)

                withContext(Dispatchers.Main) {
                    remindersAdapter.submitList(reminders)

                    // Разделяем на активные и выполненные
                    val activeReminders = reminders.filter { !it.isCompleted }
                    val completedReminders = reminders.filter { it.isCompleted }

                    if (activeReminders.isNotEmpty()) {
                        binding.upcomingRemindersTitle.visibility = android.view.View.VISIBLE
                        // В реальном приложении здесь будет отдельный адаптер для активных
                    }

                    if (completedReminders.isNotEmpty()) {
                        binding.allRemindersTitle.visibility = android.view.View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.createReminderButton.setOnClickListener {
            createReminder()
        }

        binding.typeAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedType = listOf("date", "mileage", "periodic")[position]

            when (selectedType) {
                "date" -> {
                    binding.dateLayout.visibility = android.view.View.VISIBLE
                    binding.mileageLayout.visibility = android.view.View.GONE
                    binding.periodLayout.visibility = android.view.View.GONE
                }
                "mileage" -> {
                    binding.dateLayout.visibility = android.view.View.GONE
                    binding.mileageLayout.visibility = android.view.View.VISIBLE
                    binding.periodLayout.visibility = android.view.View.GONE
                }
                "periodic" -> {
                    binding.dateLayout.visibility = android.view.View.VISIBLE
                    binding.mileageLayout.visibility = android.view.View.GONE
                    binding.periodLayout.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun createReminder() {
        val event = binding.eventAutoCompleteTextView.text.toString()
        val typeText = binding.typeAutoCompleteTextView.text.toString()

        if (event.isEmpty() || typeText.isEmpty()) {
            android.widget.Toast.makeText(this, "Заполните все поля", android.widget.Toast.LENGTH_SHORT).show()
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
            "date" -> {
                val dateText = binding.dateEditText.text.toString()
                if (dateText.isNotEmpty()) {
                    targetDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText)
                }
            }
            "mileage" -> {
                val mileageText = binding.mileageEditText.text.toString()
                targetMileage = mileageText.toIntOrNull()
            }
            "periodic" -> {
                val periodText = binding.periodEditText.text.toString()
                periodMonths = periodText.toIntOrNull()
                val dateText = binding.dateEditText.text.toString()
                if (dateText.isNotEmpty()) {
                    targetDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText)
                }
            }
        }

        val reminder = Reminder(
            carId = currentCarId,
            title = event,
            type = type,
            targetDate = targetDate,
            targetMileage = targetMileage,
            periodMonths = periodMonths
        )

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@RemindersActivity)
            database.reminderDao().insert(reminder)

            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@RemindersActivity,
                    "Напоминание создано",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                loadReminders()

                // Очищаем форму
                binding.eventAutoCompleteTextView.text.clear()
                binding.typeAutoCompleteTextView.text.clear()
            }
        }
    }

    inner class RemindersAdapter : androidx.recyclerview.widget.ListAdapter<Reminder, ReminderViewHolder>(
        ReminderDiffCallback()
    ) {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ReminderViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reminder, parent, false)
            return ReminderViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            val reminder = getItem(position)
            holder.bind(reminder)
        }
    }

    inner class ReminderViewHolder(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

        fun bind(reminder: Reminder) {
            itemView.findViewById<android.widget.TextView>(R.id.titleTextView).text = reminder.title

            val status = when(reminder.type) {
                "date" -> {
                    reminder.targetDate?.let {
                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                        "📅 $dateStr"
                    } ?: "Нет даты"
                }
                "mileage" -> {
                    reminder.targetMileage?.let {
                        "🚗 $it км"
                    } ?: "Нет пробега"
                }
                else -> "Периодическое"
            }

            itemView.findViewById<android.widget.TextView>(R.id.statusTextView).text = status

            if (reminder.isCompleted) {
                itemView.findViewById<android.widget.TextView>(R.id.completedTextView).visibility =
                    android.view.View.VISIBLE
            } else {
                itemView.findViewById<android.widget.TextView>(R.id.completedTextView).visibility =
                    android.view.View.GONE
            }

            itemView.setOnClickListener {
                // В полной версии здесь будет изменение статуса
            }
        }
    }

    class ReminderDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
            return oldItem == newItem
        }
    }
}