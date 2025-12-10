package com.example.autouchet.Views

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.TaxCalculator
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Car
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityCarSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*

class CarSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCarSettingsBinding
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadCarData()
        setupClickListeners()
    }

    private fun setupUI() {
        // Настройка выпадающих списков
        val regions = TaxCalculator.getRegions()
        val regionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            regions
        )
        binding.regionAutoCompleteTextView.setAdapter(regionAdapter)

        // Предзаполненные значения для демо
        binding.brandEditText.setText("Volkswagen")
        binding.modelEditText.setText("Golf")
        binding.yearEditText.setText("2018")
        binding.horsepowerEditText.setText("150")
        binding.regionAutoCompleteTextView.setText("Свердловская область", false)
        binding.mileageEditText.setText("85430")

        calculateTax()
    }

    private fun loadCarData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val cars = database.carDao().getAll()

            withContext(Dispatchers.Main) {
                if (cars.isNotEmpty()) {
                    val car = cars.first()
                    binding.brandEditText.setText(car.brand)
                    binding.modelEditText.setText(car.model)
                    binding.yearEditText.setText(car.year.toString())
                    binding.horsepowerEditText.setText(car.horsepower.toString())
                    binding.regionAutoCompleteTextView.setText(car.region, false)
                    binding.mileageEditText.setText(car.currentMileage.toString())
                    calculateTax()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            saveCar()
        }

        binding.calculateTaxButton.setOnClickListener {
            calculateTax()
        }

        binding.horsepowerEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                calculateTax()
            }
        }

        binding.regionAutoCompleteTextView.setOnItemClickListener { _, _, _, _ ->
            calculateTax()
        }

        binding.addAnotherCarButton.setOnClickListener {
            addAnotherCar()
        }

        binding.exportDataButton.setOnClickListener {
            exportAllData()
        }

        binding.backupButton.setOnClickListener {
            createBackup()
        }
    }

    private fun calculateTax() {
        val horsepowerText = binding.horsepowerEditText.text.toString()
        val region = binding.regionAutoCompleteTextView.text.toString()

        if (horsepowerText.isNotEmpty() && region.isNotEmpty()) {
            val horsepower = horsepowerText.toIntOrNull() ?: 0
            val yearlyTax = TaxCalculator.calculateYearlyTax(horsepower, region)
            val monthlyTax = yearlyTax / 12

            val rate = TaxCalculator.taxRates[region] ?: 25.0

            binding.taxRateTextView.text = "Ставка в регионе: ${String.format("%.0f", rate)} ₽/л.с."
            binding.yearlyTaxTextView.text = "Годовой налог: ${currencyFormat.format(yearlyTax)}"
            binding.monthlyTaxTextView.text = "Ежемесячно: ${currencyFormat.format(monthlyTax)}"
        }
    }

    private fun saveCar() {
        val brand = binding.brandEditText.text.toString()
        val model = binding.modelEditText.text.toString()
        val yearText = binding.yearEditText.text.toString()
        val horsepowerText = binding.horsepowerEditText.text.toString()
        val region = binding.regionAutoCompleteTextView.text.toString()
        val mileageText = binding.mileageEditText.text.toString()

        if (brand.isEmpty() || model.isEmpty() || yearText.isEmpty() ||
            horsepowerText.isEmpty() || region.isEmpty() || mileageText.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        val year = yearText.toIntOrNull() ?: 2018
        val horsepower = horsepowerText.toIntOrNull() ?: 150
        val mileage = mileageText.toIntOrNull() ?: 0

        val car = Car(
            brand = brand,
            model = model,
            year = year,
            horsepower = horsepower,
            region = region,
            currentMileage = mileage
        )

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val existingCars = database.carDao().getAll()

            if (existingCars.isEmpty()) {
                database.carDao().insert(car)
            } else {
                // Обновляем существующую запись
                val existingCar = existingCars.first()
                val updatedCar = existingCar.copy(
                    brand = brand,
                    model = model,
                    year = year,
                    horsepower = horsepower,
                    region = region,
                    currentMileage = mileage
                )
                database.carDao().update(updatedCar)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CarSettingsActivity,
                    "Данные автомобиля сохранены",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun addAnotherCar() {
        Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun exportAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@CarSettingsActivity)
            val cars = database.carDao().getAll()
            val expenses = if (cars.isNotEmpty()) {
                database.expenseDao().getRecentByCar(cars.first().id, 10000)
            } else {
                emptyList()
            }

            // Сохранение данных в CSV
            if (expenses.isNotEmpty()) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val csv = StringBuilder()

                csv.append("Дата;Категория;Сумма;Пробег;Комментарий;Магазин\n")

                for (expense in expenses) {
                    csv.append("${dateFormat.format(expense.date)};")
                    csv.append("${expense.category};")
                    csv.append("${expense.amount};")
                    csv.append("${expense.mileage};")
                    csv.append("${expense.comment.replace(";", ",")};")
                    csv.append("${expense.shopName}\n")
                }

                val fileName = "autouchet_full_export_${java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
                val file = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ).resolve(fileName)

                file.writeText(csv.toString())

                runOnUiThread {
                    Toast.makeText(
                        this@CarSettingsActivity,
                        "Все данные экспортированы в Загрузки",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createBackup() {
        Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show()
    }
}