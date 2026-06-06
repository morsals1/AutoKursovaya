package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Api.PartPriceComparison
import com.example.autouchet.Api.UmapiPart
import com.example.autouchet.Models.AppDatabase
import com.example.autouchet.R
import com.example.autouchet.ViewModels.PartsSearchViewModel
import com.example.autouchet.databinding.ActivityPartsSearchBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*

class PartsSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPartsSearchBinding
    private lateinit var viewModel: PartsSearchViewModel
    private val adapter = PartsAdapter()
    private val currencyFormat = NumberFormat.getCurrencyInstance().apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("RUB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPartsSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PartsSearchViewModel::class.java]

        setupUI()
        observeData()

        val expenseId = intent.getIntExtra("expense_id", -1)
        if (expenseId != -1) {
            loadExpenseAndSearch(expenseId)
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }

        binding.backButton.setOnClickListener { finish() }

        // Добавьте эту строку:
        binding.partsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.partsRecyclerView.adapter = adapter

        binding.searchButton.setOnClickListener {
            val article = binding.articleEditText.text.toString().trim()
            val brand = binding.brandEditText.text.toString().trim()
            val name = binding.nameEditText.text.toString().trim()

            if (article.isNotEmpty() || name.isNotEmpty()) {
                viewModel.searchParts(
                    article = article.ifEmpty { null },
                    brand = brand.ifEmpty { null },
                    name = name.ifEmpty { null }
                )
            } else {
                Toast.makeText(this, "Введите артикул или название", Toast.LENGTH_SHORT).show()
            }
        }

        adapter.setOnItemClickListener { part ->
            viewModel.getPriceComparison(part.article, part.brand)
            viewModel.priceComparison.observe(this) { comparison ->
                comparison?.let { showPriceDialog(it) }
            }
        }
    }

    private fun observeData() {
        viewModel.searchResults.observe(this) { parts ->
            adapter.submitList(parts)
            binding.resultsCountTextView.text = "Найдено: ${parts.size}"
            binding.emptyView.visibility = if (parts.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun loadExpenseAndSearch(expenseId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@PartsSearchActivity)
            val expense = db.expenseDao().getById(expenseId)
            withContext(Dispatchers.Main) {
                expense?.let {
                    viewModel.matchExpense(it)
                    binding.articleEditText.setText(it.comment)
                }
            }
        }
    }

    private fun showPriceDialog(comparison: PartPriceComparison) {
        val message = "Запчасть: ${comparison.name}\n" +
                "Артикул: ${comparison.article}\n" +
                "Бренд: ${comparison.brand}\n\n" +
                "Цены будут доступны в следующем обновлении"

        AlertDialog.Builder(this)
            .setTitle("Информация о запчасти")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Аналоги") { _, _ ->
                viewModel.getAnalogs(comparison.article, comparison.brand)
            }
            .show()
    }

    inner class PartsAdapter : RecyclerView.Adapter<PartsViewHolder>() {
        private var items = listOf<UmapiPart>()
        private var listener: ((UmapiPart) -> Unit)? = null

        fun submitList(newItems: List<UmapiPart>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setOnItemClickListener(l: (UmapiPart) -> Unit) {
            listener = l
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartsViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_part, parent, false)
            return PartsViewHolder(view)
        }

        override fun onBindViewHolder(holder: PartsViewHolder, position: Int) {
            holder.bind(items[position], listener)
        }

        override fun getItemCount() = items.size
    }

    inner class PartsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.partNameTextView)
        private val articleTextView: TextView = itemView.findViewById(R.id.partArticleTextView)
        private val brandTextView: TextView = itemView.findViewById(R.id.partSupplierTextView)

        fun bind(part: UmapiPart, listener: ((UmapiPart) -> Unit)?) {
            nameTextView.text = part.name
            articleTextView.text = part.article
            brandTextView.text = part.brand
            itemView.setOnClickListener { listener?.invoke(part) }
        }
    }
}