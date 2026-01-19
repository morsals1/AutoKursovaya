package com.example.autouchet.Views

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.TireReplacement
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityAddExpenseBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var expenseController: ExpenseController
    private var currentCarId: Int = -1
    private var currentMileage: Int = 0
    private var isEditMode: Boolean = false
    private var expenseToEditId: Int = -1
    private val dateFormatInput = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    private val categories = listOf(
        "Топливо", "Обслуживание", "Ремонт", "Шины",
        "Мойка", "Страховка", "Налоги", "Парковка",
        "Штрафы", "Другое"
    )

    private val tireTypes = listOf("Зимняя", "Летняя", "Всесезонная")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseController = ExpenseController(this)

        isEditMode = intent.getBooleanExtra("edit_mode", false)
        expenseToEditId = intent.getIntExtra("expense_id", -1)

        currentCarId = SharedPrefsHelper.getCurrentCarId(this)

        setupUI()
        setupClickListeners()

        if (currentCarId == -1) {
            Toast.makeText(this, "Сначала добавьте автомобиль", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadCurrentCar()

        if (isEditMode && expenseToEditId != -1) {
            loadExpenseForEditing()
        }
    }

    private fun setupUI() {
        // Настройка категорий
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        binding.categoryAutoCompleteTextView.setAdapter(categoryAdapter)

        // Настройка типов шин
        val tireTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            tireTypes
        )
        binding.tireTypeAutoCompleteTextView.setAdapter(tireTypeAdapter)

        val currentDate = dateFormatInput.format(Date())
        binding.dateEditText.setText(currentDate)

        binding.toolbarTitle.text = if (isEditMode) "РЕДАКТИРОВАНИЕ РАСХОДА" else "НОВЫЙ РАСХОД"

        if (isEditMode) {
            binding.saveButton.text = "ОБНОВИТЬ"
        }

        // Добавляем обработчик клика на EditText для отображения DatePicker
        binding.dateEditText.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentText = binding.dateEditText.text.toString()
        if (currentText.isNotEmpty()) {
            try {
                val currentDate = dateFormatInput.parse(currentText)
                currentDate?.let {
                    calendar.time = it
                }
            } catch (e: Exception) {
                // Ошибка парсинга даты, используем текущую дату
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }
                binding.dateEditText.setText(dateFormatInput.format(selectedDate.time))
            },
            year,
            month,
            day
        )
        datePicker.show()
    }

    private fun loadCurrentCar() {
        if (currentCarId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)
            val car = database.carDao().getById(currentCarId)

            withContext(Dispatchers.Main) {
                car?.let {
                    currentMileage = it.currentMileage
                    if (!isEditMode) {
                        binding.mileageEditText.setText(currentMileage.toString())
                    }
                }
            }
        }
    }

    private fun loadExpenseForEditing() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)
            val expense = database.expenseDao().getById(expenseToEditId)

            expense?.let {
                withContext(Dispatchers.Main) {
                    binding.amountEditText.setText(String.format("%.2f", it.amount))
                    binding.categoryAutoCompleteTextView.setText(it.category, false)
                    binding.dateEditText.setText(dateFormatInput.format(it.date))
                    binding.mileageEditText.setText(it.mileage.toString())
                    binding.shopNameEditText.setText(it.shopName)
                    binding.commentEditText.setText(it.comment)

                    if (it.category == "Шины") {
                        showTireTemplate()
                        // Загружаем информацию о шинах если есть
                        loadTireInfo(expenseToEditId)
                    }
                }
            }
        }
    }

    private fun loadTireInfo(expenseId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)
            val tires = database.tireReplacementDao().getByCar(currentCarId)
            val tireForThisExpense = tires.find { it.installationMileage == binding.mileageEditText.text.toString().toIntOrNull() }

            tireForThisExpense?.let { tire ->
                withContext(Dispatchers.Main) {
                    binding.tireTypeAutoCompleteTextView.setText(tire.tireType, false)
                    binding.tireBrandEditText.setText(tire.brand)
                    binding.tireModelEditText.setText(tire.model)
                    binding.tireSizeEditText.setText(tire.size)
                    binding.tireExpectedYearsEditText.setText(tire.expectedLifetimeYears.toString())
                    binding.tireExpectedKmEditText.setText(tire.expectedLifetimeKm.toString())
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            if (isEditMode) {
                updateExpense()
            } else {
                saveExpense()
            }
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

    private fun showTireTemplate() {
        binding.tireTemplateLayout.visibility = View.VISIBLE
    }

    private fun hideTireTemplate() {
        binding.tireTemplateLayout.visibility = View.GONE
    }

    private fun saveExpense() {
        if (currentCarId == -1) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            return
        }

        val amountText = binding.amountEditText.text.toString()
        val category = binding.categoryAutoCompleteTextView.text.toString().trim()
        val mileageText = binding.mileageEditText.text.toString()
        val dateText = binding.dateEditText.text.toString()

        if (amountText.isEmpty() || category.isEmpty() || mileageText.isEmpty() || dateText.isEmpty()) {
            Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull() ?: 0.0
        val mileage = mileageText.toIntOrNull() ?: currentMileage
        val comment = binding.commentEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()

        val selectedDate = try {
            dateFormatInput.parse(dateText) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val today = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val expenseDate = Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        if (expenseDate > today) {
            Toast.makeText(this, "Нельзя добавить расход на будущую дату", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Сохраняем расход
            expenseController.addExpense(
                carId = currentCarId,
                amount = amount,
                category = category,
                date = selectedDate,
                mileage = mileage,
                comment = comment,
                shopName = shopName,
                isFromReceipt = false
            ) { expenseId ->
                // Если это шины, сохраняем информацию о шинах
                if (category == "Шины") {
                    saveTireInfo(expenseId, selectedDate, mileage)

                    // Если установлен чекбокс напоминания
                    if (binding.createReminderCheckBox.isChecked) {
                        createTireReminder(selectedDate, mileage, expenseId)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@AddExpenseActivity,
                    "Расход сохранён",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun saveTireInfo(expenseId: Int, installationDate: Date, installationMileage: Int) {
        val tireType = binding.tireTypeAutoCompleteTextView.text.toString()
        val brand = binding.tireBrandEditText.text.toString()
        val model = binding.tireModelEditText.text.toString()
        val size = binding.tireSizeEditText.text.toString()
        val expectedYears = binding.tireExpectedYearsEditText.text.toString().toIntOrNull() ?: 4
        val expectedKm = binding.tireExpectedKmEditText.text.toString().toIntOrNull() ?: 60000

        if (tireType.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val database = AppDatabase.getDatabase(this@AddExpenseActivity)

                // Деактивируем старые шины такого же типа
                val oldTire = database.tireReplacementDao().getActiveTireByType(currentCarId, tireType)
                oldTire?.let {
                    database.tireReplacementDao().update(it.copy(isActive = false))
                }

                // Создаем новую запись о шинах
                val tireReplacement = TireReplacement(
                    carId = currentCarId,
                    tireType = tireType,
                    brand = brand,
                    model = model,
                    size = size,
                    installationDate = installationDate,
                    installationMileage = installationMileage,
                    price = 0.0,
                    expectedLifetimeYears = expectedYears,
                    expectedLifetimeKm = expectedKm,
                    isActive = true
                )

                database.tireReplacementDao().insert(tireReplacement)
            }
        }
    }

    private fun createTireReminder(installationDate: Date, installationMileage: Int, expenseId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)

            val reminderYears = binding.reminderYearsEditText.text.toString().toIntOrNull() ?: 2
            val reminderKm = binding.reminderKmEditText.text.toString().toIntOrNull() ?: 40000

            val calendar = Calendar.getInstance()
            calendar.time = installationDate
            calendar.add(Calendar.YEAR, reminderYears)
            val reminderDate = calendar.time

            val reminderMileage = installationMileage + reminderKm

            val reminder = com.example.autouchet.Models.Reminder(
                carId = currentCarId,
                title = "Замена шин",
                type = "mileage",
                targetDate = reminderDate,
                targetMileage = reminderMileage,
                isCompleted = false
            )

            database.reminderDao().insert(reminder)
        }
    }

    private fun updateExpense() {
        if (currentCarId == -1) {
            Toast.makeText(this, "Ошибка: автомобиль не выбран", Toast.LENGTH_SHORT).show()
            return
        }

        val amountText = binding.amountEditText.text.toString()
        val category = binding.categoryAutoCompleteTextView.text.toString().trim()
        val mileageText = binding.mileageEditText.text.toString()
        val dateText = binding.dateEditText.text.toString()

        if (amountText.isEmpty() || category.isEmpty() || mileageText.isEmpty() || dateText.isEmpty()) {
            Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull() ?: 0.0
        val mileage = mileageText.toIntOrNull() ?: currentMileage
        val comment = binding.commentEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()

        val selectedDate = try {
            dateFormatInput.parse(dateText) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val today = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val expenseDate = Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        if (expenseDate > today) {
            Toast.makeText(this, "Нельзя установить будущую дату", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)
            val expense = database.expenseDao().getById(expenseToEditId)

            expense?.let {
                val updatedExpense = it.copy(
                    amount = amount,
                    category = category,
                    date = selectedDate,
                    mileage = mileage,
                    comment = comment,
                    shopName = shopName
                )

                database.expenseDao().update(updatedExpense)

                // Если это шины, обновляем информацию о шинах
                if (category == "Шины") {
                    updateTireInfo(expenseToEditId, selectedDate, mileage)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddExpenseActivity,
                        "Расход обновлён",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddExpenseActivity,
                        "Ошибка: расход не найден",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateTireInfo(expenseId: Int, installationDate: Date, installationMileage: Int) {
        val tireType = binding.tireTypeAutoCompleteTextView.text.toString()
        val brand = binding.tireBrandEditText.text.toString()
        val model = binding.tireModelEditText.text.toString()
        val size = binding.tireSizeEditText.text.toString()
        val expectedYears = binding.tireExpectedYearsEditText.text.toString().toIntOrNull() ?: 4
        val expectedKm = binding.tireExpectedKmEditText.text.toString().toIntOrNull() ?: 60000

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)

            // Ищем существующую запись о шинах по пробегу и типу
            val existingTires = database.tireReplacementDao().getByCar(currentCarId)
            val existingTire = existingTires.find {
                it.installationMileage == installationMileage && it.tireType == tireType
            }

            if (existingTire != null) {
                // Обновляем существующую запись
                val updatedTire = existingTire.copy(
                    tireType = tireType,
                    brand = brand,
                    model = model,
                    size = size,
                    installationDate = installationDate,
                    installationMileage = installationMileage,
                    expectedLifetimeYears = expectedYears,
                    expectedLifetimeKm = expectedKm,
                    isActive = true
                )
                database.tireReplacementDao().update(updatedTire)
            } else if (tireType.isNotEmpty()) {
                // Деактивируем старые шины такого же типа
                val oldTire = database.tireReplacementDao().getActiveTireByType(currentCarId, tireType)
                oldTire?.let {
                    database.tireReplacementDao().update(it.copy(isActive = false))
                }

                // Создаем новую запись
                val tireReplacement = TireReplacement(
                    carId = currentCarId,
                    tireType = tireType,
                    brand = brand,
                    model = model,
                    size = size,
                    installationDate = installationDate,
                    installationMileage = installationMileage,
                    price = 0.0,
                    expectedLifetimeYears = expectedYears,
                    expectedLifetimeKm = expectedKm,
                    isActive = true
                )
                database.tireReplacementDao().insert(tireReplacement)
            }
        }
    }
}