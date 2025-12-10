package com.example.autouchet.Views

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Controllers.NotificationManager
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Car
import com.example.autouchet.Models.Expense
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // ДОБАВЬ ЭТОТ ИМПОРТ
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var expenseController: ExpenseController
    private lateinit var notificationManager: NotificationManager
    private var currentCar: Car? = null
    private val expenseAdapter = ExpenseAdapter()
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseController = ExpenseController(this)
        notificationManager = NotificationManager(this)

        setupUI()
        loadData()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        checkReminders()
    }

    private fun setupUI() {
        // Настройка RecyclerView
        binding.recentExpensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = expenseAdapter
        }

        // Настройка нижнего меню
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, AddExpenseActivity::class.java))
                    true
                }
                R.id.nav_reminders -> {
                    startActivity(Intent(this, RemindersActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, CarSettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@MainActivity)
            val cars = database.carDao().getAll()

            withContext(Dispatchers.Main) {
                if (cars.isNotEmpty()) {
                    currentCar = cars.first()
                    updateCarInfo()
                    loadExpenses()
                    loadStatistics()
                } else {
                    // Если нет автомобилей, предлагаем добавить
                    binding.carInfoCard.visibility = View.GONE
                    binding.monthStatsCard.visibility = View.GONE
                    binding.recentExpensesCard.visibility = View.GONE
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.addFirstCarButton.setOnClickListener {
                        startActivity(Intent(this@MainActivity, CarSettingsActivity::class.java))
                    }
                }
            }
        }
    }

    private fun updateCarInfo() {
        currentCar?.let { car ->
            binding.carNameTextView.text = car.getFullName()
            binding.currentMileageTextView.text = "Текущий пробег: ${car.currentMileage} км"
        }
    }

    private fun loadExpenses() {
        currentCar?.let { car ->
            expenseController.getRecentExpenses(car.id) { expenses ->
                expenseAdapter.submitList(expenses)
                if (expenses.isEmpty()) {
                    binding.recentExpensesTitle.visibility = View.GONE
                } else {
                    binding.recentExpensesTitle.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadStatistics() {
        currentCar?.let { car ->
            CoroutineScope(Dispatchers.IO).launch {
                val monthlyExpenses = expenseController.getMonthlyExpensesSync(car.id)
                val previousMonthExpenses = expenseController.getPreviousMonthExpensesSync(car.id)

                withContext(Dispatchers.Main) {
                    binding.totalExpensesTextView.text = currencyFormat.format(monthlyExpenses ?: 0.0)

                    // Расчет экономии
                    val economy = (previousMonthExpenses ?: 0.0) - (monthlyExpenses ?: 0.0)
                    binding.economyTextView.text = currencyFormat.format(economy)
                    if (economy > 0) {
                        binding.economyTextView.setTextColor(getColor(R.color.green))
                    } else {
                        binding.economyTextView.setTextColor(getColor(R.color.red))
                    }

                    // Процент изменения
                    if ((previousMonthExpenses ?: 0.0) > 0) {
                        val percentChange = (((monthlyExpenses ?: 0.0) - (previousMonthExpenses ?: 0.0)) / (previousMonthExpenses ?: 1.0) * 100).toInt()
                        binding.monthComparisonTextView.text = if (percentChange > 0) {
                            "▲$percentChange% к прошлому месяцу"
                        } else {
                            "▼${-percentChange}% к прошлому месяцу"
                        }
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.addExpenseFab.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }

        binding.showAllExpensesButton.setOnClickListener {
            // В полной версии здесь будет переход к полному списку
        }
    }

    private fun checkReminders() {
        currentCar?.let { car ->
            CoroutineScope(Dispatchers.IO).launch {
                // notificationManager.checkMileageReminders(car.id) // Пока закомментируй
            }
        }
    }

    inner class ExpenseAdapter : androidx.recyclerview.widget.ListAdapter<Expense, ExpenseViewHolder>(
        ExpenseDiffCallback()
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            return ExpenseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
            val expense = getItem(position)
            holder.bind(expense)
        }
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(expense: Expense) {
            itemView.findViewById<TextView>(R.id.dateTextView).text =
                SimpleDateFormat("dd MMM", Locale.getDefault()).format(expense.date)
            itemView.findViewById<TextView>(R.id.categoryTextView).text =
                "${expense.getCategoryIcon()} ${expense.category}"
            itemView.findViewById<TextView>(R.id.amountTextView).text =
                currencyFormat.format(expense.amount)

            if (expense.comment.isNotEmpty()) {
                itemView.findViewById<TextView>(R.id.commentTextView).text = expense.comment
                itemView.findViewById<TextView>(R.id.commentTextView).visibility = View.VISIBLE
            } else {
                itemView.findViewById<TextView>(R.id.commentTextView).visibility = View.GONE
            }

            itemView.setOnClickListener {
                // В полной версии здесь будет переход к деталям расхода
            }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem == newItem
        }
    }
}