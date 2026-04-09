package com.example.autouchet.Views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.AnalyticsController
import com.example.autouchet.Controllers.CategoryController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.CategoryTotal
import com.example.autouchet.Models.ExpenseCategory
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityAnalyticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var analyticsController: AnalyticsController
    private lateinit var categoryController: CategoryController
    private var currentCarId: Int = -1
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    private val monthNames = listOf(
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
        "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    )

    private var currentStartDate: Long = 0L
    private var currentEndDate: Long = 0L
    private var compareMode = false
    private var compareStartDate: Long = 0L
    private var compareEndDate: Long = 0L
    private var categoriesCache = listOf<ExpenseCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyticsController = AnalyticsController(this)
        categoryController = CategoryController(this)
        currentCarId = SharedPrefsHelper.getCurrentCarId(this)

        setupUI()
        setupDateRangePicker()
        initDefaultPeriod()
        loadCategories()

        if (currentCarId != -1) {
            loadDataForCurrentPeriod()
        } else {
            showNoCarMessage()
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.compareButton.setOnClickListener {
            showCompareModeDialog()
        }
    }

    private fun loadCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            categoriesCache = categoryController.getAllCategories()
        }
    }

    private fun initDefaultPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        currentStartDate = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        currentEndDate = calendar.timeInMillis

        val currentMonth = SimpleDateFormat("LLLL yyyy", Locale("ru")).format(Date())
        binding.toolbarTitle.text = "АНАЛИТИКА • $currentMonth"
        binding.selectedPeriodTextView.text = currentMonth
    }

    private fun setupDateRangePicker() {
        binding.selectDateRangeButton.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Выберите диапазон дат")
                .build()

            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                val startDate = selection.first
                val endDate = selection.second

                if (startDate != null && endDate != null) {
                    val startCalendar = Calendar.getInstance().apply {
                        timeInMillis = startDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val endCalendar = Calendar.getInstance().apply {
                        timeInMillis = endDate
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }

                    currentStartDate = startCalendar.timeInMillis
                    currentEndDate = endCalendar.timeInMillis
                    compareMode = false

                    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
                    val periodText = "${dateFormat.format(startCalendar.time)} - ${dateFormat.format(endCalendar.time)}"

                    binding.selectedPeriodTextView.text = periodText
                    binding.toolbarTitle.text = "АНАЛИТИКА • $periodText"
                    binding.compareIndicator.visibility = View.GONE

                    loadDataForCurrentPeriod()
                }
            }

            dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
        }
    }

    private fun showCompareModeDialog() {
        val options = arrayOf(
            "Сравнить с предыдущим месяцем",
            "Сравнить с предыдущим годом",
            "Выбрать период для сравнения"
        )

        AlertDialog.Builder(this)
            .setTitle("Сравнение периодов")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setCompareWithPreviousMonth()
                    1 -> setCompareWithPreviousYear()
                    2 -> showCompareDatePicker()
                }
            }
            .show()
    }

    private fun setCompareWithPreviousMonth() {
        val startCalendar = Calendar.getInstance().apply { timeInMillis = currentStartDate }
        val endCalendar = Calendar.getInstance().apply { timeInMillis = currentEndDate }
        val daysDiff = ((currentEndDate - currentStartDate) / (1000 * 60 * 60 * 24)).toInt()

        startCalendar.add(Calendar.MONTH, -1)
        compareStartDate = startCalendar.timeInMillis

        endCalendar.timeInMillis = compareStartDate
        endCalendar.add(Calendar.DAY_OF_MONTH, daysDiff)
        compareEndDate = endCalendar.timeInMillis

        compareMode = true
        binding.compareIndicator.visibility = View.VISIBLE
        binding.compareIndicator.text = "Сравнение с предыдущим месяцем"
        loadDataForCurrentPeriod()
    }

    private fun setCompareWithPreviousYear() {
        val startCalendar = Calendar.getInstance().apply { timeInMillis = currentStartDate }
        val endCalendar = Calendar.getInstance().apply { timeInMillis = currentEndDate }

        startCalendar.add(Calendar.YEAR, -1)
        compareStartDate = startCalendar.timeInMillis

        endCalendar.add(Calendar.YEAR, -1)
        compareEndDate = endCalendar.timeInMillis

        compareMode = true
        binding.compareIndicator.visibility = View.VISIBLE
        binding.compareIndicator.text = "Сравнение с предыдущим годом"
        loadDataForCurrentPeriod()
    }

    private fun showCompareDatePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Выберите период для сравнения")
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection.first
            val endDate = selection.second

            if (startDate != null && endDate != null) {
                val startCalendar = Calendar.getInstance().apply {
                    timeInMillis = startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCalendar = Calendar.getInstance().apply {
                    timeInMillis = endDate
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                compareStartDate = startCalendar.timeInMillis
                compareEndDate = endCalendar.timeInMillis
                compareMode = true

                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
                val periodText = "${dateFormat.format(startCalendar.time)} - ${dateFormat.format(endCalendar.time)}"
                binding.compareIndicator.visibility = View.VISIBLE
                binding.compareIndicator.text = "Сравнение с: $periodText"

                loadDataForCurrentPeriod()
            }
        }

        dateRangePicker.show(supportFragmentManager, "COMPARE_DATE_PICKER")
    }

    private fun loadDataForCurrentPeriod() {
        if (currentCarId == -1) {
            showNoCarMessage()
            return
        }

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val monthlyExpenses = getExpensesForPeriodSync(currentCarId, currentStartDate, currentEndDate)
                val expensePerKm = calculateExpensePerKm(currentCarId, currentStartDate, currentEndDate)
                val expensePerKmByCategory = calculateExpensePerKmByCategory(currentCarId, currentStartDate, currentEndDate)
                val averageDailyExpense = calculateAverageDailyExpense(monthlyExpenses, currentStartDate, currentEndDate)
                val categoryTotals = getCategoryTotalsForPeriodSync(currentCarId, currentStartDate, currentEndDate)
                val monthlyTrends = getMonthlyTrendsForPeriodSync(currentCarId, currentStartDate, currentEndDate)
                val seasonalComparison = analyticsController.getSeasonalComparison(currentCarId)

                val compareData = if (compareMode) {
                    val compareExpenses = getExpensesForPeriodSync(currentCarId, compareStartDate, compareEndDate)
                    val comparePerKm = calculateExpensePerKm(currentCarId, compareStartDate, compareEndDate)
                    val compareDaily = calculateAverageDailyExpense(compareExpenses, compareStartDate, compareEndDate)
                    CompareData(compareExpenses, comparePerKm, compareDaily)
                } else null

                withContext(Dispatchers.Main) {
                    updateStatistics(monthlyExpenses, expensePerKm, averageDailyExpense, compareData)
                    updateExpensePerKmByCategory(expensePerKmByCategory)
                    updatePieChart(categoryTotals)
                    updateBarChart(monthlyTrends)
                    updateSeasonalComparison(seasonalComparison)
                    showLoading(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showError()
                    showLoading(false)
                }
            }
        }
    }

    private suspend fun calculateExpensePerKmByCategory(carId: Int, startDate: Long, endDate: Long): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val expenses = database.expenseDao().getByDateRange(carId, startDate, endDate)

            if (expenses.isEmpty()) return@withContext emptyMap()

            val mileageEntries = expenses.filter { it.mileage > 0 }
            if (mileageEntries.isEmpty()) return@withContext emptyMap()

            val startMileage = mileageEntries.minByOrNull { it.mileage }?.mileage ?: 0
            val endMileage = mileageEntries.maxByOrNull { it.mileage }?.mileage ?: 0
            val totalKm = endMileage - startMileage

            if (totalKm <= 0) return@withContext emptyMap()

            val result = mutableMapOf<String, Double>()
            val categories = expenses.map { it.category }.distinct()

            categories.forEach { category ->
                val categoryExpenses = expenses.filter { it.category == category }
                val categoryTotal = categoryExpenses.sumByDouble { it.amount }
                result[category] = categoryTotal / totalKm
            }

            result
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun showNoCarMessage() {
        binding.statisticsCard.visibility = View.VISIBLE
        binding.totalExpensesTextView.text = "• Сначала добавьте автомобиль"
        binding.averageDailyTextView.text = "• В настройках"
        binding.expensePerKmTextView.text = ""

        binding.pieChartCard.visibility = View.GONE
        binding.barChartCard.visibility = View.GONE
        binding.seasonalCard.visibility = View.GONE
        binding.expensePerKmDetailCard.visibility = View.GONE
        binding.loadingProgressBar.visibility = ProgressBar.GONE
    }

    private fun showError() {
        binding.statisticsCard.visibility = View.VISIBLE
        binding.totalExpensesTextView.text = "• Ошибка загрузки данных"
        binding.averageDailyTextView.text = "• Средний день: 0 ₽"
        binding.expensePerKmTextView.text = "• Расход на 1 км: 0 ₽"

        binding.pieChartCard.visibility = View.GONE
        binding.barChartCard.visibility = View.GONE
        binding.seasonalCard.visibility = View.GONE
        binding.expensePerKmDetailCard.visibility = View.GONE
    }

    private suspend fun getExpensesForPeriodSync(carId: Int, startDate: Long, endDate: Long): Double {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            database.expenseDao().getTotalByDateRange(carId, startDate, endDate) ?: 0.0
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

    private suspend fun getMonthlyTrendsForPeriodSync(carId: Int, startDate: Long, endDate: Long): List<MonthlyData> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@AnalyticsActivity)
            val monthsData = mutableListOf<MonthlyData>()

            val startCalendar = Calendar.getInstance().apply { timeInMillis = startDate }
            val endCalendar = Calendar.getInstance().apply { timeInMillis = endDate }

            val currentCalendar = Calendar.getInstance().apply {
                timeInMillis = startDate
                set(Calendar.DAY_OF_MONTH, 1)
            }

            while (currentCalendar.timeInMillis <= endCalendar.timeInMillis) {
                val year = currentCalendar.get(Calendar.YEAR)
                val month = currentCalendar.get(Calendar.MONTH)

                val monthStart = getMonthStartTime(year, month)
                val monthEnd = getMonthEndTime(year, month)

                val total = database.expenseDao().getTotalByDateRange(carId, monthStart, monthEnd) ?: 0.0
                monthsData.add(MonthlyData(year, month, total))

                currentCalendar.add(Calendar.MONTH, 1)
            }

            monthsData
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

    private fun updateStatistics(monthlyExpenses: Double, expensePerKm: Double, averageDailyExpense: Double, compareData: CompareData?) {
        binding.statisticsCard.visibility = View.VISIBLE

        if (compareData != null) {
            val expenseDiff = monthlyExpenses - compareData.totalExpenses
            val expensePercent = if (compareData.totalExpenses > 0) (expenseDiff / compareData.totalExpenses * 100) else 0.0

            val kmDiff = expensePerKm - compareData.expensePerKm
            val kmPercent = if (compareData.expensePerKm > 0) (kmDiff / compareData.expensePerKm * 100) else 0.0

            val dailyDiff = averageDailyExpense - compareData.averageDaily
            val dailyPercent = if (compareData.averageDaily > 0) (dailyDiff / compareData.averageDaily * 100) else 0.0

            binding.totalExpensesTextView.text = buildComparisonString(
                "Всего расходов", monthlyExpenses, expenseDiff, expensePercent
            )
            binding.averageDailyTextView.text = buildComparisonString(
                "Средний день", averageDailyExpense, dailyDiff, dailyPercent
            )
            binding.expensePerKmTextView.text = buildComparisonString(
                "Расход на 1 км", expensePerKm, kmDiff, kmPercent, "₽"
            )

            binding.monthComparisonTextView.text = when {
                expenseDiff > 0 -> "▲ Расходы выросли на ${currencyFormat.format(expenseDiff)} (${String.format("%.1f", expensePercent)}%)"
                expenseDiff < 0 -> "▼ Расходы снизились на ${currencyFormat.format(abs(expenseDiff))} (${String.format("%.1f", abs(expensePercent))}%)"
                else -> "Расходы не изменились"
            }
            binding.monthComparisonTextView.setTextColor(
                when {
                    expenseDiff > 0 -> getColor(R.color.red)
                    expenseDiff < 0 -> getColor(R.color.green)
                    else -> getColor(R.color.text_secondary)
                }
            )
        } else {
            binding.totalExpensesTextView.text = "• Всего расходов: ${currencyFormat.format(monthlyExpenses)}"
            binding.averageDailyTextView.text = "• Средний день: ${currencyFormat.format(averageDailyExpense)}"
            binding.expensePerKmTextView.text = "• Расход на 1 км: ${String.format("%.2f", expensePerKm)} ₽"
            binding.monthComparisonTextView.text = "Выбранный период"
            binding.monthComparisonTextView.setTextColor(getColor(R.color.text_secondary))
        }

        val startCalendar = Calendar.getInstance().apply { timeInMillis = currentStartDate }
        val endCalendar = Calendar.getInstance().apply { timeInMillis = currentEndDate }
        val yearText = if (startCalendar.get(Calendar.YEAR) == endCalendar.get(Calendar.YEAR)) {
            "${startCalendar.get(Calendar.YEAR)}"
        } else {
            "${startCalendar.get(Calendar.YEAR)}-${endCalendar.get(Calendar.YEAR)}"
        }
        binding.barChartTitle.text = "ГРАФИК ПО МЕСЯЦАМ ($yearText)"
    }

    private fun buildComparisonString(label: String, current: Double, diff: Double, percent: Double, suffix: String = "₽"): String {
        val arrow = if (diff > 0) "▲" else if (diff < 0) "▼" else ""
        val diffStr = if (diff != 0.0) " $arrow ${String.format("%.1f", abs(percent))}%" else ""
        return "• $label: ${if (suffix == "₽") currencyFormat.format(current) else String.format("%.2f", current) + " $suffix"}$diffStr"
    }

    private fun updateExpensePerKmByCategory(expensePerKmByCategory: Map<String, Double>) {
        if (expensePerKmByCategory.isEmpty()) {
            binding.expensePerKmDetailCard.visibility = View.GONE
            return
        }

        binding.expensePerKmDetailCard.visibility = View.VISIBLE
        binding.expensePerKmDetailLayout.removeAllViews()

        val sortedEntries = expensePerKmByCategory.entries.sortedByDescending { it.value }

        sortedEntries.forEach { (category, value) ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_expense_per_km, null)
            val categoryTextView = itemView.findViewById<TextView>(R.id.categoryTextView)
            val valueTextView = itemView.findViewById<TextView>(R.id.valueTextView)

            val categoryData = categoriesCache.find { it.name == category }
            categoryTextView.text = "${categoryData?.icon ?: "💰"} $category"
            valueTextView.text = String.format("%.2f ₽/км", value)

            binding.expensePerKmDetailLayout.addView(itemView)
        }
    }

    private fun updatePieChart(categoryTotals: List<CategoryTotal>) {
        val hasData = categoryTotals.isNotEmpty() && categoryTotals.sumByDouble { it.total } > 0

        if (hasData) {
            binding.pieChartCard.visibility = View.VISIBLE
            binding.pieChart.visibility = View.VISIBLE

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
            binding.pieChartCard.visibility = View.GONE
        }
    }

    private fun updateCategoryLegend(categoryTotals: List<CategoryTotal>) {
        binding.categoryLegendLayout.removeAllViews()

        val totalAmount = categoryTotals.sumByDouble { it.total }

        for (categoryTotal in categoryTotals.sortedByDescending { it.total }) {
            val percentage = (categoryTotal.total / totalAmount * 100).toInt()

            val legendItem = LayoutInflater.from(this).inflate(R.layout.legend_item, null)
            val colorView = legendItem.findViewById<View>(R.id.colorView)
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
            binding.barChartCard.visibility = View.VISIBLE

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
            binding.barChartCard.visibility = View.GONE
        }
    }

    private fun getCategoryColor(category: String): Int {
        val categoryData = categoriesCache.find { it.name == category }
        return categoryData?.color ?: when(category) {
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
            binding.seasonalCard.visibility = View.VISIBLE
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

                if (abs(percentDifference) < 1) {
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
            binding.seasonalCard.visibility = View.GONE
        }
    }

    data class MonthlyData(
        val year: Int,
        val month: Int,
        val total: Double
    )

    data class CompareData(
        val totalExpenses: Double,
        val expensePerKm: Double,
        val averageDaily: Double
    )
}