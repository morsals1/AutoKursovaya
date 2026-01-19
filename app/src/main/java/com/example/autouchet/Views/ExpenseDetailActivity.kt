package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            // Редактирование расхода
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

                    // Если это шины, загружаем дополнительную информацию
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

        // Устанавливаем цвет категории
        binding.categoryTextView.setTextColor(expense.getCategoryColor())

        binding.dateTextView.text = dateFormatDisplay.format(expense.date)
        binding.mileageTextView.text = "${String.format("%,d", expense.mileage)} км"

        if (expense.comment.isNotEmpty()) {
            binding.commentTextView.text = expense.comment
        } else {
            binding.commentTextView.text = "Нет комментария"
        }

        // Показываем магазин, если есть
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
            val tires = database.tireReplacementDao().getByCar(currentCarId)

            // 1. Ищем шины ТОЛЬКО по expenseId
            val tireToShow = tires.find { it.expenseId == expenseId }

            withContext(Dispatchers.Main) {
                if (tireToShow != null) {
                    displayTireInfo(tireToShow)
                    binding.tireInfoCard.isVisible = true
                    val params = binding.editButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    params.topToBottom = R.id.tireInfoCard
                    binding.editButton.layoutParams = params
                } else {
                    // Если не нашли по expenseId, показываем сообщение
                    binding.tireInfoCard.isVisible = false
                    // Или показываем сообщение об отсутствии информации
                    Toast.makeText(this@ExpenseDetailActivity, "Информация о шинах не найдена", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayTireInfo(tire: com.example.autouchet.Models.TireReplacement) {
        // Тип резины
        binding.tireTypeTextView.text = tire.tireType

        // Марка и модель
        val brandModel = if (tire.model.isNotEmpty()) {
            "${tire.brand} ${tire.model}"
        } else {
            tire.brand
        }
        binding.tireBrandModelTextView.text = brandModel

        // Размер
        binding.tireSizeTextView.text = tire.size

        // Срок службы
        binding.tireLifetimeTextView.text = "${tire.expectedLifetimeYears} года или ${String.format("%,d", tire.expectedLifetimeKm)} км"

        // Статус
        val currentDate = Date()
        val (needsReplacement, statusMessage) = tire.needsReplacement(currentDate, currentMileage)

        if (needsReplacement) {
            binding.tireStatusTextView.text = "Требуют замены"
            binding.tireStatusTextView.setTextColor(getColor(R.color.red))
        } else {
            binding.tireStatusTextView.text = "Активны"
            binding.tireStatusTextView.setTextColor(getColor(R.color.green))
        }

        // Оставшийся ресурс
        val daysPassed = (currentDate.time - tire.installationDate.time) / (1000 * 60 * 60 * 24)
        val yearsPassed = daysPassed / 365.0
        val kmPassed = currentMileage - tire.installationMileage

        val yearsLeft = tire.expectedLifetimeYears - yearsPassed
        val kmLeft = tire.expectedLifetimeKm - kmPassed

        val remainingText = when {
            yearsLeft <= 0 && kmLeft <= 0 -> "Ресурс исчерпан"
            yearsLeft > 0 && kmLeft > 0 -> "${String.format("%.1f", yearsLeft)} лет или ${String.format("%,d", kmLeft)} км"
            yearsLeft > 0 -> "${String.format("%.1f", yearsLeft)} лет"
            kmLeft > 0 -> "${String.format("%,d", kmLeft)} км"
            else -> "Ресурс исчерпан"
        }

        binding.tireRemainingTextView.text = remainingText

        // Подсветка если ресурс заканчивается
        if (yearsLeft < 1 || kmLeft < 10000) {
            binding.tireRemainingTextView.setTextColor(getColor(R.color.orange))
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
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@ExpenseDetailActivity)
            val expense = database.expenseDao().getById(expenseId)

            expense?.let {
                database.expenseDao().delete(it)

                // Если это шины, проверяем, нужно ли деактивировать запись о шинах
                if (it.category == "Шины") {
                    val tires = database.tireReplacementDao().getByCar(currentCarId)
                    // Ищем по expenseId
                    val tire = tires.find { tire -> tire.expenseId == expenseId }

                    tire?.let { tireRecord ->
                        // Деактивируем шины
                        database.tireReplacementDao().update(tireRecord.copy(isActive = false))
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