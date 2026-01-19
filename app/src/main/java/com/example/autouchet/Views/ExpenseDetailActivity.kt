package com.example.autouchet.Views

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.autouchet.Controllers.ExpenseController
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.Models.Expense
import com.example.autouchet.R
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
    private lateinit var expenseController: ExpenseController
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var expenseId: Int = -1
    private var expense: Expense? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseId = intent.getIntExtra("expense_id", -1)
        expenseController = ExpenseController(this)

        setupClickListeners()
        loadExpenseDetails()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.editButton.setOnClickListener {
            expense?.let {
                val intent = Intent(this, AddExpenseActivity::class.java).apply {
                    putExtra("expense_id", it.id)
                    putExtra("edit_mode", true)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun loadExpenseDetails() {
        if (expenseId == -1) {
            Toast.makeText(this, "Ошибка: расход не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(this@ExpenseDetailActivity)
            expense = database.expenseDao().getById(expenseId)

            withContext(Dispatchers.Main) {
                expense?.let { updateUI(it) } ?: run {
                    Toast.makeText(this@ExpenseDetailActivity, "Расход не найден", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun updateUI(expense: Expense) {
        binding.amountTextView.text = currencyFormat.format(expense.amount)
        binding.categoryTextView.text = expense.category
        binding.dateTextView.text = dateFormat.format(expense.date)
        binding.mileageTextView.text = "${expense.mileage} км"

        if (expense.comment.isNotEmpty()) {
            binding.commentTextView.text = expense.comment
        } else {
            binding.commentTextView.text = "Нет комментария"
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Удаление расхода")
            .setMessage("Вы уверены, что хотите удалить этот расход?")
            .setPositiveButton("Удалить") { dialog, which ->
                deleteExpense()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteExpense() {
        CoroutineScope(Dispatchers.IO).launch {
            expense?.let {
                expenseController.deleteExpense(it.id)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ExpenseDetailActivity,
                        "Расход удален",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }
}