package com.example.autouchet.Views

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Controllers.*
import com.example.autouchet.Models.*
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var expenseController: ExpenseController
    private lateinit var categoryController: CategoryController
    private lateinit var notificationManager: NotificationManager
    private var syncController: SyncController? = null
    private var firebaseController: FirebaseController? = null

    private var currentCar: Car? = null
    private var groupId: String? = null

    private val expenseAdapter = ExpenseAdapter()

    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    private val dateFormat = SimpleDateFormat("dd MMM", Locale("ru"))
    private val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))

    private var categoriesCache = listOf<ExpenseCategory>()

    private var isInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверка авторизации
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null || !SharedPrefsHelper.isLoggedIn(this)) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Проверяем наличие данных
        checkAndLoadData()
    }

    private fun checkAndLoadData() {
        showLoadingState()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Сначала проверяем локальные данные
                val hasLocalCar = SharedPrefsHelper.hasCar(this@MainActivity)
                val localCarId = SharedPrefsHelper.getCurrentCarId(this@MainActivity)

                // Проверяем существование данных в локальной БД
                val db = AppDatabase.getDatabase(this@MainActivity)
                val localCar = if (localCarId != -1) {
                    db.carDao().getById(localCarId)
                } else {
                    // Проверяем, есть ли вообще какие-то автомобили в БД
                    val allCars = db.carDao().getAll()
                    if (allCars.isNotEmpty()) {
                        // Восстанавливаем ID первого автомобиля
                        val firstCar = allCars.first()
                        SharedPrefsHelper.setCurrentCarId(this@MainActivity, firstCar.id)
                        SharedPrefsHelper.setHasCar(this@MainActivity, true)
                        firstCar
                    } else null
                }

                // Если есть локальные данные, используем их
                if (localCar != null) {
                    withContext(Dispatchers.Main) {
                        initializeApp()
                    }
                    return@launch
                }

                // Локальных данных нет - показываем диалог выбора
                withContext(Dispatchers.Main) {
                    showNoCarOptionsDialog()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking data: ${e.message}", e)

                // При ошибке проверяем локальные данные
                val db = AppDatabase.getDatabase(this@MainActivity)
                val allCars = db.carDao().getAll()

                if (allCars.isNotEmpty()) {
                    val firstCar = allCars.first()
                    SharedPrefsHelper.setCurrentCarId(this@MainActivity, firstCar.id)
                    SharedPrefsHelper.setHasCar(this@MainActivity, true)

                    withContext(Dispatchers.Main) {
                        initializeApp()
                        Toast.makeText(this@MainActivity, "Работа в локальном режиме", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showNoCarOptionsDialog()
                    }
                }
            }
        }
    }

    private fun showNoCarOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Добро пожаловать!")
            .setMessage("У вас пока нет автомобилей. Что хотите сделать?")
            .setPositiveButton("➕ Добавить автомобиль") { _, _ ->
                startActivity(Intent(this, CarSettingsActivity::class.java))
                finish()
            }
            .setNegativeButton("🔗 Присоединиться по коду") { _, _ ->
                showJoinGroupDialog()
            }
            .setNeutralButton("☁️ Восстановить из облака") { _, _ ->
                checkCloudData()
            }
            .setCancelable(false)
            .show()
    }

    private fun showJoinGroupDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Введите 8-значный код"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        input.setTextSize(18f)
        input.gravity = android.view.Gravity.CENTER
        input.setPadding(32, 32, 32, 32)

        AlertDialog.Builder(this)
            .setTitle("Присоединиться к группе")
            .setMessage("Введите код приглашения от владельца группы")
            .setView(input)
            .setPositiveButton("Присоединиться") { _, _ ->
                val code = input.text.toString().trim().uppercase()
                if (code.length == 8) {
                    joinGroupByCode(code)
                } else {
                    Toast.makeText(this, "Код должен быть 8 символов", Toast.LENGTH_SHORT).show()
                    showJoinGroupDialog()
                }
            }
            .setNegativeButton("Назад") { _, _ ->
                showNoCarOptionsDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun joinGroupByCode(code: String) {
        // Сначала инициализируем контроллеры
        initializeControllers()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = firebaseController?.joinCarGroup(code)

                withContext(Dispatchers.Main) {
                    result?.onSuccess { groupId ->
                        SharedPrefsHelper.setGroupId(this@MainActivity, groupId)
                        SharedPrefsHelper.setSyncEnabled(this@MainActivity, true)
                        Toast.makeText(this@MainActivity, "✅ Вы присоединились к группе!", Toast.LENGTH_SHORT).show()

                        isInitialized = true
                        setupUI()
                        loadCategories()
                        setupClickListeners()
                        loadGroupData(groupId)
                    }

                    result?.onFailure { error ->
                        Toast.makeText(this@MainActivity, "❌ ${error.message}", Toast.LENGTH_SHORT).show()
                        showNoCarOptionsDialog()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    showNoCarOptionsDialog()
                }
            }
        }
    }

    private fun loadGroupData(groupId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val firestore = FirebaseFirestore.getInstance()

                val groupDoc = firestore.collection("carGroups").document(groupId).get().await()
                if (!groupDoc.exists()) {
                    withContext(Dispatchers.Main) { showNoCarOptionsDialog() }
                    return@launch
                }

                // Загружаем автомобили
                val carsSnapshot = firestore.collection("carGroups/$groupId/cars")
                    .whereEqualTo("isDeleted", false)
                    .get()
                    .await()

                var savedCarId = -1

                for (carDoc in carsSnapshot.documents) {
                    val car = Car(
                        brand = carDoc.getString("brand") ?: "",
                        model = carDoc.getString("model") ?: "",
                        year = carDoc.getLong("year")?.toInt() ?: 0,
                        horsepower = carDoc.getLong("horsepower")?.toInt() ?: 0,
                        region = carDoc.getString("region") ?: "",
                        currentMileage = carDoc.getLong("currentMileage")?.toInt() ?: 0,
                        averageConsumption = carDoc.getDouble("averageConsumption") ?: 8.5,
                        cloudId = carDoc.id,
                        updatedAt = carDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    val localId = db.carDao().insert(car).toInt()
                    savedCarId = localId
                }

                if (savedCarId != -1) {
                    SharedPrefsHelper.setCurrentCarId(this@MainActivity, savedCarId)
                    SharedPrefsHelper.setHasCar(this@MainActivity, true)

                    // Загружаем расходы
                    val expensesSnapshot = firestore.collection("carGroups/$groupId/expenses")
                        .whereEqualTo("isDeleted", false)
                        .get()
                        .await()

                    for (expenseDoc in expensesSnapshot.documents) {
                        if (db.expenseDao().getByCloudId(expenseDoc.id) == null) {
                            val expense = Expense(
                                carId = savedCarId,
                                amount = expenseDoc.getDouble("amount") ?: 0.0,
                                category = expenseDoc.getString("category") ?: "",
                                categoryId = expenseDoc.getLong("categoryId")?.toInt(),
                                date = expenseDoc.getLong("date")?.let { Date(it) } ?: Date(),
                                mileage = expenseDoc.getLong("mileage")?.toInt() ?: 0,
                                comment = expenseDoc.getString("comment") ?: "",
                                shopName = expenseDoc.getString("shopName") ?: "",
                                cloudId = expenseDoc.id,
                                updatedAt = expenseDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            db.expenseDao().insert(expense)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    syncController?.setGroupId(groupId)
                    syncController?.startRealtimeSync { loadCarData() }
                    syncController?.startAutoSync()
                    loadCarData()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading group data: ${e.message}")
                withContext(Dispatchers.Main) { showNoCarOptionsDialog() }
            }
        }
    }

    private fun checkCloudData() {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Нет подключения к интернету", Toast.LENGTH_SHORT).show()
                    showNoCarOptionsDialog()
                }
                return@launch
            }

            val firestore = FirebaseFirestore.getInstance()
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (userId == null) {
                withContext(Dispatchers.Main) { showNoCarOptionsDialog() }
                return@launch
            }

            val userGroups = firestore.collection("carGroups")
                .whereArrayContains("members", userId)
                .get()
                .await()

            withContext(Dispatchers.Main) {
                if (userGroups.isEmpty) {
                    Toast.makeText(this@MainActivity, "Нет сохраненных данных в облаке", Toast.LENGTH_SHORT).show()
                    showNoCarOptionsDialog()
                } else {
                    showRestoreDataDialog(userGroups)
                }
            }
        }
    }

    private fun showRestoreDataDialogNonBlocking(userGroups: com.google.firebase.firestore.QuerySnapshot) {
        val groups = userGroups.documents

        AlertDialog.Builder(this)
            .setTitle("Найдены данные в облаке")
            .setMessage("У вас есть сохраненные данные в облаке. Хотите синхронизироваться с ними?")
            .setPositiveButton("Синхронизировать") { _, _ ->
                restoreDataFromCloud(groups)
            }
            .setNegativeButton("Продолжить локально") { _, _ ->
                SharedPrefsHelper.setSyncEnabled(this, false)
                Toast.makeText(this, "Продолжаем работу с локальными данными", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(true)
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showLoadingState() {
        binding.apply {
            carInfoCard.visibility = View.GONE
            monthStatsCard.visibility = View.GONE
            recentExpensesCard.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            addExpenseFab.visibility = View.GONE

            emptyStateText.text = "Проверка данных..."
            emptyStateSubtext.text = "Подключение к облаку..."
            addFirstCarButton.visibility = View.GONE
        }
    }

    private fun showRestoreDataDialog(userGroups: com.google.firebase.firestore.QuerySnapshot) {
        val groups = userGroups.documents

        AlertDialog.Builder(this)
            .setTitle("Найдены данные в облаке")
            .setMessage("У вас есть сохраненные данные в облаке. Что вы хотите сделать?")
            .setPositiveButton("Восстановить из облака") { _, _ ->
                restoreDataFromCloud(groups)
            }
            .setNegativeButton("Использовать локально") { _, _ ->
                // Продолжаем с локальными данными, отключаем синхронизацию
                SharedPrefsHelper.setSyncEnabled(this, false)
                SharedPrefsHelper.setGroupId(this, "")
                initializeApp()
                Toast.makeText(this, "Работа в локальном режиме", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Начать заново") { _, _ ->
                // Очищаем все и начинаем с чистого листа
                clearLocalData()
                showNoCarOptionsDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun clearLocalData() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            db.carDao().getAll().forEach { db.carDao().delete(it.id) }
            db.expenseDao().getAll().forEach { db.expenseDao().delete(it) }
            db.reminderDao().getAll().forEach { db.reminderDao().delete(it) }

            // Сохраняем groupId
            val savedGroupId = SharedPrefsHelper.getGroupId(this@MainActivity)
            SharedPrefsHelper.clearAll(this@MainActivity)
            if (savedGroupId != null) {
                SharedPrefsHelper.setGroupId(this@MainActivity, savedGroupId)
            }
        }
    }

    private fun restoreDataFromCloud(groups: List<com.google.firebase.firestore.DocumentSnapshot>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val firestore = FirebaseFirestore.getInstance()

                withContext(Dispatchers.Main) {
                    binding.emptyStateText.text = "Восстановление данных..."
                    binding.emptyStateSubtext.text = "Загрузка из облака..."
                }

                // Очищаем ВСЕ локальные данные перед восстановлением
                db.carDao().getAll().forEach { db.carDao().delete(it.id) }
                db.expenseDao().getAll().forEach { db.expenseDao().delete(it) }
                db.reminderDao().getAll().forEach { db.reminderDao().delete(it) }
                // Очищаем все категории, кроме дефолтных
                val categories = db.categoryDao().getAll()
                categories.forEach { db.categoryDao().delete(it) }

                var savedCarId = -1

                for (groupDoc in groups) {
                    val groupId = groupDoc.id

                    // Сохраняем groupId
                    SharedPrefsHelper.setGroupId(this@MainActivity, groupId)

                    // Загружаем автомобили из группы
                    val carsSnapshot = firestore.collection("carGroups/$groupId/cars")
                        .whereEqualTo("isDeleted", false)
                        .get()
                        .await()

                    for (carDoc in carsSnapshot.documents) {
                        val car = Car(
                            brand = carDoc.getString("brand") ?: "",
                            model = carDoc.getString("model") ?: "",
                            year = carDoc.getLong("year")?.toInt() ?: 0,
                            horsepower = carDoc.getLong("horsepower")?.toInt() ?: 0,
                            region = carDoc.getString("region") ?: "",
                            currentMileage = carDoc.getLong("currentMileage")?.toInt() ?: 0,
                            averageConsumption = carDoc.getDouble("averageConsumption") ?: 8.5,
                            cloudId = carDoc.id,
                            updatedAt = carDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )

                        val localId = db.carDao().insert(car).toInt()
                        savedCarId = localId
                        SharedPrefsHelper.setCurrentCarId(this@MainActivity, localId)
                    }

                    // Загружаем категории
                    val categoriesSnapshot = firestore.collection("carGroups/$groupId/categories")
                        .whereEqualTo("isDeleted", false)
                        .get()
                        .await()

                    for (catDoc in categoriesSnapshot.documents) {
                        val existing = db.categoryDao().getByCloudId(catDoc.id)
                        if (existing == null) {
                            val category = ExpenseCategory(
                                name = catDoc.getString("name") ?: "",
                                icon = catDoc.getString("icon") ?: "💰",
                                color = catDoc.getLong("color")?.toInt() ?: 0xFF9E9E9E.toInt(),
                                isDefault = catDoc.getBoolean("isDefault") ?: false,
                                sortOrder = catDoc.getLong("sortOrder")?.toInt() ?: 0,
                                cloudId = catDoc.id,
                                updatedAt = catDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                            )
                            db.categoryDao().insert(category)
                        }
                    }

                    // Загружаем расходы только если есть автомобиль
                    if (savedCarId != -1) {
                        val expensesSnapshot = firestore.collection("carGroups/$groupId/expenses")
                            .whereEqualTo("isDeleted", false)
                            .get()
                            .await()

                        for (expenseDoc in expensesSnapshot.documents) {
                            val existing = db.expenseDao().getByCloudId(expenseDoc.id)
                            if (existing == null) {
                                val expense = Expense(
                                    carId = savedCarId,
                                    amount = expenseDoc.getDouble("amount") ?: 0.0,
                                    category = expenseDoc.getString("category") ?: "",
                                    categoryId = expenseDoc.getLong("categoryId")?.toInt(),
                                    date = expenseDoc.getLong("date")?.let { Date(it) } ?: Date(),
                                    mileage = expenseDoc.getLong("mileage")?.toInt() ?: 0,
                                    comment = expenseDoc.getString("comment") ?: "",
                                    shopName = expenseDoc.getString("shopName") ?: "",
                                    cloudId = expenseDoc.id,
                                    updatedAt = expenseDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                                db.expenseDao().insert(expense)
                            }
                        }

                        // Загружаем напоминания
                        val remindersSnapshot = firestore.collection("carGroups/$groupId/reminders")
                            .whereEqualTo("isDeleted", false)
                            .get()
                            .await()

                        for (reminderDoc in remindersSnapshot.documents) {
                            val existing = db.reminderDao().getByCloudId(reminderDoc.id)
                            if (existing == null) {
                                val reminder = Reminder(
                                    carId = savedCarId,
                                    title = reminderDoc.getString("title") ?: "",
                                    type = reminderDoc.getString("type") ?: "",
                                    targetDate = reminderDoc.getLong("targetDate")?.let { Date(it) },
                                    targetMileage = reminderDoc.getLong("targetMileage")?.toInt(),
                                    isCompleted = reminderDoc.getBoolean("isCompleted") ?: false,
                                    cloudId = reminderDoc.id,
                                    updatedAt = reminderDoc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                                db.reminderDao().insert(reminder)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Данные успешно восстановлены!", Toast.LENGTH_SHORT).show()
                    initializeApp()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error restoring data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка восстановления: ${e.message}", Toast.LENGTH_LONG).show()
                    showNoCarOptionsDialog()
                }
            }
        }
    }

    private fun initializeApp() {
        initializeControllers()
        isInitialized = true

        setupUI()
        loadCategories()
        setupClickListeners()
        initializeAndSync()
    }

    private fun initializeControllers() {
        expenseController = ExpenseController(this)
        categoryController = CategoryController(this)
        notificationManager = NotificationManager(this)
        syncController = SyncController(this)
        firebaseController = FirebaseController()
    }

    override fun onResume() {
        super.onResume()
        if (isInitialized) {
            loadCategories()
            checkReminders()
            loadCarData() // Перезагружаем данные при возврате
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncController?.stopRealtimeSync()
    }

    private fun initializeAndSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                groupId = SharedPrefsHelper.getGroupId(this@MainActivity)
                val carId = SharedPrefsHelper.getCurrentCarId(this@MainActivity)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                Log.d("MainActivity", "initializeAndSync: groupId=$groupId, carId=$carId, userId=$currentUserId")

                if (carId == -1 || currentUserId == null) {
                    withContext(Dispatchers.Main) { handleNoCar() }
                    return@launch
                }

                val db = AppDatabase.getDatabase(this@MainActivity)
                val car = db.carDao().getById(carId)

                if (car == null) {
                    withContext(Dispatchers.Main) { handleNoCar() }
                    return@launch
                }

                var finalGroupId = groupId

                // Если groupId потерян, ищем существующую группу пользователя в Firebase
                if (finalGroupId == null && firebaseController != null) {
                    Log.d("MainActivity", "GroupId is null, searching for existing groups...")

                    // Ищем существующие группы пользователя
                    val existingGroups = FirebaseFirestore.getInstance()
                        .collection("carGroups")
                        .whereEqualTo("ownerUid", currentUserId)
                        .get()
                        .await()

                    if (!existingGroups.isEmpty) {
                        // Нашли существующую группу - используем её
                        val existingGroup = existingGroups.documents.first()
                        finalGroupId = existingGroup.id
                        Log.d("MainActivity", "Found existing group: $finalGroupId")
                        SharedPrefsHelper.setGroupId(this@MainActivity, finalGroupId)
                    } else {
                        // Группы нет - создаем новую
                        Log.d("MainActivity", "No existing groups, creating new one...")
                        val result = firebaseController!!.createCarGroup(carId)

                        if (result.isSuccess) {
                            val (newGroupId, _) = result.getOrThrow()
                            Log.d("MainActivity", "New group created: $newGroupId")
                            finalGroupId = newGroupId
                            SharedPrefsHelper.setGroupId(this@MainActivity, newGroupId)
                        } else {
                            Log.e("MainActivity", "Failed to create group: ${result.exceptionOrNull()?.message}")
                            withContext(Dispatchers.Main) { loadCarData() }
                            return@launch
                        }
                    }
                }

                // Устанавливаем groupId для синхронизации
                syncController?.setGroupId(finalGroupId!!)
                groupId = finalGroupId

                // Синхронизируем автомобиль в облако если нужно
                if (car.cloudId.isEmpty()) {
                    Log.d("MainActivity", "Syncing car to cloud...")
                    syncController?.syncCar(car)
                    delay(1000)
                }

                // Синхронизируем все остальные данные
                Log.d("MainActivity", "Syncing all local data to cloud...")
                syncController?.syncAllLocalToCloud()

                // Даем время на завершение синхронизации
                delay(2000)

                withContext(Dispatchers.Main) {
                    Log.d("MainActivity", "Starting realtime sync...")
                    syncController?.startRealtimeSync { loadCarData() }
                    syncController?.startAutoSync()
                    loadCarData()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error in initializeAndSync: ${e.message}", e)
                withContext(Dispatchers.Main) { loadCarData() }
            }
        }
    }

    private fun setupUI() {
        binding.recentExpensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = expenseAdapter
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_home

        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java)); true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, AddExpenseActivity::class.java)); true
                }
                R.id.nav_reminders -> {
                    startActivity(Intent(this, RemindersActivity::class.java)); true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, CarSettingsActivity::class.java)); true
                }
                else -> false
            }
        }

        binding.logoutButton.setOnClickListener { performLogout() }
        binding.groupManagementButton.setOnClickListener {
            startActivity(Intent(this, GroupManagementActivity::class.java))
        }

        binding.addFirstCarButton.setOnClickListener {
            startActivity(Intent(this, CarSettingsActivity::class.java))
        }

        binding.showAllExpensesButton.setOnClickListener {
            // Переход на список всех расходов
            val intent = Intent(this, ExpensesListActivity::class.java)
            intent.putExtra("CAR_ID", currentCar?.id ?: -1)
            startActivity(intent)
        }

        // Добавляем обработчик для FAB
        binding.addExpenseFab.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }
    }

    private fun performLogout() {
        syncController?.stopRealtimeSync()
        firebaseController?.logout()

        // Сохраняем groupId перед очисткой
        val savedGroupId = SharedPrefsHelper.getGroupId(this)

        // Очищаем все кроме groupId
        SharedPrefsHelper.clearAll(this)

        // Восстанавливаем groupId
        if (savedGroupId != null) {
            SharedPrefsHelper.setGroupId(this, savedGroupId)
        }

        startActivity(
            Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun loadCategories() {
        if (!isInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                categoriesCache = categoryController.getAllCategories()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading categories: ${e.message}")
            }
        }
    }

    private fun loadCarData() {
        if (!isInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val carId = SharedPrefsHelper.getCurrentCarId(this@MainActivity)

                if (carId != -1) {
                    val car = db.carDao().getById(carId)

                    withContext(Dispatchers.Main) {
                        if (car != null) {
                            currentCar = car
                            updateCarInfo()
                            loadExpenses()
                            loadStatistics()

                            // Показываем все карточки
                            binding.apply {
                                carInfoCard.visibility = View.VISIBLE
                                monthStatsCard.visibility = View.VISIBLE
                                recentExpensesCard.visibility = View.VISIBLE
                                emptyStateLayout.visibility = View.GONE
                                addExpenseFab.visibility = View.VISIBLE
                            }
                        } else {
                            handleNoCar()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        handleNoCar()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading data: ${e.message}")
                withContext(Dispatchers.Main) {
                    handleNoCar()
                }
            }
        }
    }

    private fun handleNoCar() {
        binding.apply {
            carInfoCard.visibility = View.GONE
            monthStatsCard.visibility = View.GONE
            recentExpensesCard.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            addExpenseFab.visibility = View.GONE

            emptyStateText.text = "Добавьте ваш первый автомобиль"
            emptyStateSubtext.text = "Начните отслеживать расходы на авто"
            addFirstCarButton.visibility = View.VISIBLE
        }
    }

    private fun updateCarInfo() {
        currentCar?.let { car ->
            binding.apply {
                carNameTextView.text = car.getFullName()
                currentMileageTextView.text = "Текущий пробег: ${car.currentMileage} км"
                monthTitleTextView.text = monthFormat.format(Date())
            }
        }
    }

    private fun loadExpenses() {
        if (!isInitialized) return
        currentCar?.let { car ->
            expenseController.getRecentExpenses(car.id) { expenses ->
                expenseAdapter.submitList(expenses)
            }
        }
    }

    private fun loadStatistics() {
        if (!isInitialized) return
        currentCar?.let { car ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val month = expenseController.getMonthlyExpensesSync(car.id)
                    val prev = expenseController.getPreviousMonthExpensesSync(car.id)

                    withContext(Dispatchers.Main) {
                        binding.totalExpensesTextView.text = currencyFormat.format(month ?: 0.0)

                        if (prev != null && prev > 0 && month != null && month > 0) {
                            val diff = prev - month
                            val percentChange = ((diff / prev) * 100).toInt()

                            if (diff > 0) {
                                binding.monthComparisonTextView.text = "▼${percentChange}% к прошлому месяцу"
                                binding.economyTextView.text = "🏆 ${currencyFormat.format(diff)}"
                            } else if (diff < 0) {
                                binding.monthComparisonTextView.text = "▲${-percentChange}% к прошлому месяцу"
                                binding.economyTextView.text = "📈 ${currencyFormat.format(-diff)}"
                            } else {
                                binding.monthComparisonTextView.text = "Без изменений"
                                binding.economyTextView.text = ""
                            }
                        } else {
                            binding.monthComparisonTextView.text = "Нет данных для сравнения"
                            binding.economyTextView.text = ""
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading statistics: ${e.message}")
                    withContext(Dispatchers.Main) {
                        binding.monthComparisonTextView.text = "Ошибка загрузки статистики"
                        binding.economyTextView.text = ""
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.addExpenseFab.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }
    }

    private fun checkReminders() {
        if (!isInitialized) return
        currentCar?.let { car ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    notificationManager.checkMileageReminders(car.id)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error checking reminders: ${e.message}")
                }
            }
        }
    }

    inner class ExpenseAdapter :
        ListAdapter<Expense, ExpenseViewHolder>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            return ExpenseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(e: Expense) {
            itemView.findViewById<TextView>(R.id.categoryTextView).text = e.category
            itemView.findViewById<TextView>(R.id.amountTextView).text = currencyFormat.format(e.amount)
            itemView.findViewById<TextView>(R.id.dateTextView).text = dateFormat.format(e.date)

            // Отображаем магазин и комментарий как в ExpenseDetailActivity
            val commentTextView = itemView.findViewById<TextView>(R.id.commentTextView)

            if (e.shopName.isNotEmpty() && e.comment.isNotEmpty()) {
                commentTextView.text = "${e.shopName}, ${e.comment}"
            } else if (e.shopName.isNotEmpty()) {
                commentTextView.text = e.shopName
            } else if (e.comment.isNotEmpty()) {
                commentTextView.text = e.comment
            } else {
                commentTextView.text = ""
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(old: Expense, new: Expense) = old.id == new.id
        override fun areContentsTheSame(old: Expense, new: Expense) = old == new
    }
}