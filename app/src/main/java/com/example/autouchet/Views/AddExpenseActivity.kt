package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Controllers.ReceiptScanner
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityAddExpenseBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var expenseController: ExpenseController
    private lateinit var receiptScanner: ReceiptScanner
    private var currentCarId: Int = 1
    private var currentMileage: Int = 0

    private val categories = listOf(
        "Топливо", "Обслуживание", "Шины", "Налоги",
        "Страховка", "Ремонт", "Мойка", "Прочее"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseController = ExpenseController(this)
        receiptScanner = ReceiptScanner(this)

        setupUI()
        loadCurrentCar()
        setupClickListeners()
    }

    private fun setupUI() {
        // Настройка выпадающего списка категорий
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        binding.categoryAutoCompleteTextView.setAdapter(categoryAdapter)

        // Установка текущей даты
        val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        binding.dateEditText.setText(currentDate)

        // Показываем форму ручного ввода по умолчанию
        showManualInput()
    }

    private fun loadCurrentCar() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)
            val cars = database.carDao().getAll()
            if (cars.isNotEmpty()) {
                currentCarId = cars.first().id
                currentMileage = cars.first().currentMileage

                runOnUiThread {
                    binding.mileageEditText.setText(currentMileage.toString())
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.manualInputButton.setOnClickListener {
            showManualInput()
        }

        binding.receiptInputButton.setOnClickListener {
            showReceiptInput()
        }

        binding.saveButton.setOnClickListener {
            saveExpense()
        }

        binding.scanReceiptButton.setOnClickListener {
            simulateReceiptScan()
        }

        binding.categoryAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            if (selectedCategory == "Шины") {
                showTireTemplate()
            } else {
                hideTireTemplate()
            }
        }

        binding.createReminderCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.reminderLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun showManualInput() {
        binding.manualInputButton.isChecked = true
        binding.receiptInputButton.isChecked = false
        binding.manualInputLayout.visibility = View.VISIBLE
        binding.receiptInputLayout.visibility = View.GONE
    }

    private fun showReceiptInput() {
        binding.manualInputButton.isChecked = false
        binding.receiptInputButton.isChecked = true
        binding.manualInputLayout.visibility = View.GONE
        binding.receiptInputLayout.visibility = View.VISIBLE
    }

    private fun showTireTemplate() {
        binding.tireTemplateLayout.visibility = View.VISIBLE
    }

    private fun hideTireTemplate() {
        binding.tireTemplateLayout.visibility = View.GONE
    }

    private fun simulateReceiptScan() {
        // В реальном приложении здесь будет вызов камеры
        // Для демо симулируем сканирование

        val qrData = "t=20230925T142700&s=3200.00&fn=9289000100234567&i=12345&fp=1234567890"
        val receiptData = receiptScanner.simulateScanReceipt(qrData)
        val detectedCategory = receiptScanner.detectCategoryFromReceipt(receiptData.items)

        // Заполняем форму данными из чека
        binding.amountEditText.setText(receiptData.totalAmount.toInt().toString())
        binding.categoryAutoCompleteTextView.setText(detectedCategory, false)
        binding.shopNameEditText.setText(receiptData.shopName)
        binding.commentEditText.setText("Чек ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(receiptData.date)}")

        showManualInput()
    }

    private fun saveExpense() {
        val amountText = binding.amountEditText.text.toString()
        val category = binding.categoryAutoCompleteTextView.text.toString()
        val mileageText = binding.mileageEditText.text.toString()

        if (amountText.isEmpty() || category.isEmpty() || mileageText.isEmpty()) {
            Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull() ?: 0.0
        val mileage = mileageText.toIntOrNull() ?: currentMileage
        val comment = binding.commentEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()

        CoroutineScope(Dispatchers.IO).launch {
            expenseController.addExpense(
                carId = currentCarId,
                amount = amount,
                category = category,
                mileage = mileage,
                comment = comment,
                shopName = shopName,
                isFromReceipt = binding.manualInputButton.isChecked.not()
            )

            runOnUiThread {
                Toast.makeText(
                    this@AddExpenseActivity,
                    "Расход сохранён",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}