package com.example.autouchet.Views

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Car
import com.example.autouchet.Models.Expense
import com.example.autouchet.databinding.ActivityCarSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CarSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCarSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var currentCarId: Int = -1
    private var isEditingExisting = false

    companion object {
        private const val PREFS_NAME = "AutoUchetPrefs"
        private const val KEY_CURRENT_CAR_ID = "current_car_id"
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentCarId = intent.getIntExtra("CURRENT_CAR_ID", -1)
        if (currentCarId == -1) {
            currentCarId = sharedPreferences.getInt(KEY_CURRENT_CAR_ID, -1)
        }

        setupUI()
        loadCarData()
        setupClickListeners()
    }

    private fun setupUI() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.yearEditText.setText(currentYear.toString())
    }

    private fun loadCarData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)

            if (currentCarId != -1) {
                val car = database.carDao().getById(currentCarId)
                withContext(Dispatchers.Main) {
                    if (car != null) {
                        isEditingExisting = true
                        loadCarIntoForm(car)
                        binding.toolbarTitle.text = "РЕДАКТИРОВАНИЕ АВТОМОБИЛЯ"
                        binding.addAnotherCarButton.text = "✚ ДОБАВИТЬ ЕЩЕ АВТОМОБИЛЬ"
                    } else {
                        isEditingExisting = false
                        binding.toolbarTitle.text = "НОВЫЙ АВТОМОБИЛЬ"
                        binding.addAnotherCarButton.text = "✚ СОЗДАТЬ АВТОМОБИЛЬ"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isEditingExisting = false
                    binding.toolbarTitle.text = "НОВЫЙ АВТОМОБИЛЬ"
                    binding.addAnotherCarButton.text = "✚ СОЗДАТЬ АВТОМОБИЛЬ"
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveCar() }
        binding.addAnotherCarButton.setOnClickListener { showAddCarDialog() }
        binding.exportDataButton.setOnClickListener { exportAllData() }
        binding.backupButton.setOnClickListener { createBackup() }
    }

    private fun saveCar() {
        val brand = binding.brandEditText.text.toString().trim()
        val model = binding.modelEditText.text.toString().trim()
        val yearText = binding.yearEditText.text.toString()
        val mileageText = binding.mileageEditText.text.toString()

        if (brand.isEmpty() || model.isEmpty() || yearText.isEmpty() || mileageText.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        val year = yearText.toIntOrNull()
        val mileage = mileageText.toIntOrNull()

        if (year == null || year < 1900 || year > Calendar.getInstance().get(Calendar.YEAR) + 1) {
            Toast.makeText(this, "Введите корректный год выпуска", Toast.LENGTH_SHORT).show()
            return
        }

        if (mileage == null || mileage < 0) {
            Toast.makeText(this, "Введите корректный пробег", Toast.LENGTH_SHORT).show()
            return
        }

        // Устанавливаем мощность по умолчанию 0, так как поля больше нет
        val horsepower = 0

        val car = Car(
            id = if (isEditingExisting) currentCarId else 0,
            brand = brand,
            model = model,
            year = year,
            horsepower = horsepower, // Добавляем мощность по умолчанию
            region = "",
            currentMileage = mileage
        )

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)

            if (isEditingExisting && currentCarId != -1) {
                database.carDao().update(car)
                sharedPreferences.edit().putInt(KEY_CURRENT_CAR_ID, car.id).apply()
            } else {
                val newId = database.carDao().insert(car).toInt()
                if (sharedPreferences.getInt(KEY_CURRENT_CAR_ID, -1) == -1) {
                    sharedPreferences.edit().putInt(KEY_CURRENT_CAR_ID, newId).apply()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CarSettingsActivity,
                    if (isEditingExisting) "Данные автомобиля обновлены" else "Автомобиль добавлен",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun showAddCarDialog() {
        AlertDialog.Builder(this)
            .setTitle("Добавить автомобиль")
            .setMessage("Вы хотите создать новый автомобиль или выбрать существующий?")
            .setPositiveButton("Новый автомобиль") { _, _ ->
                createNewCar()
            }
            .setNegativeButton("Выбрать существующий") { _, _ ->
                showCarSelectionDialog()
            }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun createNewCar() {
        binding.brandEditText.text?.clear()
        binding.modelEditText.text?.clear()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.yearEditText.setText(currentYear.toString())
        binding.mileageEditText.text?.clear()

        isEditingExisting = false
        currentCarId = -1
        binding.toolbarTitle.text = "НОВЫЙ АВТОМОБИЛЬ"
        binding.addAnotherCarButton.text = "✚ ДОБАВИТЬ ЕЩЕ АВТОМОБИЛЬ"

        Toast.makeText(this, "Заполните данные нового автомобиля", Toast.LENGTH_SHORT).show()
    }

    private fun showCarSelectionDialog() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val cars = database.carDao().getAll()

            withContext(Dispatchers.Main) {
                if (cars.isEmpty()) {
                    Toast.makeText(this@CarSettingsActivity, "Нет сохраненных автомобилей", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val carNames = cars.map { it.getFullName() }.toTypedArray()

                AlertDialog.Builder(this@CarSettingsActivity)
                    .setTitle("Выберите автомобиль")
                    .setItems(carNames) { _, which ->
                        val selectedCar = cars[which]
                        sharedPreferences.edit().putInt(KEY_CURRENT_CAR_ID, selectedCar.id).apply()
                        loadCarIntoForm(selectedCar)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
    }

    private fun loadCarIntoForm(car: Car) {
        currentCarId = car.id
        isEditingExisting = true

        binding.brandEditText.setText(car.brand)
        binding.modelEditText.setText(car.model)
        binding.yearEditText.setText(car.year.toString())
        binding.mileageEditText.setText(car.currentMileage.toString())

        binding.toolbarTitle.text = "РЕДАКТИРОВАНИЕ АВТОМОБИЛЯ"

        Toast.makeText(this, "Загружены данные: ${car.getFullName()}", Toast.LENGTH_SHORT).show()
    }

    private fun exportAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val cars = database.carDao().getAll()

            if (cars.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CarSettingsActivity, "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val csv = StringBuilder()

            // Добавляем BOM (Byte Order Mark) для UTF-8, чтобы Excel правильно определил кодировку
            csv.append("\uFEFF")

            // Экспорт автомобилей
            csv.append("=== АВТОМОБИЛИ ===\n")
            csv.append("ID;Марка;Модель;Год;Пробег\n")
            for (car in cars) {
                csv.append("${car.id};${escapeCsv(car.brand)};${escapeCsv(car.model)};${car.year};")
                csv.append("${car.currentMileage}\n")
            }

            csv.append("\n=== РАСХОДЫ ===\n")
            csv.append("ID;АвтоID;Дата;Категория;Сумма;Пробег;Комментарий;Магазин\n")

            for (car in cars) {
                val expenses = database.expenseDao().getAllByCar(car.id)
                for (expense in expenses) {
                    csv.append("${expense.id};${expense.carId};${dateFormat.format(expense.date)};")
                    csv.append("${escapeCsv(expense.category)};${expense.amount};${expense.mileage};")
                    csv.append("${escapeCsv(expense.comment)};")
                    csv.append("${escapeCsv(expense.shopName)}\n")
                }
            }

            try {
                val fileName = "autouchet_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                // Используем UTF-8 с BOM для записи файла
                file.writeText(csv.toString(), Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "✅ Данные сохранены в Загрузки:\n$fileName",
                        Toast.LENGTH_LONG
                    ).show()

                    AlertDialog.Builder(this@CarSettingsActivity)
                        .setTitle("Экспорт завершен")
                        .setMessage("Файл сохранен в папку Загрузки. Хотите поделиться им?")
                        .setPositiveButton("Поделиться") { _, _ ->
                            shareFile(file)
                        }
                        .setNegativeButton("Открыть папку") { _, _ ->
                            openDownloadsFolder()
                        }
                        .setNeutralButton("Закрыть", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "Ошибка экспорта: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun escapeCsv(text: String): String {
        if (text.isEmpty()) return ""

        // Экранируем кавычки и добавляем кавычки если есть точка с запятой, кавычки или перенос строки
        val escapedText = text.replace("\"", "\"\"")

        return if (escapedText.contains(";") || escapedText.contains("\"") || escapedText.contains("\n") || escapedText.contains("\r")) {
            "\"$escapedText\""
        } else {
            escapedText
        }
    }

    private fun openDownloadsFolder() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val uri = Uri.fromFile(downloadsDir)

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "resource/folder")

            if (intent.resolveActivity(packageManager) == null) {
                val fileManagerIntent = Intent(Intent.ACTION_VIEW)
                fileManagerIntent.data = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                startActivity(fileManagerIntent)
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Откройте Проводник → Внутреннее хранилище → Download",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
        } catch (e: Exception) {
            try {
                val uri = Uri.fromFile(file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
                startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Файл сохранен: ${file.absolutePath}\nОткройте его через Проводник",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createBackup() {
        AlertDialog.Builder(this)
            .setTitle("Резервное копирование")
            .setMessage("Что вы хотите сделать?")
            .setPositiveButton("Создать бэкап") { _, _ ->
                createBackupFile()
            }
            .setNegativeButton("Восстановить из бэкапа") { _, _ ->
                restoreFromBackup()
            }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun createBackupFile() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val cars = database.carDao().getAll()

            if (cars.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CarSettingsActivity, "Нет данных для резервного копирования", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val backupData = mutableMapOf<String, Any>()

            // Сохраняем автомобили (мощность всё ещё есть в модели Car)
            backupData["cars"] = cars.map { car ->
                mapOf(
                    "id" to car.id,
                    "brand" to car.brand,
                    "model" to car.model,
                    "year" to car.year,
                    "horsepower" to car.horsepower,
                    "region" to car.region,
                    "currentMileage" to car.currentMileage
                )
            }

            val allExpenses = mutableListOf<Map<String, Any>>()
            for (car in cars) {
                val expenses = database.expenseDao().getAllByCar(car.id)
                allExpenses.addAll(expenses.map { expense ->
                    mapOf(
                        "id" to expense.id,
                        "carId" to expense.carId,
                        "amount" to expense.amount,
                        "category" to expense.category,
                        "date" to expense.date.time,
                        "mileage" to expense.mileage,
                        "comment" to expense.comment,
                        "shopName" to expense.shopName
                    )
                })
            }
            backupData["expenses"] = allExpenses

            val json = com.google.gson.Gson().toJson(backupData)

            try {
                val fileName = "autouchet_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                file.writeText(json)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "✅ Резервная копия создана в Загрузках:\n$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "Ошибка создания бэкапа: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun restoreFromBackup() {
        getContent.launch("*/*")
    }

    private fun importBackup(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    val backupData = com.google.gson.Gson().fromJson(json, Map::class.java)

                    val database = AppDatabase.getDatabase(this@CarSettingsActivity)

                    database.carDao().getAll().forEach { database.carDao().delete(it.id) }
                    database.expenseDao().getAll().forEach { database.expenseDao().delete(it) }

                    @Suppress("UNCHECKED_CAST")
                    val carsData = backupData["cars"] as? List<Map<String, Any>>
                    carsData?.forEach { carMap ->
                        val car = Car(
                            id = (carMap["id"] as Double).toInt(),
                            brand = carMap["brand"] as String,
                            model = carMap["model"] as String,
                            year = (carMap["year"] as Double).toInt(),
                            horsepower = (carMap["horsepower"] as Double).toInt(),
                            region = carMap["region"] as String,
                            currentMileage = (carMap["currentMileage"] as Double).toInt()
                        )
                        database.carDao().insert(car)
                    }

                    @Suppress("UNCHECKED_CAST")
                    val expensesData = backupData["expenses"] as? List<Map<String, Any>>
                    expensesData?.forEach { expenseMap ->
                        val expense = Expense(
                            id = (expenseMap["id"] as Double).toInt(),
                            carId = (expenseMap["carId"] as Double).toInt(),
                            amount = expenseMap["amount"] as Double,
                            category = expenseMap["category"] as String,
                            date = Date((expenseMap["date"] as Double).toLong()),
                            mileage = (expenseMap["mileage"] as Double).toInt(),
                            comment = expenseMap["comment"] as? String ?: "",
                            shopName = expenseMap["shopName"] as? String ?: ""
                        )
                        database.expenseDao().insert(expense)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CarSettingsActivity,
                            "Данные восстановлены из резервной копии",
                            Toast.LENGTH_LONG
                        ).show()
                        loadCarData()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "Ошибка восстановления: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}