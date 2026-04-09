package com.example.autouchet.Views

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.autouchet.Controllers.CategoryController
import com.example.autouchet.Models.ExpenseCategory
import com.example.autouchet.R
import com.example.autouchet.databinding.ActivityCategoriesBinding
import com.example.autouchet.databinding.DialogAddCategoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CategoriesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var categoryController: CategoryController
    private lateinit var adapter: CategoryAdapter
    private var categories = mutableListOf<ExpenseCategory>()
    private var filteredCategories = mutableListOf<ExpenseCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryController = CategoryController(this)

        setupUI()
        loadCategories()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Управление категориями"

        adapter = CategoryAdapter { category ->
            showEditCategoryDialog(category)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                if (fromPosition < toPosition) {
                    for (i in fromPosition until toPosition) {
                        Collections.swap(filteredCategories, i, i + 1)
                    }
                } else {
                    for (i in fromPosition downTo toPosition + 1) {
                        Collections.swap(filteredCategories, i, i - 1)
                    }
                }

                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveCategoryOrder()
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.fabAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCategories(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val loadedCategories = categoryController.getAllCategories()
            withContext(Dispatchers.Main) {
                categories.clear()
                categories.addAll(loadedCategories)
                filterCategories(binding.searchEditText.text.toString())
            }
        }
    }

    private fun filterCategories(query: String) {
        filteredCategories.clear()
        if (query.isEmpty()) {
            filteredCategories.addAll(categories)
        } else {
            filteredCategories.addAll(categories.filter {
                it.name.contains(query, ignoreCase = true)
            })
        }
        adapter.submitList(filteredCategories)
        binding.emptyView.visibility = if (filteredCategories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveCategoryOrder() {
        CoroutineScope(Dispatchers.IO).launch {
            categoryController.updateCategoryOrder(filteredCategories)
        }
    }

    private fun showAddCategoryDialog() {
        val dialogBinding = DialogAddCategoryBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Новая категория")
            .setView(dialogBinding.root)
            .setPositiveButton("Добавить", null)
            .setNegativeButton("Отмена", null)
            .create()

        setupIconSelector(dialogBinding)
        setupColorSelector(dialogBinding)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.nameEditText.text.toString().trim()
                if (name.isEmpty()) {
                    dialogBinding.nameLayout.error = "Введите название"
                    return@setOnClickListener
                }

                val selectedIcon = dialogBinding.selectedIconTextView.text.toString()
                val selectedColor = dialogBinding.selectedColorView.tag as? Int ?: 0xFF4CAF50.toInt()

                CoroutineScope(Dispatchers.IO).launch {
                    val result = categoryController.addCategory(name, selectedIcon, selectedColor)
                    withContext(Dispatchers.Main) {
                        if (result > 0) {
                            loadCategories()
                            dialog.dismiss()
                            Snackbar.make(binding.root, "Категория добавлена", Snackbar.LENGTH_SHORT).show()
                        } else {
                            dialogBinding.nameLayout.error = "Такая категория уже существует"
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditCategoryDialog(category: ExpenseCategory) {
        if (category.isDefault) {
            Snackbar.make(binding.root, "Нельзя редактировать стандартную категорию", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddCategoryBinding.inflate(layoutInflater)
        dialogBinding.nameEditText.setText(category.name)
        dialogBinding.selectedIconTextView.text = category.icon
        dialogBinding.selectedColorView.setBackgroundColor(category.color)
        dialogBinding.selectedColorView.tag = category.color

        val dialog = AlertDialog.Builder(this)
            .setTitle("Редактировать категорию")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить") { _, _ ->
                showDeleteConfirmation(category)
            }
            .create()

        setupIconSelector(dialogBinding)
        setupColorSelector(dialogBinding)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.nameEditText.text.toString().trim()
                if (name.isEmpty()) {
                    dialogBinding.nameLayout.error = "Введите название"
                    return@setOnClickListener
                }

                val selectedIcon = dialogBinding.selectedIconTextView.text.toString()
                val selectedColor = dialogBinding.selectedColorView.tag as? Int ?: category.color

                val updatedCategory = category.copy(
                    name = name,
                    icon = selectedIcon,
                    color = selectedColor
                )

                CoroutineScope(Dispatchers.IO).launch {
                    categoryController.updateCategory(updatedCategory)
                    withContext(Dispatchers.Main) {
                        loadCategories()
                        dialog.dismiss()
                        Snackbar.make(binding.root, "Категория обновлена", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(category: ExpenseCategory) {
        AlertDialog.Builder(this)
            .setTitle("Удалить категорию")
            .setMessage("Вы уверены, что хотите удалить категорию \"${category.name}\"?")
            .setPositiveButton("Удалить") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val success = categoryController.deleteCategory(category.id)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            loadCategories()
                            Snackbar.make(binding.root, "Категория удалена", Snackbar.LENGTH_SHORT).show()
                        } else {
                            Snackbar.make(binding.root, "Не удалось удалить категорию", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupIconSelector(dialogBinding: DialogAddCategoryBinding) {
        val icons = categoryController.getDefaultIcons()
        val iconAdapter = IconAdapter(icons) { icon ->
            dialogBinding.selectedIconTextView.text = icon
        }

        dialogBinding.iconsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dialogBinding.iconsRecyclerView.adapter = iconAdapter
    }

    private fun setupColorSelector(dialogBinding: DialogAddCategoryBinding) {
        val colors = categoryController.getDefaultColors()
        val colorAdapter = ColorAdapter(colors) { color ->
            dialogBinding.selectedColorView.setBackgroundColor(color)
            dialogBinding.selectedColorView.tag = color
        }

        dialogBinding.colorsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dialogBinding.colorsRecyclerView.adapter = colorAdapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class CategoryAdapter(
        private val onItemClick: (ExpenseCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
        private var items = listOf<ExpenseCategory>()

        fun submitList(newItems: List<ExpenseCategory>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconTextView: TextView = itemView.findViewById(R.id.iconTextView)
            private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
            private val defaultBadge: TextView = itemView.findViewById(R.id.defaultBadge)
            private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

            fun bind(category: ExpenseCategory) {
                iconTextView.text = category.icon
                nameTextView.text = category.name

                iconTextView.background.setTint(category.color)

                if (category.isDefault) {
                    defaultBadge.visibility = View.VISIBLE
                    dragHandle.visibility = View.GONE
                } else {
                    defaultBadge.visibility = View.GONE
                    dragHandle.visibility = View.VISIBLE
                }

                itemView.setOnClickListener {
                    onItemClick(category)
                }
            }
        }
    }

    class IconAdapter(
        private val icons: List<String>,
        private val onIconClick: (String) -> Unit
    ) : RecyclerView.Adapter<IconAdapter.ViewHolder>() {
        private var selectedPosition = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_icon_selector, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(icons[position], position == selectedPosition)
        }

        override fun getItemCount() = icons.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconTextView: TextView = itemView.findViewById(R.id.iconTextView)

            fun bind(icon: String, isSelected: Boolean) {
                iconTextView.text = icon
                itemView.isSelected = isSelected
                itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = adapterPosition
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    onIconClick(icon)
                }
            }
        }
    }

    class ColorAdapter(
        private val colors: List<Pair<String, Int>>,
        private val onColorClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ViewHolder>() {
        private var selectedPosition = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color_selector, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(colors[position].second, position == selectedPosition)
        }

        override fun getItemCount() = colors.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorView: View = itemView.findViewById(R.id.colorView)

            fun bind(color: Int, isSelected: Boolean) {
                colorView.setBackgroundColor(color)
                itemView.isSelected = isSelected
                itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = adapterPosition
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    onColorClick(color)
                }
            }
        }
    }
}