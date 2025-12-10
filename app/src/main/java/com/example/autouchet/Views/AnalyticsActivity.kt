package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.AnalyticsController
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityAnalyticsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var analyticsController: AnalyticsController
    private lateinit var expenseController: ExpenseController
    private var currentCarId: Int = 1
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyticsController = AnalyticsController(this)
        expenseController = ExpenseController(this)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.exportButton.setOnClickListener {
            exportToCSV()
        }
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            // Загружаем текущий автомобиль
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val cars = database.carDao().getAll()
            if (cars.isNotEmpty()) {
                currentCarId = cars.first().id

                // Загружаем статистику
                val monthlyExpenses = getMonthlyExpensesSync(currentCarId)
                val previousMonthExpenses = getPreviousMonthExpensesSync(currentCarId)
                val expensePerKm = analyticsController.getExpensePerKm(currentCarId)
                val averageDailyExpense = analyticsController.getAverageDailyExpense(currentCarId)
                val categoryTotals = getCategoryTotalsSync(currentCarId)
                val seasonalComparison = analyticsController.getSeasonalComparison(currentCarId)

                withContext(Dispatchers.Main) {
                    updateStatistics(
                        monthlyExpenses,
                        previousMonthExpenses,
                        expensePerKm,
                        averageDailyExpense
                    )
                    updatePieChart(categoryTotals)
                    updateSeasonalComparison(seasonalComparison)
                }
            }
        }
    }

    private suspend fun getMonthlyExpensesSync(carId: Int): Double {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate) ?: 0.0
        }
    }

    private suspend fun getPreviousMonthExpensesSync(carId: Int): Double {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange(-1)
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate) ?: 0.0
        }
    }

    private suspend fun getCategoryTotalsSync(carId: Int): List<com.example.autouchet.Models.CategoryTotal> {
        return withContext(Dispatchers.IO) {
            val (startDate, endDate) = getMonthRange()
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getCategoryTotals(carId, startDate, endDate)
        }
    }

    private fun getMonthRange(monthOffset: Int = 0): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endDate = calendar.timeInMillis

        return Pair(startDate, endDate)
    }

    private fun updateStatistics(
        monthlyExpenses: Double,
        previousMonthExpenses: Double,
        expensePerKm: Double,
        averageDailyExpense: Double
    ) {
        binding.totalExpensesTextView.text = currencyFormat.format(monthlyExpenses)
        binding.averageDailyTextView.text = currencyFormat.format(averageDailyExpense)
        binding.expensePerKmTextView.text = "${String.format("%.1f", expensePerKm)} ₽"

        // Расчет процента изменения
        if (previousMonthExpenses > 0) {
            val percentChange = ((monthlyExpenses - previousMonthExpenses) / previousMonthExpenses * 100).toInt()
            binding.monthComparisonTextView.text = if (percentChange > 0) {
                "СЕНТЯБРЬ: НА $percentChange% БОЛЬШЕ чем в августе"
            } else {
                "СЕНТЯБРЬ: НА ${-percentChange}% МЕНЬШЕ чем в августе"
            }
        }
    }

    private fun updatePieChart(categoryTotals: List<com.example.autouchet.Models.CategoryTotal>) {
        if (categoryTotals.isEmpty()) {
            binding.pieChart.visibility = android.view.View.GONE
            return
        }

        val entries = mutableListOf<com.github.mikephil.charting.data.PieEntry>()
        val colors = mutableListOf<Int>()

        for (categoryTotal in categoryTotals) {
            entries.add(com.github.mikephil.charting.data.PieEntry(categoryTotal.total.toFloat(), categoryTotal.category))
            colors.add(getCategoryColor(categoryTotal.category))
        }

        val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = android.graphics.Color.WHITE
        }

        val pieData = com.github.mikephil.charting.data.PieData(dataSet)
        binding.pieChart.apply {
            data = pieData
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 40f
            setEntryLabelColor(android.graphics.Color.BLACK)
            animateY(1000)
            invalidate()
        }
    }

    private fun getCategoryColor(category: String): Int {
        return when(category) {
            "Топливо" -> android.graphics.Color.parseColor("#4CAF50")
            "Обслуживание" -> android.graphics.Color.parseColor("#2196F3")
            "Шины" -> android.graphics.Color.parseColor("#FF9800")
            "Налоги" -> android.graphics.Color.parseColor("#F44336")
            "Страховка" -> android.graphics.Color.parseColor("#9C27B0")
            "Ремонт" -> android.graphics.Color.parseColor("#795548")
            "Мойка" -> android.graphics.Color.parseColor("#00BCD4")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
    }

    private fun updateSeasonalComparison(comparison: AnalyticsController.SeasonalComparison) {
        binding.winterAverageTextView.text = currencyFormat.format(comparison.winterAverage)
        binding.summerAverageTextView.text = currencyFormat.format(comparison.summerAverage)

        if (comparison.percentDifference > 0) {
            binding.seasonComparisonTextView.text =
                "+${String.format("%.0f", comparison.percentDifference)}% к летнему периоду"
        }
    }

    private fun exportToCSV() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val expenses = database.expenseDao().getRecentByCar(currentCarId, 1000)

            if (expenses.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val csv = StringBuilder()

                // Заголовок
                csv.append("Дата;Категория;Сумма;Пробег;Комментарий\n")

                // Данные
                for (expense in expenses) {
                    csv.append("${dateFormat.format(expense.date)};")
                    csv.append("${expense.category};")
                    csv.append("${expense.amount};")
                    csv.append("${expense.mileage};")
                    csv.append("${expense.comment.replace(";", ",")}\n")
                }

                // Сохраняем файл
                val fileName = "autouchet_export_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
                val file = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ).resolve(fileName)

                file.writeText(csv.toString())

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@AnalyticsActivity,
                        "Файл сохранён в Загрузки",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}