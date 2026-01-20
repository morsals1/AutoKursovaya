package com.example.autouchet.Views

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.AnalyticsController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.CategoryTotal
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityAnalyticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var analyticsController: AnalyticsController
    private var currentCarId: Int = -1
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    private val monthNames = listOf(
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
        "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    )

    private val periodOptions = listOf(
        "Текущий месяц",
        "Прошлый месяц",
        "Последние 3 месяца",
        "Последние 6 месяцев",
        "Текущий год",
        "Прошлый год"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyticsController = AnalyticsController(this)
        currentCarId = SharedPrefsHelper.getCurrentCarId(this)

        setupUI()
        setupPeriodSelector()

        initTitle()

        if (currentCarId != -1) {
            loadData()
        } else {
            showNoCarMessage()
        }
    }

    private fun initTitle() {
        val currentMonth = SimpleDateFormat("LLLL yyyy", Locale("ru")).format(Date())
        binding.toolbarTitle.text = "АНАЛИТИКА • $currentMonth"
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupPeriodSelector() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, periodOptions)
        binding.periodAutoCompleteTextView.setAdapter(adapter)
        binding.periodAutoCompleteTextView.setText(periodOptions[0], false)

        updatePeriodTitle(periodOptions[0])

        binding.periodAutoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedPeriod = parent.getItemAtPosition(position) as String
            updatePeriodTitle(selectedPeriod)
            loadDataForPeriod(selectedPeriod)
        }
    }

    private fun updatePeriodTitle(period: String) {
        val calendar = Calendar.getInstance()

        when (period) {
            "Текущий месяц" -> {
                val currentMonth = SimpleDateFormat("LLLL yyyy", Locale("ru")).format(Date())
                binding.toolbarTitle.text = "АНАЛИТИКА • $currentMonth"
            }
            "Прошлый месяц" -> {
                calendar.add(Calendar.MONTH, -1)
                val lastMonth = SimpleDateFormat("LLLL yyyy", Locale("ru")).format(calendar.time)
                binding.toolbarTitle.text = "АНАЛИТИКА • $lastMonth"
            }
            "Последние 3 месяца" -> {
                calendar.add(Calendar.MONTH, -2)
                val startMonth = SimpleDateFormat("MMM", Locale("ru")).format(calendar.time)
                calendar.time = Date()
                val endMonth = SimpleDateFormat("MMM yyyy", Locale("ru")).format(calendar.time)
                binding.toolbarTitle.text = "АНАЛИТИКА • $startMonth - $endMonth"
            }
            "Последние 6 месяцев" -> {
                calendar.add(Calendar.MONTH, -5)
                val startMonth = SimpleDateFormat("MMM", Locale("ru")).format(calendar.time)
                calendar.time = Date()
                val endMonth = SimpleDateFormat("MMM yyyy", Locale("ru")).format(calendar.time)
                binding.toolbarTitle.text = "АНАЛИТИКА • $startMonth - $endMonth"
            }
            "Текущий год" -> {
                val currentYear = calendar.get(Calendar.YEAR)
                binding.toolbarTitle.text = "АНАЛИТИКА • $currentYear год"
            }
            "Прошлый год" -> {
                val lastYear = calendar.get(Calendar.YEAR) - 1
                binding.toolbarTitle.text = "АНАЛИТИКА • $lastYear год"
            }
            else -> binding.toolbarTitle.text = "АНАЛИТИКА • $period"
        }
    }

    private fun loadData() {
        loadDataForPeriod("Текущий месяц")
    }

    private fun loadDataForPeriod(period: String) {
        if (currentCarId == -1) {
            showNoCarMessage()
            return
        }

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (startDate, endDate) = getDateRangeForPeriod(period)
                val monthlyExpenses = getExpensesForPeriodSync(currentCarId, startDate, endDate)
                val previousPeriodExpenses = getPreviousPeriodExpensesSync(period)
                val expensePerKm = calculateExpensePerKm(currentCarId, startDate, endDate)
                val averageDailyExpense = calculateAverageDailyExpense(monthlyExpenses, startDate, endDate)
                val categoryTotals = getCategoryTotalsForPeriodSync(currentCarId, startDate, endDate)
                val monthlyTrends = getMonthlyTrendsSync(currentCarId, period)
                val seasonalComparison = analyticsController.getSeasonalComparison(currentCarId)

                withContext(Dispatchers.Main) {
                    updateStatistics(
                        monthlyExpenses,
                        previousPeriodExpenses,
                        expensePerKm,
                        averageDailyExpense,
                        period,
                        categoryTotals
                    )
                    updatePieChart(categoryTotals)
                    updateBarChart(monthlyTrends)
                    updateSeasonalComparison(seasonalComparison)
                    showLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError()
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun showNoCarMessage() {
        binding.statisticsCard.visibility = android.view.View.VISIBLE
        binding.totalExpensesTextView.text = "• Сначала добавьте автомобиль"
        binding.averageDailyTextView.text = "• В настройках"
        binding.expensePerKmTextView.text = ""

        binding.pieChartCard.visibility = android.view.View.GONE
        binding.barChartCard.visibility = android.view.View.GONE
        binding.seasonalCard.visibility = android.view.View.GONE
        binding.loadingProgressBar.visibility = ProgressBar.GONE
    }

    private fun showError() {
        binding.statisticsCard.visibility = android.view.View.VISIBLE
        binding.totalExpensesTextView.text = "• Ошибка загрузки данных"
        binding.averageDailyTextView.text = "• Средний день: 0 ₽"
        binding.expensePerKmTextView.text = "• Расход на 1 км: 0 ₽"

        binding.pieChartCard.visibility = android.view.View.GONE
        binding.barChartCard.visibility = android.view.View.GONE
        binding.seasonalCard.visibility = android.view.View.GONE
    }

    private suspend fun getExpensesForPeriodSync(carId: Int, startDate: Long, endDate: Long): Double {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate) ?: 0.0
        }
    }

    private suspend fun getPreviousPeriodExpensesSync(period: String): Double {
        return withContext(Dispatchers.IO) {
            val (prevStartDate, prevEndDate) = getPreviousDateRangeForPeriod(period)
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getTotalByDateRange(currentCarId, prevStartDate, prevEndDate) ?: 0.0
        }
    }

    private suspend fun calculateExpensePerKm(carId: Int, startDate: Long, endDate: Long): Double {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val expenses = database.expenseDao().getByDateRange(carId, startDate, endDate)

            if (expenses.isEmpty()) return@withContext 0.0

            val totalAmount = expenses.sumByDouble { it.amount }
            val mileageEntries = expenses.filter { it.mileage > 0 }

            if (mileageEntries.isEmpty()) return@withContext 0.0

            val startMileage = mileageEntries.minByOrNull { it.mileage }?.mileage ?: 0
            val endMileage = mileageEntries.maxByOrNull { it.mileage }?.mileage ?: 0
            val totalKm = endMileage - startMileage

            if (totalKm > 0) totalAmount / totalKm else 0.0
        }
    }

    private fun calculateAverageDailyExpense(totalAmount: Double, startDate: Long, endDate: Long): Double {
        val days = ((endDate - startDate) / (1000 * 60 * 60 * 24)).toInt() + 1
        return if (days > 0) totalAmount / days else 0.0
    }

    private suspend fun getCategoryTotalsForPeriodSync(carId: Int, startDate: Long, endDate: Long): List<CategoryTotal> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getCategoryTotals(carId, startDate, endDate)
        }
    }

    private suspend fun getMonthlyTrendsSync(carId: Int, period: String): List<MonthlyData> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)

            val monthsData = mutableListOf<MonthlyData>()

            when (period) {
                "Текущий месяц", "Прошлый месяц" -> {
                    for (i in 5 downTo 0) {
                        calendar.time = Date()
                        calendar.add(Calendar.MONTH, -i)
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)

                        val monthStart = getMonthStartTime(year, month)
                        val monthEnd = getMonthEndTime(year, month)

                        val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                        monthsData.add(MonthlyData(year, month, total))
                    }
                }
                "Последние 3 месяца" -> {
                    for (i in 2 downTo 0) {
                        calendar.time = Date()
                        calendar.add(Calendar.MONTH, -i)
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)

                        val monthStart = getMonthStartTime(year, month)
                        val monthEnd = getMonthEndTime(year, month)

                        val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                        monthsData.add(MonthlyData(year, month, total))
                    }
                }
                "Текущий год" -> {
                    for (month in 0..11) {
                        val monthStart = getMonthStartTime(currentYear, month)
                        val monthEnd = getMonthEndTime(currentYear, month)

                        val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                        monthsData.add(MonthlyData(currentYear, month, total))
                    }
                }
                "Прошлый год" -> {
                    val lastYear = currentYear - 1
                    for (month in 0..11) {
                        val monthStart = getMonthStartTime(lastYear, month)
                        val monthEnd = getMonthEndTime(lastYear, month)

                        val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                        monthsData.add(MonthlyData(lastYear, month, total))
                    }
                }
                else -> {
                    for (i in 5 downTo 0) {
                        calendar.time = Date()
                        calendar.add(Calendar.MONTH, -i)
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)

                        val monthStart = getMonthStartTime(year, month)
                        val monthEnd = getMonthEndTime(year, month)

                        val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                        monthsData.add(MonthlyData(year, month, total))
                    }
                }
            }

            monthsData
        }
    }

    private fun getDateRangeForPeriod(period: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        return when (period) {
            "Текущий месяц" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Прошлый месяц" -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Последние 3 месяца" -> {
                calendar.add(Calendar.MONTH, -2)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                val endCalendar = Calendar.getInstance()
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
                val endDate = endCalendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Последние 6 месяцев" -> {
                calendar.add(Calendar.MONTH, -5)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                val endCalendar = Calendar.getInstance()
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
                val endDate = endCalendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Текущий год" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                val endCalendar = Calendar.getInstance()
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
                val endDate = endCalendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Прошлый год" -> {
                calendar.add(Calendar.YEAR, -1)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            else -> {
                getDateRangeForPeriod("Текущий месяц")
            }
        }
    }

    private fun getPreviousDateRangeForPeriod(period: String): Pair<Long, Long> {
        return when (period) {
            "Текущий месяц" -> getDateRangeForPeriod("Прошлый месяц")
            "Прошлый месяц" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -2)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Последние 3 месяца" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -5)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 3)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            "Последние 6 месяцев" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -11)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 6)
                calendar.add(Calendar.MILLISECOND, -1)
                val endDate = calendar.timeInMillis

                Pair(startDate, endDate)
            }
            else -> getDateRangeForPeriod("Прошлый месяц")
        }
    }

    private fun getMonthStartTime(year: Int, month: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getMonthEndTime(year: Int, month: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun updateStatistics(
        monthlyExpenses: Double,
        previousPeriodExpenses: Double,
        expensePerKm: Double,
        averageDailyExpense: Double,
        period: String,
        categoryTotals: List<CategoryTotal>
    ) {
        binding.statisticsCard.visibility = android.view.View.VISIBLE
        binding.totalExpensesTextView.text = "• Всего расходов: ${currencyFormat.format(monthlyExpenses)}"
        binding.averageDailyTextView.text = "• Средний день: ${currencyFormat.format(averageDailyExpense)}"
        binding.expensePerKmTextView.text = "• Расход на 1 км: ${String.format("%.1f", expensePerKm)} ₽"

        if (previousPeriodExpenses > 0 && monthlyExpenses > 0) {
            val percentChange = ((monthlyExpenses - previousPeriodExpenses) / previousPeriodExpenses * 100).toInt()
            val comparisonText = when {
                percentChange > 0 -> "НА ${percentChange}% БОЛЬШЕ чем в предыдущем периоде"
                percentChange < 0 -> "НА ${-percentChange}% МЕНЬШЕ чем в предыдущем периоде"
                else -> "БЕЗ ИЗМЕНЕНИЙ по сравнению с предыдущим периодом"
            }
            binding.monthComparisonTextView.text = comparisonText
            binding.monthComparisonTextView.setTextColor(
                if (percentChange > 0) getColor(R.color.red)
                else if (percentChange < 0) getColor(R.color.green)
                else getColor(R.color.text_secondary)
            )
        } else {
            binding.monthComparisonTextView.text = "Нет данных для сравнения"
            binding.monthComparisonTextView.setTextColor(getColor(R.color.text_secondary))
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.barChartTitle.text = "ГРАФИК ПО МЕСЯЦАМ ($currentYear)"
    }

    private fun updatePieChart(categoryTotals: List<CategoryTotal>) {
        val hasData = categoryTotals.isNotEmpty() && categoryTotals.sumByDouble { it.total } > 0

        if (hasData) {
            binding.pieChartCard.visibility = android.view.View.VISIBLE
            binding.pieChartTitle.text = "РАСПРЕДЕЛЕНИЕ ПО КАТЕГОРИЯМ"
            binding.pieChart.visibility = android.view.View.VISIBLE

            val entries = mutableListOf<com.github.mikephil.charting.data.PieEntry>()
            val colors = mutableListOf<Int>()

            for (categoryTotal in categoryTotals) {
                entries.add(com.github.mikephil.charting.data.PieEntry(categoryTotal.total.toFloat(), categoryTotal.category))
                colors.add(getCategoryColor(categoryTotal.category))
            }

            val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "").apply {
                this.colors = colors
                valueTextSize = 8f
                valueTextColor = android.graphics.Color.BLACK
                yValuePosition = com.github.mikephil.charting.data.PieDataSet.ValuePosition.OUTSIDE_SLICE
            }

            val pieData = com.github.mikephil.charting.data.PieData(dataSet)
            pieData.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return currencyFormat.format(value.toDouble())
                }
            })

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

            updateCategoryLegend(categoryTotals)
        } else {
            binding.pieChartCard.visibility = android.view.View.GONE
        }
    }

    private fun updateCategoryLegend(categoryTotals: List<CategoryTotal>) {
        binding.categoryLegendLayout.removeAllViews()

        val totalAmount = categoryTotals.sumByDouble { it.total }

        for (categoryTotal in categoryTotals.sortedByDescending { it.total }) {
            val percentage = (categoryTotal.total / totalAmount * 100).toInt()

            val legendItem = LayoutInflater.from(this).inflate(R.layout.legend_item, null)
            val colorView = legendItem.findViewById<android.view.View>(R.id.colorView)
            val categoryTextView = legendItem.findViewById<TextView>(R.id.categoryTextView)
            val amountTextView = legendItem.findViewById<TextView>(R.id.amountTextView)
            val percentageTextView = legendItem.findViewById<TextView>(R.id.percentageTextView)

            colorView.setBackgroundColor(getCategoryColor(categoryTotal.category))
            categoryTextView.text = categoryTotal.category
            amountTextView.text = currencyFormat.format(categoryTotal.total)
            percentageTextView.text = "$percentage%"

            binding.categoryLegendLayout.addView(legendItem)
        }
    }

    private fun updateBarChart(monthlyTrends: List<MonthlyData>) {
        val hasData = monthlyTrends.isNotEmpty() && monthlyTrends.any { it.total > 0 }

        if (hasData) {
            binding.barChartCard.visibility = android.view.View.VISIBLE

            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()

            for ((index, monthlyData) in monthlyTrends.withIndex()) {
                entries.add(BarEntry(index.toFloat(), monthlyData.total.toFloat()))
                val label = if (monthlyTrends.size <= 6) {
                    "${monthNames[monthlyData.month]}\n${monthlyData.year}"
                } else {
                    monthNames[monthlyData.month]
                }
                labels.add(label)
            }

            val dataSet = BarDataSet(entries, "Расходы по месяцам")
            dataSet.color = getColor(R.color.blue)
            dataSet.valueTextSize = 8f

            val barData = BarData(dataSet)
            barData.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1000) {
                        "${(value / 1000).toInt()}к"
                    } else {
                        value.toInt().toString()
                    }
                }
            })

            binding.barChart.apply {
                data = barData
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index in labels.indices) labels[index] else ""
                        }
                    }
                    textSize = 8f
                    labelCount = labels.size
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    axisMinimum = 0f
                    textSize = 8f
                }

                axisRight.isEnabled = false

                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(true)
                setPinchZoom(true)

                animateY(1000)
                invalidate()
            }
        } else {
            binding.barChartCard.visibility = android.view.View.GONE
        }
    }

    private fun getCategoryColor(category: String): Int {
        return when(category) {
            "Топливо" -> getColor(R.color.green)
            "Обслуживание" -> getColor(R.color.blue)
            "Шины" -> getColor(R.color.orange)
            "Налоги" -> getColor(R.color.red)
            "Страховка" -> getColor(R.color.purple)
            "Ремонт" -> getColor(R.color.brown)
            "Мойка" -> getColor(R.color.cyan)
            else -> getColor(R.color.gray)
        }
    }

    private fun updateSeasonalComparison(comparison: AnalyticsController.SeasonalComparison) {
        val hasData = comparison.winterAverage > 0 || comparison.summerAverage > 0

        if (hasData) {
            binding.seasonalCard.visibility = android.view.View.VISIBLE
            binding.winterAverageTextView.text =
                if (comparison.winterAverage > 0)
                    "• Зимой: ${currencyFormat.format(comparison.winterAverage)}"
                else
                    "• Зимой: нет данных"

            binding.summerAverageTextView.text =
                if (comparison.summerAverage > 0)
                    "• Летом: ${currencyFormat.format(comparison.summerAverage)}"
                else
                    "• Летом: нет данных"
            val hasBothSeasons = comparison.winterAverage > 0 && comparison.summerAverage > 0

            if (hasBothSeasons) {
                val percentDifference = ((comparison.winterAverage - comparison.summerAverage) / comparison.summerAverage * 100)

                if (Math.abs(percentDifference) < 1) {
                    binding.seasonComparisonTextView.text = "• Расходы почти одинаковы"
                    binding.seasonComparisonTextView.setTextColor(getColor(R.color.text_secondary))
                } else if (percentDifference > 0) {
                    binding.seasonComparisonTextView.text = "• Зима дороже на ${String.format("%.0f", percentDifference)}%"
                    binding.seasonComparisonTextView.setTextColor(getColor(R.color.red))
                } else {
                    binding.seasonComparisonTextView.text = "• Лето дороже на ${String.format("%.0f", -percentDifference)}%"
                    binding.seasonComparisonTextView.setTextColor(getColor(R.color.red))
                }
            } else if (comparison.winterAverage > 0) {
                binding.seasonComparisonTextView.text = "• Есть только зимние расходы"
                binding.seasonComparisonTextView.setTextColor(getColor(R.color.text_secondary))
            } else if (comparison.summerAverage > 0) {
                binding.seasonComparisonTextView.text = "• Есть только летние расходы"
                binding.seasonComparisonTextView.setTextColor(getColor(R.color.text_secondary))
            } else {
                binding.seasonComparisonTextView.text = "• Недостаточно данных"
                binding.seasonComparisonTextView.setTextColor(getColor(R.color.text_secondary))
            }
        } else {
            binding.seasonalCard.visibility = android.view.View.GONE
        }
    }

    data class MonthlyData(
        val year: Int,
        val month: Int,
        val total: Double
    )
}