package com.example.autouchet.Views

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Models.Expense
import com.example.autouchet.R
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityExpensesListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpensesListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpensesListBinding
    private lateinit var expenseController: ExpenseController
    private val expenseAdapter = ExpenseAdapter()
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }
    private val monthFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    private var currentCarId: Int = -1
    private var currentDate = Calendar.getInstance()
    private var allExpenses = listOf<Expense>()
    private var filteredExpenses = listOf<Expense>()
    private val categories = listOf(
        "Топливо", "Обслуживание", "Ремонт", "Шины",
        "Мойка", "Страховка", "Налоги", "Парковка",
        "Штрафы", "Другое"
    )
    private var searchQuery = ""
    private var selectedCategory = ""
    private var amountFrom: Double? = null
    private var amountTo: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCarId = SharedPrefsHelper.getCurrentCarId(this)
        expenseController = ExpenseController(this)

        setupUI()
        setupClickListeners()
        setupSearchAndFilters()

        if (currentCarId != -1) {
            loadExpensesForMonth()
        } else {
            showNoCarMessage()
        }
    }

    private fun setupUI() {
        binding.expensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ExpensesListActivity)
            adapter = expenseAdapter
        }
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            listOf("Все категории") + categories
        )
        binding.categoryFilterAutoCompleteTextView.setAdapter(categoryAdapter)

        updateMonthTitle()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.prevMonthButton.setOnClickListener {
            if (currentCarId == -1) {
                showNoCarMessage()
                return@setOnClickListener
            }

            currentDate.add(Calendar.MONTH, -1)
            updateMonthTitle()
            loadExpensesForMonth()
        }

        binding.nextMonthButton.setOnClickListener {
            if (currentCarId == -1) {
                showNoCarMessage()
                return@setOnClickListener
            }

            currentDate.add(Calendar.MONTH, 1)
            updateMonthTitle()
            loadExpensesForMonth()

            val now = Calendar.getInstance()
            if (currentDate.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
                (currentDate.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        currentDate.get(Calendar.MONTH) > now.get(Calendar.MONTH))) {
                currentDate = Calendar.getInstance()
                updateMonthTitle()
            }
        }

        binding.addFirstExpenseButton.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }
        binding.filterButton.setOnClickListener {
            val isVisible = binding.filtersCard.visibility == View.VISIBLE
            binding.filtersCard.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
        binding.applyFiltersButton.setOnClickListener {
            applyFilters()
            binding.filtersCard.visibility = View.GONE
        }
        binding.clearFiltersButton.setOnClickListener {
            clearFilters()
        }
    }

    private fun setupSearchAndFilters() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                applyFilters()
            }
        })
        binding.categoryFilterAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = if (position == 0) "" else categories[position - 1]
        }
    }

    private fun updateMonthTitle() {
        binding.currentMonthTextView.text = monthFormat.format(currentDate.time)
    }

    private fun loadExpensesForMonth() {
        if (currentCarId == -1) {
            showNoCarMessage()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val year = currentDate.get(Calendar.YEAR)
            val month = currentDate.get(Calendar.MONTH) + 1

            allExpenses = expenseController.getExpensesForMonthSync(currentCarId, year, month)
            applyFilters()

            withContext(Dispatchers.Main) {
                updateUI()
            }
        }
    }

    private fun applyFilters() {
        val amountFromText = binding.amountFromEditText.text.toString()
        val amountToText = binding.amountToEditText.text.toString()

        amountFrom = amountFromText.toDoubleOrNull()
        amountTo = amountToText.toDoubleOrNull()

        filteredExpenses = allExpenses.filter { expense ->
            val matchesSearch = searchQuery.isEmpty() ||
                    expense.category.contains(searchQuery, ignoreCase = true) ||
                    expense.comment.contains(searchQuery, ignoreCase = true) ||
                    expense.shopName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory.isEmpty() || expense.category == selectedCategory
            val matchesAmountFrom = amountFrom == null || expense.amount >= amountFrom!!
            val matchesAmountTo = amountTo == null || expense.amount <= amountTo!!

            matchesSearch && matchesCategory && matchesAmountFrom && matchesAmountTo
        }

        runOnUiThread {
            updateUI()
        }
    }

    private fun clearFilters() {
        binding.searchEditText.text?.clear()
        binding.categoryFilterAutoCompleteTextView.text?.clear()
        binding.amountFromEditText.text?.clear()
        binding.amountToEditText.text?.clear()

        searchQuery = ""
        selectedCategory = ""
        amountFrom = null
        amountTo = null

        applyFilters()
        binding.filtersCard.visibility = View.GONE
    }

    private fun updateUI() {
        val total = filteredExpenses.sumOf { it.amount }

        expenseAdapter.submitList(filteredExpenses)
        binding.totalAmountTextView.text = currencyFormat.format(total)

        if (filteredExpenses.isEmpty()) {
            binding.expensesRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE

            if (allExpenses.isEmpty()) {
                binding.emptyStateTitle.text = "Нет расходов за этот период"
                binding.emptyStateDescription.text = "Добавьте первый расход"
                binding.addFirstExpenseButton.visibility = View.VISIBLE
            } else {
                binding.emptyStateTitle.text = "Ничего не найдено"
                binding.emptyStateDescription.text = "Попробуйте изменить параметры поиска"
                binding.addFirstExpenseButton.visibility = View.GONE
            }
        } else {
            binding.expensesRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showNoCarMessage() {
        binding.expensesRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.addFirstExpenseButton.visibility = View.GONE
        binding.prevMonthButton.isEnabled = false
        binding.nextMonthButton.isEnabled = false
        binding.searchCard.visibility = View.GONE
        binding.periodCard.visibility = View.GONE

        binding.emptyStateTitle.text = "Нет автомобиля"
        binding.emptyStateDescription.text = "Сначала добавьте автомобиль в настройках"
    }

    inner class ExpenseAdapter : androidx.recyclerview.widget.ListAdapter<Expense, ExpenseViewHolder>(
        ExpenseDiffCallback()
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            return ExpenseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
            val expense = getItem(position)
            holder.bind(expense)
        }
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(expense: Expense) {
            itemView.findViewById<TextView>(R.id.dateTextView).text =
                dateFormat.format(expense.date)
            itemView.findViewById<TextView>(R.id.categoryTextView).text =
                "${expense.getCategoryIcon()} ${expense.category}"
            itemView.findViewById<TextView>(R.id.amountTextView).text =
                currencyFormat.format(expense.amount)

            val commentTextView = itemView.findViewById<TextView>(R.id.commentTextView)
            if (expense.comment.isNotEmpty()) {
                commentTextView.text = expense.comment
                commentTextView.visibility = View.VISIBLE
            } else {
                commentTextView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, ExpenseDetailActivity::class.java).apply {
                    putExtra("expense_id", expense.id)
                }
                itemView.context.startActivity(intent)
            }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem == newItem
        }
    }
}