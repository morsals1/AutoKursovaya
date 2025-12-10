package com.example.autouchet.Controllers

import android.content.Context
import android.util.Log
import java.util.*

class ReceiptScanner(private val context: Context) {

    fun simulateScanReceipt(qrData: String): ReceiptData {
        // В реальном приложении здесь будет подключение к API ФНС
        // Для демо симулируем сканирование

        return when {
            qrData.contains("lukoil", ignoreCase = true) -> {
                ReceiptData(
                    totalAmount = 3200.0,
                    date = Date(),
                    shopName = "Лукойл",
                    items = listOf(
                        ReceiptItem("АИ-95", 3200.0, 40.0)
                    )
                )
            }
            qrData.contains("magnit", ignoreCase = true) -> {
                ReceiptData(
                    totalAmount = 5400.0,
                    date = Date(),
                    shopName = "Магнит Авто",
                    items = listOf(
                        ReceiptItem("Масло моторное 5W-40", 3000.0, 1.0),
                        ReceiptItem("Масляный фильтр", 1500.0, 1.0),
                        ReceiptItem("Воздушный фильтр", 900.0, 1.0)
                    )
                )
            }
            qrData.contains("shina", ignoreCase = true) -> {
                ReceiptData(
                    totalAmount = 12000.0,
                    date = Date(),
                    shopName = "Шина-Маркет",
                    items = listOf(
                        ReceiptItem("Шина зимняя 205/55 R16", 3000.0, 4.0)
                    )
                )
            }
            else -> {
                ReceiptData(
                    totalAmount = 1000.0,
                    date = Date(),
                    shopName = "Неизвестный магазин",
                    items = listOf(
                        ReceiptItem("Товар", 1000.0, 1.0)
                    )
                )
            }
        }
    }

    fun detectCategoryFromReceipt(items: List<ReceiptItem>): String {
        val text = items.joinToString { it.name.lowercase() }

        return when {
            Regex("бензин|дизель|аи-?\\d+|топливо").containsMatchIn(text) -> "Топливо"
            Regex("масло|фильтр|свеч|тормозн|охлажден").containsMatchIn(text) -> "Обслуживание"
            Regex("шины?|колесо|резин|покрышк").containsMatchIn(text) -> "Шины"
            Regex("мойк|чистк|полировк").containsMatchIn(text) -> "Мойка"
            else -> "Прочее"
        }
    }

    data class ReceiptData(
        val totalAmount: Double,
        val date: Date,
        val shopName: String,
        val items: List<ReceiptItem>
    )

    data class ReceiptItem(
        val name: String,
        val price: Double,
        val quantity: Double
    )
}