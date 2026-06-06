package com.example.autouchet.Views

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.CategoryController
import com.example.autouchet.Controllers.SyncController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Expense
import com.example.autouchet.Models.ExpenseCategory
import com.example.autouchet.Models.TireReplacement
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityAddExpenseBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var categoryController: CategoryController
    private lateinit var syncController: SyncController

    private var currentCarId: Int = -1
    private var currentMileage: Int = 0
    private var isEditMode: Boolean = false
    private var expenseToEditId: Int = -1

    private val dateFormatInput = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var categories = mutableListOf<ExpenseCategory>()
    private var categoryNames = mutableListOf<String>()
    private lateinit var categoryAdapter: ArrayAdapter<String>

    private val tireTypes = listOf("Зимняя", "Летняя", "Всесезонная")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryController = CategoryController(this)
        syncController = SyncController(this)

        currentCarId = SharedPrefsHelper.getCurrentCarId(this)

        if (currentCarId == -1) {
            Toast.makeText(this, "Сначала добавьте автомобиль", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isEditMode = intent.getBooleanExtra("edit_mode", false)
        expenseToEditId = intent.getIntExtra("expense_id", -1)

        setupUI()
        setupClickListeners()
        loadCategories()
        loadCurrentCar()

        if (isEditMode && expenseToEditId != -1) {
            loadExpenseForEditing()
        }
    }

    private fun setupUI() {
        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryNames)
        binding.categoryAutoCompleteTextView.setAdapter(categoryAdapter)

        val tireTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tireTypes)
        binding.tireTypeAutoCompleteTextView.setAdapter(tireTypeAdapter)

        binding.dateEditText.setText(dateFormatInput.format(Date()))
        binding.toolbarTitle.text = if (isEditMode) "РЕДАКТИРОВАНИЕ РАСХОДА" else "НОВЫЙ РАСХОД"
        binding.saveButton.text = if (isEditMode) "ОБНОВИТЬ" else "СОХРАНИТЬ"

        binding.categoryAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories.getOrNull(position)
            if (selectedCategory?.name == "Шины") {
                showTireTemplate()
            } else {
                hideTireTemplate()
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveOrUpdateExpense() }
        binding.dateEditText.setOnClickListener { showDatePicker() }

        binding.createReminderCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.reminderLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.manageCategoriesButton.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        binding.searchPartsButton.setOnClickListener {
            val intent = Intent(this, PartsSearchActivity::class.java)
            startActivity(intent)
        }
    }

    // Добавьте этот метод в класс (вне setupClickListeners)
    private fun suggestPartsSearch(expenseId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Поиск запчастей")
            .setMessage("Хотите найти эту запчасть по лучшей цене?")
            .setPositiveButton("Найти") { _, _ ->
                val intent = Intent(this, PartsSearchActivity::class.java)
                intent.putExtra("expense_id", expenseId)
                startActivity(intent)
            }
            .setNegativeButton("Позже", null)
            .show()
    }



    private fun loadCategories() {
        scope.launch {
            try {
                val loadedCategories = withContext(Dispatchers.IO) {
                    categoryController.getAllCategories()
                }
                categories.clear()
                categories.addAll(loadedCategories)
                categoryNames.clear()
                categoryNames.addAll(loadedCategories.map { "${it.icon} ${it.name}" })
                categoryAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("AddExpense", "Error loading categories: ${e.message}")
            }
        }
    }

    private fun loadCurrentCar() {
        scope.launch {
            try {
                val car = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@AddExpenseActivity).carDao().getById(currentCarId)
                }
                car?.let {
                    currentMileage = it.currentMileage
                    if (!isEditMode) {
                        binding.mileageEditText.setText(currentMileage.toString())
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error loading car: ${e.message}")
            }
        }
    }

    private fun loadExpenseForEditing() {
        scope.launch {
            try {
                val expense = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@AddExpenseActivity).expenseDao().getById(expenseToEditId)
                }

                expense?.let {
                    binding.amountEditText.setText(String.format("%.2f", it.amount))

                    val category = categories.find { c -> c.name == it.category }
                    val displayText = category?.let { c -> "${c.icon} ${c.name}" } ?: it.category
                    binding.categoryAutoCompleteTextView.setText(displayText, false)

                    binding.dateEditText.setText(dateFormatInput.format(it.date))
                    binding.mileageEditText.setText(it.mileage.toString())
                    binding.shopNameEditText.setText(it.shopName)
                    binding.commentEditText.setText(it.comment)

                    if (it.category == "Шины") {
                        showTireTemplate()
                        loadTireInfo()
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error loading expense: ${e.message}")
            }
        }
    }

    private fun loadTireInfo() {
        scope.launch {
            try {
                val tires = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@AddExpenseActivity)
                        .tireReplacementDao()
                        .getByCar(currentCarId)
                }

                val tireForThisExpense = tires.find { it.expenseId == expenseToEditId }
                tireForThisExpense?.let { tire ->
                    binding.tireTypeAutoCompleteTextView.setText(tire.tireType, false)
                    binding.tireBrandEditText.setText(tire.brand)
                    binding.tireModelEditText.setText(tire.model)
                    binding.tireSizeEditText.setText(tire.size)
                    binding.tireExpectedYearsEditText.setText(tire.expectedLifetimeYears.toString())
                    binding.tireExpectedKmEditText.setText(tire.expectedLifetimeKm.toString())
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error loading tire info: ${e.message}")
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentText = binding.dateEditText.text.toString()

        if (currentText.isNotEmpty()) {
            try {
                dateFormatInput.parse(currentText)?.let { calendar.time = it }
            } catch (e: Exception) {
                // Use current date if parsing fails
            }
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                binding.dateEditText.setText(dateFormatInput.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTireTemplate() {
        binding.tireTemplateLayout.visibility = View.VISIBLE
        binding.replaceAllTiresCheckBox.visibility = View.VISIBLE
    }

    private fun hideTireTemplate() {
        binding.tireTemplateLayout.visibility = View.GONE
        binding.replaceAllTiresCheckBox.visibility = View.GONE
    }

    private fun getSelectedCategory(): String {
        val selectedText = binding.categoryAutoCompleteTextView.text.toString()
        val category = categories.find { "${it.icon} ${it.name}" == selectedText }
        return category?.name ?: selectedText
    }

    private fun saveOrUpdateExpense() {
        if (isEditMode) {
            updateExpense()
        } else {
            saveExpense()
        }
    }

    private fun saveExpense() {
        if (!validateInput()) return

        val amount = binding.amountEditText.text.toString().toDouble()
        val category = getSelectedCategory()
        val mileage = binding.mileageEditText.text.toString().toInt()
        val comment = binding.commentEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()
        val selectedDate = try {
            dateFormatInput.parse(binding.dateEditText.text.toString()) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        scope.launch {
            try {
                val expense = Expense(
                    carId = currentCarId,
                    amount = amount,
                    category = category,
                    date = selectedDate,
                    mileage = mileage,
                    comment = comment,
                    shopName = shopName,
                    updatedAt = System.currentTimeMillis()
                )

                val expenseId = withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddExpenseActivity)
                    val localId = db.expenseDao().insert(expense)

                    // Обновляем пробег автомобиля
                    val car = db.carDao().getById(currentCarId)
                    car?.let {
                        if (mileage > it.currentMileage) {
                            db.carDao().update(it.copy(currentMileage = mileage))
                        }
                    }

                    localId
                }

                // Сохраняем информацию о шинах, если нужно
                if (category == "Шины") {
                    saveTireInfo(expenseId.toInt(), selectedDate, mileage)

                    if (binding.createReminderCheckBox.isChecked) {
                        createTireReminder(selectedDate, mileage)
                    }
                }

                // ДОБАВЬТЕ ЭТОТ КОД:
                // Автоматически предлагаем поиск запчастей
                if (category == "Ремонт" || category == "Обслуживание") {
                    suggestPartsSearch(expenseId.toInt())
                }

                Toast.makeText(this@AddExpenseActivity, "Расход сохранён", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e("AddExpense", "Error saving expense: ${e.message}")
                Toast.makeText(this@AddExpenseActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateExpense() {
        if (!validateInput()) return

        val amount = binding.amountEditText.text.toString().toDouble()
        val category = getSelectedCategory()
        val mileage = binding.mileageEditText.text.toString().toInt()
        val comment = binding.commentEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()
        val selectedDate = try {
            dateFormatInput.parse(binding.dateEditText.text.toString()) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddExpenseActivity)
                    val existingExpense = db.expenseDao().getById(expenseToEditId)

                    existingExpense?.let {
                        val updatedExpense = it.copy(
                            amount = amount,
                            category = category,
                            date = selectedDate,
                            mileage = mileage,
                            comment = comment,
                            shopName = shopName,
                            updatedAt = System.currentTimeMillis()
                        )

                        db.expenseDao().update(updatedExpense)

                        // Обновляем пробег автомобиля
                        val car = db.carDao().getById(currentCarId)
                        car?.let { carData ->
                            if (mileage > carData.currentMileage) {
                                db.carDao().update(carData.copy(currentMileage = mileage))
                            }
                        }

                        // Синхронизируем с Firebase
                        syncController.setGroupId(SharedPrefsHelper.getGroupId(this@AddExpenseActivity) ?: "")
                        syncController.syncExpense(updatedExpense)
                    }
                }

                if (category == "Шины") {
                    updateTireInfo(selectedDate, mileage)
                }

                Toast.makeText(this@AddExpenseActivity, "Расход обновлён", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                Log.e("AddExpense", "Error updating expense: ${e.message}")
                Toast.makeText(this@AddExpenseActivity, "Ошибка обновления: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncToCloud(expense: Expense) {
        scope.launch {
            try {
                val groupId = SharedPrefsHelper.getGroupId(this@AddExpenseActivity)
                if (groupId != null) {
                    withContext(Dispatchers.IO) {
                        syncController.setGroupId(groupId)
                        syncController.syncExpense(expense)
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error syncing to cloud: ${e.message}")
            }
        }
    }

    private fun validateInput(): Boolean {
        val amountText = binding.amountEditText.text.toString()
        val category = getSelectedCategory()
        val mileageText = binding.mileageEditText.text.toString()
        val dateText = binding.dateEditText.text.toString()
        val shopName = binding.shopNameEditText.text.toString()
        val comment = binding.commentEditText.text.toString()

        if (amountText.isEmpty() || category.isEmpty() || mileageText.isEmpty() || dateText.isEmpty()) {
            Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
            return false
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
            return false
        }

        if (amount > 100000000) {
            Toast.makeText(this, "Сумма не может превышать 100 000 000", Toast.LENGTH_SHORT).show()
            return false
        }

        val mileage = mileageText.toIntOrNull()
        if (mileage == null || mileage < 0) {
            Toast.makeText(this, "Введите корректный пробег", Toast.LENGTH_SHORT).show()
            return false
        }

        if (mileage > 9999999) {
            Toast.makeText(this, "Пробег не может превышать 10 000 000 км", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val selectedDate = dateFormatInput.parse(dateText) ?: throw Exception()
            val today = Calendar.getInstance().apply {
                time = Date()
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time

            if (selectedDate.after(today)) {
                Toast.makeText(this, "Нельзя добавить расход на будущую дату", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Введите корректную дату", Toast.LENGTH_SHORT).show()
            return false
        }

        if (shopName.length > 50) {
            Toast.makeText(this, "Название магазина не более 50 символов", Toast.LENGTH_SHORT).show()
            return false
        }

        if (comment.length > 100) {
            Toast.makeText(this, "Комментарий не более 100 символов", Toast.LENGTH_SHORT).show()
            return false
        }

        if (category == "Шины" && !validateTireData()) {
            return false
        }

        return true
    }

    private fun validateTireData(): Boolean {
        val tireType = binding.tireTypeAutoCompleteTextView.text.toString()
        val brand = binding.tireBrandEditText.text.toString()
        val size = binding.tireSizeEditText.text.toString()
        val expectedYearsText = binding.tireExpectedYearsEditText.text.toString()
        val expectedKmText = binding.tireExpectedKmEditText.text.toString()

        if (tireType.isEmpty() || brand.isEmpty() || size.isEmpty()) {
            Toast.makeText(this, "Заполните данные о шинах", Toast.LENGTH_SHORT).show()
            return false
        }

        val expectedYears = expectedYearsText.toIntOrNull()
        if (expectedYears == null || expectedYears < 1 || expectedYears > 10) {
            Toast.makeText(this, "Срок службы должен быть от 1 до 10 лет", Toast.LENGTH_SHORT).show()
            return false
        }

        val expectedKm = expectedKmText.toIntOrNull()
        if (expectedKm == null || expectedKm < 1000 || expectedKm > 100000) {
            Toast.makeText(this, "Ожидаемый пробег должен быть от 1000 до 100000 км", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveTireInfo(expenseId: Int, installationDate: Date, installationMileage: Int) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddExpenseActivity)
                    val tireType = binding.tireTypeAutoCompleteTextView.text.toString()
                    val brand = binding.tireBrandEditText.text.toString()
                    val model = binding.tireModelEditText.text.toString()
                    val size = binding.tireSizeEditText.text.toString()
                    val expectedYears = binding.tireExpectedYearsEditText.text.toString().toIntOrNull() ?: 4
                    val expectedKm = binding.tireExpectedKmEditText.text.toString().toIntOrNull() ?: 60000
                    val replaceAll = binding.replaceAllTiresCheckBox.isChecked

                    if (replaceAll) {
                        // Деактивируем все активные шины
                        val activeTires = db.tireReplacementDao().getByCar(currentCarId)
                            .filter { it.isActive }
                        activeTires.forEach { tire ->
                            db.tireReplacementDao().update(tire.copy(isActive = false))
                        }

                        // Создаем новые записи для обоих типов
                        listOf("Зимняя", "Летняя").forEach { type ->
                            db.tireReplacementDao().insert(
                                TireReplacement(
                                    carId = currentCarId,
                                    tireType = type,
                                    brand = brand,
                                    model = model,
                                    size = size,
                                    installationDate = installationDate,
                                    installationMileage = installationMileage,
                                    expectedLifetimeYears = expectedYears,
                                    expectedLifetimeKm = expectedKm,
                                    isActive = true,
                                    expenseId = if (type == tireType) expenseId else null
                                )
                            )
                        }
                    } else {
                        // Деактивируем шины того же типа
                        val oldTiresSameType = db.tireReplacementDao().getByCar(currentCarId)
                            .filter { it.isActive && it.tireType == tireType }
                        oldTiresSameType.forEach { tire ->
                            db.tireReplacementDao().update(tire.copy(isActive = false))
                        }

                        // Создаем новую запись
                        db.tireReplacementDao().insert(
                            TireReplacement(
                                carId = currentCarId,
                                tireType = tireType,
                                brand = brand,
                                model = model,
                                size = size,
                                installationDate = installationDate,
                                installationMileage = installationMileage,
                                expectedLifetimeYears = expectedYears,
                                expectedLifetimeKm = expectedKm,
                                isActive = true,
                                expenseId = expenseId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error saving tire info: ${e.message}")
            }
        }
    }

    private fun createTireReminder(installationDate: Date, installationMileage: Int) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddExpenseActivity)
                    val reminderYearsText = binding.reminderYearsEditText.text.toString()
                    val reminderKmText = binding.reminderKmEditText.text.toString()

                    if (reminderYearsText.isNotEmpty() || reminderKmText.isNotEmpty()) {
                        val reminder = com.example.autouchet.Models.Reminder(
                            carId = currentCarId,
                            title = "Замена шин",
                            type = when {
                                reminderYearsText.isNotEmpty() && reminderKmText.isNotEmpty() -> "combined"
                                reminderYearsText.isNotEmpty() -> "date"
                                else -> "mileage"
                            },
                            targetDate = if (reminderYearsText.isNotEmpty()) {
                                val calendar = Calendar.getInstance().apply {
                                    time = installationDate
                                    add(Calendar.YEAR, reminderYearsText.toInt())
                                }
                                calendar.time
                            } else null,
                            targetMileage = if (reminderKmText.isNotEmpty()) {
                                installationMileage + reminderKmText.toInt()
                            } else null,
                            isCompleted = false
                        )
                        db.reminderDao().insert(reminder)
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error creating tire reminder: ${e.message}")
            }
        }
    }

    private fun updateTireInfo(installationDate: Date, installationMileage: Int) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@AddExpenseActivity)
                    val existingTire = db.tireReplacementDao().getByCar(currentCarId)
                        .find { it.expenseId == expenseToEditId }

                    existingTire?.let {
                        val updatedTire = it.copy(
                            installationDate = installationDate,
                            installationMileage = installationMileage
                        )
                        db.tireReplacementDao().update(updatedTire)
                    }
                }
            } catch (e: Exception) {
                Log.e("AddExpense", "Error updating tire info: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}