package com.example.autouchet.Views

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
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
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        binding.categoryAutoCompleteTextView.setAdapter(categoryAdapter)

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
            val tireForThisExpense = tires.find { it.expenseId == expenseId }

            tireForThisExpense?.let { tire ->
                withContext(Dispatchers.Main) {
                    binding.tireTypeAutoCompleteTextView.setText(tire.tireType, false)
                    binding.tireBrandEditText.setText(tire.brand)
                    binding.tireModelEditText.setText(tire.model)
                    binding.tireSizeEditText.setText(tire.size)
                    binding.tireExpectedYearsEditText.setText(tire.expectedLifetimeYears.toString())
                    binding.tireExpectedKmEditText.setText(tire.expectedLifetimeKm.toString())
                    binding.replaceAllTiresCheckBox.isChecked = false
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
        binding.replaceAllTiresCheckBox.visibility = View.VISIBLE
    }

    private fun hideTireTemplate() {
        binding.tireTemplateLayout.visibility = View.GONE
        binding.replaceAllTiresCheckBox.visibility = View.GONE
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

        if (mileage > 9999999) {
            Toast.makeText(this, "Пробег не может превышать 10 000 000 км", Toast.LENGTH_SHORT).show()
            return
        }
        if (mileage < 0) {
            Toast.makeText(this, "Пробег не может быть отрицательным", Toast.LENGTH_SHORT).show()
            return
        }

        if (shopName.length > 50) {
            Toast.makeText(this, "Название магазина не более 50 символов", Toast.LENGTH_SHORT).show()
            return
        }

        if (comment.length > 100) {
            Toast.makeText(this, "Комментарий не более 100 символов", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount > 100000000) {
            Toast.makeText(this, "Сумма не может превышать 100 000 000", Toast.LENGTH_SHORT).show()
            return
        }
        if (amount <= 0) {
            Toast.makeText(this, "Сумма должна быть больше 0", Toast.LENGTH_SHORT).show()
            return
        }

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

        if (category == "Шины" && !validateTireData()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
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
                if (category == "Шины") {
                    saveTireInfo(expenseId, selectedDate, mileage)
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

    private fun validateTireData(): Boolean {
        val tireType = binding.tireTypeAutoCompleteTextView.text.toString()
        val brand = binding.tireBrandEditText.text.toString()
        val size = binding.tireSizeEditText.text.toString()
        val expectedYearsText = binding.tireExpectedYearsEditText.text.toString()
        val expectedKmText = binding.tireExpectedKmEditText.text.toString()

        if (tireType.isEmpty()) {
            Toast.makeText(this, "Выберите тип резины", Toast.LENGTH_SHORT).show()
            binding.tireTypeAutoCompleteTextView.requestFocus()
            return false
        }

        if (!tireTypes.contains(tireType)) {
            Toast.makeText(this, "Выберите тип резины из списка", Toast.LENGTH_SHORT).show()
            binding.tireTypeAutoCompleteTextView.requestFocus()
            return false
        }

        if (brand.isEmpty()) {
            Toast.makeText(this, "Введите марку шин", Toast.LENGTH_SHORT).show()
            binding.tireBrandEditText.requestFocus()
            return false
        }

        if (brand.length < 2) {
            Toast.makeText(this, "Марка должна содержать минимум 2 символа", Toast.LENGTH_SHORT).show()
            binding.tireBrandEditText.requestFocus()
            return false
        }

        if (size.isEmpty()) {
            Toast.makeText(this, "Введите размер шин", Toast.LENGTH_SHORT).show()
            binding.tireSizeEditText.requestFocus()
            return false
        }

        if (!size.matches(Regex("""^\d{3}/\d{2}\s*[Rr]\d{2}.*"""))) {
            Toast.makeText(this, "Введите корректный размер (например: 195/65 R15)", Toast.LENGTH_SHORT).show()
            binding.tireSizeEditText.requestFocus()
            return false
        }

        if (expectedYearsText.isEmpty()) {
            Toast.makeText(this, "Введите ожидаемый срок службы в годах", Toast.LENGTH_SHORT).show()
            binding.tireExpectedYearsEditText.requestFocus()
            return false
        }

        val expectedYears = expectedYearsText.toIntOrNull()
        if (expectedYears == null || expectedYears < 1 || expectedYears > 10) {
            Toast.makeText(this, "Срок службы должен быть от 1 до 10 лет", Toast.LENGTH_SHORT).show()
            binding.tireExpectedYearsEditText.requestFocus()
            return false
        }

        if (expectedKmText.isEmpty()) {
            Toast.makeText(this, "Введите ожидаемый пробег", Toast.LENGTH_SHORT).show()
            binding.tireExpectedKmEditText.requestFocus()
            return false
        }

        val expectedKm = expectedKmText.toIntOrNull()
        if (expectedKm == null || expectedKm < 1000 || expectedKm > 100000) {
            Toast.makeText(this, "Ожидаемый пробег должен быть от 1000 до 100000 км", Toast.LENGTH_SHORT).show()
            binding.tireExpectedKmEditText.requestFocus()
            return false
        }

        if (binding.createReminderCheckBox.isChecked) {
            val reminderYearsText = binding.reminderYearsEditText.text.toString()
            val reminderKmText = binding.reminderKmEditText.text.toString()

            if (reminderYearsText.isEmpty() && reminderKmText.isEmpty()) {
                Toast.makeText(this, "Заполните хотя бы одно поле для напоминания", Toast.LENGTH_SHORT).show()
                return false
            }

            if (reminderYearsText.isNotEmpty()) {
                val reminderYears = reminderYearsText.toIntOrNull()
                if (reminderYears == null || reminderYears < 1 || reminderYears > 10) {
                    Toast.makeText(this, "Напоминание по годам должно быть от 1 до 10 лет", Toast.LENGTH_SHORT).show()
                    binding.reminderYearsEditText.requestFocus()
                    return false
                }
            }

            if (reminderKmText.isNotEmpty()) {
                val reminderKm = reminderKmText.toIntOrNull()
                if (reminderKm == null || reminderKm < 1000 || reminderKm > 100000) {
                    Toast.makeText(this, "Напоминание по пробегу должно быть от 1000 до 100000 км", Toast.LENGTH_SHORT).show()
                    binding.reminderKmEditText.requestFocus()
                    return false
                }
            }
        }

        return true
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
                val replaceAllTires = binding.replaceAllTiresCheckBox.isChecked

                if (replaceAllTires) {
                    val activeTires = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive }

                    activeTires.forEach { tire ->
                        database.tireReplacementDao().update(
                            tire.copy(
                                isActive = false,
                                notes = if (tire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                else "${tire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                            )
                        )
                    }

                    val tireTypesToCreate = if (replaceAllTires) listOf("Зимняя", "Летняя") else listOf(tireType)

                    tireTypesToCreate.forEach { type ->
                        val tireReplacement = TireReplacement(
                            carId = currentCarId,
                            tireType = type,
                            brand = brand,
                            model = model,
                            size = size,
                            installationDate = installationDate,
                            installationMileage = installationMileage,
                            price = 0.0,
                            expectedLifetimeYears = expectedYears,
                            expectedLifetimeKm = expectedKm,
                            isActive = true,
                            expenseId = if (type == tireType) expenseId else null
                        )

                        database.tireReplacementDao().insert(tireReplacement)
                    }
                } else {
                    val oldTiresSameType = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive && it.tireType == tireType }

                    oldTiresSameType.forEach { oldTire ->
                        database.tireReplacementDao().update(
                            oldTire.copy(
                                isActive = false,
                                notes = if (oldTire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                else "${oldTire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                            )
                        )
                    }
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
                        isActive = true,
                        expenseId = expenseId
                    )

                    database.tireReplacementDao().insert(tireReplacement)
                }

                val allTires = database.tireReplacementDao().getByCar(currentCarId)
                android.util.Log.d("TireDebug", "=== Состояние шин после сохранения ===")
                allTires.forEach { tire ->
                    android.util.Log.d("TireDebug",
                        "ID: ${tire.id}, Тип: ${tire.tireType}, Активна: ${tire.isActive}, " +
                                "Дата установки: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(tire.installationDate)}, " +
                                "expenseId: ${tire.expenseId}"
                    )
                }
            }
        }
    }

    private fun createTireReminder(installationDate: Date, installationMileage: Int, expenseId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@AddExpenseActivity)

            val reminderYearsText = binding.reminderYearsEditText.text.toString()
            val reminderKmText = binding.reminderKmEditText.text.toString()

            var reminderYears: Int? = null
            var reminderKm: Int? = null

            if (reminderYearsText.isNotEmpty()) {
                reminderYears = reminderYearsText.toIntOrNull()
            }

            if (reminderKmText.isNotEmpty()) {
                reminderKm = reminderKmText.toIntOrNull()
            }

            val reminderTitle = "Замена шин"
            var reminderType = ""
            var targetDate: Date? = null
            var targetMileage: Int? = null

            if (reminderYears != null && reminderKm != null) {
                reminderType = "combined"
                val calendar = Calendar.getInstance()
                calendar.time = installationDate
                calendar.add(Calendar.YEAR, reminderYears)
                targetDate = calendar.time
                targetMileage = installationMileage + reminderKm
            } else if (reminderYears != null) {
                reminderType = "date"
                val calendar = Calendar.getInstance()
                calendar.time = installationDate
                calendar.add(Calendar.YEAR, reminderYears)
                targetDate = calendar.time
            } else if (reminderKm != null) {
                reminderType = "mileage"
                targetMileage = installationMileage + reminderKm
            }

            if (reminderType.isNotEmpty()) {
                val reminder = com.example.autouchet.Models.Reminder(
                    carId = currentCarId,
                    title = reminderTitle,
                    type = reminderType,
                    targetDate = targetDate,
                    targetMileage = targetMileage,
                    isCompleted = false
                )

                database.reminderDao().insert(reminder)
            }
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

        if (mileage > 9999999) {
            Toast.makeText(this, "Пробег не может превышать 10 000 000 км", Toast.LENGTH_SHORT).show()
            return
        }
        if (mileage < 0) {
            Toast.makeText(this, "Пробег не может быть отрицательным", Toast.LENGTH_SHORT).show()
            return
        }

        if (shopName.length > 50) {
            Toast.makeText(this, "Название магазина не более 50 символов", Toast.LENGTH_SHORT).show()
            return
        }

        if (comment.length > 100) {
            Toast.makeText(this, "Комментарий не более 100 символов", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount > 100000000) {
            Toast.makeText(this, "Сумма не может превышать 100 000 000", Toast.LENGTH_SHORT).show()
            return
        }
        if (amount <= 0) {
            Toast.makeText(this, "Сумма должна быть больше 0", Toast.LENGTH_SHORT).show()
            return
        }

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

        if (category == "Шины" && !validateTireData()) {
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
            val existingTires = database.tireReplacementDao().getByCar(currentCarId)
            val existingTireForThisExpense = existingTires.find { it.expenseId == expenseId }
            val replaceAllTires = binding.replaceAllTiresCheckBox.isChecked

            if (existingTireForThisExpense != null) {
                if (replaceAllTires) {
                    val activeTires = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive }

                    activeTires.forEach { tire ->
                        database.tireReplacementDao().update(
                            tire.copy(
                                isActive = false,
                                notes = if (tire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())} (редактирование)"
                                else "${tire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())} (редактирование)"
                            )
                        )
                    }
                } else {
                    val oldTiresSameType = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive && it.tireType == tireType && it.id != existingTireForThisExpense.id }

                    oldTiresSameType.forEach { oldTire ->
                        database.tireReplacementDao().update(
                            oldTire.copy(
                                isActive = false,
                                notes = if (oldTire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())} (редактирование)"
                                else "${oldTire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())} (редактирование)"
                            )
                        )
                    }
                }

                val updatedTire = existingTireForThisExpense.copy(
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
                if (replaceAllTires) {
                    val activeTires = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive }

                    activeTires.forEach { tire ->
                        database.tireReplacementDao().update(
                            tire.copy(
                                isActive = false,
                                notes = if (tire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                else "${tire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                            )
                        )
                    }

                    listOf("Зимняя", "Летняя").forEach { type ->
                        val tireReplacement = TireReplacement(
                            carId = currentCarId,
                            tireType = type,
                            brand = brand,
                            model = model,
                            size = size,
                            installationDate = installationDate,
                            installationMileage = installationMileage,
                            price = 0.0,
                            expectedLifetimeYears = expectedYears,
                            expectedLifetimeKm = expectedKm,
                            isActive = true,
                            expenseId = if (type == tireType) expenseId else null
                        )
                        database.tireReplacementDao().insert(tireReplacement)
                    }
                } else {
                    val oldTiresSameType = database.tireReplacementDao().getByCar(currentCarId)
                        .filter { it.isActive && it.tireType == tireType }

                    oldTiresSameType.forEach { oldTire ->
                        database.tireReplacementDao().update(
                            oldTire.copy(
                                isActive = false,
                                notes = if (oldTire.notes.isNullOrEmpty()) "Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                                else "${oldTire.notes}. Заменены ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                            )
                        )
                    }
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
                        isActive = true,
                        expenseId = expenseId
                    )
                    database.tireReplacementDao().insert(tireReplacement)
                }
            }
            val allTiresAfterUpdate = database.tireReplacementDao().getByCar(currentCarId)
            android.util.Log.d("TireDebug", "=== После обновления шин ===")
            allTiresAfterUpdate.forEach { tire ->
                android.util.Log.d("TireDebug",
                    "ID: ${tire.id}, Тип: ${tire.tireType}, Активна: ${tire.isActive}, expenseId: ${tire.expenseId}"
                )
            }
        }
    }
}