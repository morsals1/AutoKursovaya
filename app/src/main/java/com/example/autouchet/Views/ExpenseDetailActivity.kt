package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityExpenseDetailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class ExpenseDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpenseDetailBinding
    private var expenseId: Int = -1
    private var currentCarId: Int = -1
    private var currentMileage: Int = 0
    private val dateFormatDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseId = intent.getIntExtra("expense_id", -1)
        currentCarId = SharedPrefsHelper.getCurrentCarId(this)

        if (expenseId == -1 || currentCarId == -1) {
            Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadExpenseData()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.editButton.setOnClickListener {
            val intent = Intent(this, com.example.autouchet.Views.AddExpenseActivity::class.java).apply {
                putExtra("edit_mode", true)
                putExtra("expense_id", expenseId)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadExpenseData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@ExpenseDetailActivity)
            val expense = database.expenseDao().getById(expenseId)
            val car = database.carDao().getById(currentCarId)

            car?.let {
                currentMileage = it.currentMileage
            }

            expense?.let {
                withContext(Dispatchers.Main) {
                    displayExpenseData(it)
                    if (it.category == "Шины") {
                        loadTireInfo(expenseId)
                    }
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseDetailActivity, "Расход не найден", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayExpenseData(expense: com.example.autouchet.Models.Expense) {
        binding.amountTextView.text = currencyFormat.format(expense.amount)
        binding.categoryTextView.text = expense.category
        binding.categoryTextView.setTextColor(expense.getCategoryColor())

        binding.dateTextView.text = dateFormatDisplay.format(expense.date)
        binding.mileageTextView.text = "${String.format("%,d", expense.mileage)} км"

        if (expense.comment.isNotEmpty()) {
            binding.commentTextView.text = expense.comment
        } else {
            binding.commentTextView.text = "Нет комментария"
        }
        if (expense.shopName.isNotEmpty()) {
            binding.shopLayout.isVisible = true
            binding.shopTextView.text = expense.shopName
        } else {
            binding.shopLayout.isVisible = false
        }
    }

    private fun loadTireInfo(expenseId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@ExpenseDetailActivity)
            val expense = database.expenseDao().getById(expenseId)

            expense?.let { exp ->
                if (exp.category != "Шины") {
                    withContext(Dispatchers.Main) {
                        binding.tireInfoCard.isVisible = false
                    }
                    return@launch
                }
                val allTires = database.tireReplacementDao().getByCar(currentCarId)
                val tireInstalledInThisExpense = allTires.find { it.expenseId == expenseId }
                val currentActiveTireSameType = if (tireInstalledInThisExpense != null) {
                    allTires.find {
                        it.isActive &&
                                it.tireType == tireInstalledInThisExpense.tireType &&
                                it.id != tireInstalledInThisExpense.id
                    }
                } else {
                    allTires.filter { it.isActive }
                        .maxByOrNull { it.installationDate }
                }

                withContext(Dispatchers.Main) {
                    when {
                        tireInstalledInThisExpense != null -> {
                            displayTireInfo(tireInstalledInThisExpense, true)
                            binding.tireInfoCard.isVisible = true

                            if (!tireInstalledInThisExpense.isActive) {
                                binding.tireInfoCard.setCardBackgroundColor(
                                    ContextCompat.getColor(this@ExpenseDetailActivity, R.color.divider)
                                )
                            }
                        }
                        currentActiveTireSameType != null -> {
                            displayTireInfo(currentActiveTireSameType, false)
                            binding.tireInfoCard.isVisible = true
                        }

                        else -> {
                            binding.tireInfoCard.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun displayTireInfo(tire: com.example.autouchet.Models.TireReplacement, fromThisExpense: Boolean) {
        val headerTextView = binding.tireInfoCard.findViewById<TextView>(R.id.tireInfoSubtitle)
        if (headerTextView != null) {
            headerTextView.text = if (fromThisExpense) {
                "Установленные в этом расходе шины"
            } else {
                "Текущие активные шины"
            }
        }

        binding.tireTypeTextView.text = tire.tireType

        val brandModel = if (tire.model.isNotEmpty()) {
            "${tire.brand} ${tire.model}"
        } else {
            tire.brand
        }
        binding.tireBrandModelTextView.text = brandModel
        binding.tireSizeTextView.text = tire.size
        binding.tireLifetimeTextView.text = "${tire.expectedLifetimeYears} года или ${String.format("%,d", tire.expectedLifetimeKm)} км"

        val statusColor: Int
        val statusText: String

        if (!tire.isActive) {
            statusText = "Заменены"
            statusColor = getColor(R.color.text_secondary)
            binding.tireStatusTextView.setTextColor(statusColor)
            binding.tireTypeTextView.setTextColor(getColor(R.color.text_secondary))
            binding.tireBrandModelTextView.setTextColor(getColor(R.color.text_secondary))
            binding.tireSizeTextView.setTextColor(getColor(R.color.text_secondary))
            binding.tireLifetimeTextView.setTextColor(getColor(R.color.text_secondary))
            binding.tireRemainingTextView.text = "Замена выполнена"
            binding.tireRemainingTextView.setTextColor(getColor(R.color.text_secondary))

        } else {
            binding.tireTypeTextView.setTextColor(getColor(R.color.text_primary))
            binding.tireBrandModelTextView.setTextColor(getColor(R.color.text_primary))
            binding.tireSizeTextView.setTextColor(getColor(R.color.text_primary))
            binding.tireLifetimeTextView.setTextColor(getColor(R.color.text_primary))

            val currentDate = Date()
            val (needsReplacement, statusMessage) = tire.needsReplacement(currentDate, currentMileage)

            if (needsReplacement) {
                statusText = "Требуют замены"
                statusColor = getColor(R.color.red)
            } else {
                statusText = "Активны"
                statusColor = getColor(R.color.green)
            }

            binding.tireStatusTextView.setTextColor(statusColor)

            val daysPassed = (currentDate.time - tire.installationDate.time) / (1000 * 60 * 60 * 24)
            val yearsPassed = daysPassed / 365.0
            val kmPassed = currentMileage - tire.installationMileage

            val yearsLeft = max(0.0, tire.expectedLifetimeYears - yearsPassed)
            val kmLeft = max(0, tire.expectedLifetimeKm - kmPassed)

            val remainingText = when {
                yearsLeft <= 0 && kmLeft <= 0 -> "Ресурс исчерпан"
                yearsLeft > 0 && kmLeft > 0 -> {
                    "${String.format("%.1f", yearsLeft)} лет или ${String.format("%,d", kmLeft)} км"
                }
                yearsLeft > 0 -> "${String.format("%.1f", yearsLeft)} лет"
                kmLeft > 0 -> "${String.format("%,d", kmLeft)} км"
                else -> "Ресурс исчерпан"
            }

            binding.tireRemainingTextView.text = remainingText
            binding.tireRemainingTextView.setTextColor(
                when {
                    yearsLeft < 0.5 || kmLeft < 5000 -> getColor(R.color.red)
                    yearsLeft < 1 || kmLeft < 10000 -> getColor(R.color.orange)
                    else -> getColor(R.color.text_primary)
                }
            )
        }

        binding.tireStatusTextView.text = statusText

        if (!fromThisExpense) {
            val additionalInfo = "\n\n⚠️ Показаны текущие активные шины этого типа."
            val currentText = binding.tireRemainingTextView.text.toString()
            binding.tireRemainingTextView.text = currentText + additionalInfo
        }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удаление расхода")
            .setMessage("Вы уверены, что хотите удалить этот расход?")
            .setPositiveButton("Удалить") { dialog, _ ->
                deleteExpense()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteExpense() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удаление расхода")
            .setMessage("Вы уверены, что хотите удалить этот расход?")
            .setPositiveButton("Удалить") { dialog, _ ->
                performDelete()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performDelete() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@ExpenseDetailActivity)
            val expense = database.expenseDao().getById(expenseId)

            expense?.let { exp ->
                database.expenseDao().delete(exp)

                if (exp.category == "Шины") {
                    val tires = database.tireReplacementDao().getByCar(currentCarId)
                    val tireForThisExpense = tires.find { it.expenseId == expenseId }
                    tireForThisExpense?.let { tire ->
                        database.tireReplacementDao().update(
                            tire.copy(
                                isActive = false,
                                notes = if (tire.notes.isNullOrEmpty()) "Удалены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                else "${tire.notes}. Удалены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                            )
                        )
                    }
                    val currentActiveTires = tires.filter { it.isActive }
                    val tireType = tireForThisExpense?.tireType

                    if (tireType != null) {
                        val newestActiveTireSameType = currentActiveTires
                            .filter { it.tireType == tireType }
                            .maxByOrNull { it.installationDate }
                        if (newestActiveTireSameType == null) {
                            val newestInactiveTireSameType = tires
                                .filter { !it.isActive && it.tireType == tireType }
                                .maxByOrNull { it.installationDate }

                            newestInactiveTireSameType?.let { inactiveTire ->
                                database.tireReplacementDao().update(
                                    inactiveTire.copy(
                                        isActive = true,
                                        notes = if (inactiveTire.notes.isNullOrEmpty()) "Активированы ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                        else "${inactiveTire.notes}. Активированы ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                    )
                                )
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExpenseDetailActivity, "Расход удалён", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}