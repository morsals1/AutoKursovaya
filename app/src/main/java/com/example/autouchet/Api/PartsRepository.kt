package com.example.autouchet.Api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PartsRepository(private val apiKey: String) {

    private val api = UmapiClient.service

    suspend fun searchParts(
        article: String? = null,
        brand: String? = null,
        name: String? = null
    ): Result<List<UmapiPart>> {
        return withContext(Dispatchers.IO) {
            try {
                val searchArticle = article ?: name ?: ""

                if (searchArticle.isEmpty()) {
                    return@withContext Result.failure(Exception("Введите артикул для поиска"))
                }

                val response = api.searchParts(
                    apiKey = apiKey,
                    languageCode = "ru",
                    regionCode = "RU",
                    article = searchArticle,
                    limit = 20
                )

                if (response.isSuccessful) {
                    val parts = response.body() ?: emptyList()
                    Result.success(parts)
                } else {
                    Result.failure(Exception("Ошибка ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("PartsRepository", "Error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getPriceComparison(article: String, brand: String): Result<PartPriceComparison> {
        return Result.success(
            PartPriceComparison(
                article = article,
                brand = brand,
                name = ""
            )
        )
    }

    suspend fun getAnalogs(article: String, brand: String): Result<List<UmapiPart>> {
        return Result.success(emptyList())
    }

    fun matchExpenseToPart(expense: com.example.autouchet.Models.Expense): Pair<String?, String?> {
        val comment = expense.comment.lowercase()
        val shopName = expense.shopName.lowercase()

        val articlePatterns = listOf(
            Regex("арт[икул]*[:\\s]*([A-Za-z0-9\\-]+)", RegexOption.IGNORE_CASE),
            Regex("article[:\\s]*([A-Za-z0-9\\-]+)", RegexOption.IGNORE_CASE),
            Regex("номер[:\\s]*([A-Za-z0-9\\-]+)", RegexOption.IGNORE_CASE),
            Regex("([A-Z]{2,}[0-9]{4,}[A-Za-z0-9\\-]*)")
        )

        var article: String? = null
        for (pattern in articlePatterns) {
            val match = pattern.find(comment) ?: pattern.find(shopName)
            if (match != null) {
                article = match.groupValues[1]
                break
            }
        }

        return Pair(article, null)
    }
}