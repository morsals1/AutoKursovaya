// Api/UmapiModels.kt
package com.example.autouchet.Api

import com.google.gson.annotations.SerializedName

// Модель ответа поиска запчастей
data class UmapiSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<UmapiPart>? = null,
    @SerializedName("results") val results: List<UmapiPart>? = null,
    @SerializedName("items") val items: List<UmapiPart>? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("statusCode") val statusCode: Int? = null
) {
    fun getParts(): List<UmapiPart> {
        return data ?: results ?: items ?: emptyList()
    }

    fun getErrorMessage(): String {
        return message ?: error ?: "Неизвестная ошибка"
    }
}

// Модель запчасти
data class UmapiPart(
    @SerializedName("SEARCH_NUMBER") val searchNumber: String = "",
    @SerializedName("DISPLAY_NR") val displayNr: String = "",
    @SerializedName("TYPE") val type: String = "",
    @SerializedName("BRA_ID") val braId: Int = 0,
    @SerializedName("BRAND") val brand: String = "",
    @SerializedName("DISPLAY") val display: Int? = null,
    @SerializedName("DES") val description: String? = null,
    @SerializedName("IS_ADDITIV") val isAdditiv: Int? = null,
    @SerializedName("TITLE") val title: String = ""
) {
    val article: String get() = searchNumber
    val name: String get() = title.ifEmpty { description ?: "" }
}

// Модель сравнения цен
data class PartPriceComparison(
    val article: String,
    val brand: String,
    val name: String,
    val offers: List<PartOffer> = emptyList(),
    val minPrice: Double = 0.0,
    val maxPrice: Double = 0.0,
    val avgPrice: Double = 0.0,
    val recommendedOffer: PartOffer? = null,
    val potentialSavings: Double = 0.0
)

// Модель предложения
data class PartOffer(
    val supplier: String = "",
    val price: Double = 0.0,
    val deliveryDays: Int = 0,
    val supplierRating: Double = 0.0,
    val url: String = ""
)

// Модель для истории поиска
data class SearchHistory(
    val query: String,
    val article: String,
    val brand: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultsCount: Int = 0
)

// Модель для связи расхода с запчастью
data class PartExpenseLink(
    val expenseId: Int,
    val article: String,
    val brand: String,
    val partName: String,
    val price: Double,
    val supplier: String,
    val savings: Double = 0.0
)

// Ответ API с предложениями
data class UmapiOfferResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<UmapiOffer>? = null,
    @SerializedName("results") val results: List<UmapiOffer>? = null,
    @SerializedName("message") val message: String? = null
) {
    fun getOffers(): List<UmapiOffer> {
        return data ?: results ?: emptyList()
    }
}

data class UmapiOffer(
    @SerializedName("article") val article: String = "",
    @SerializedName("brand") val brand: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("supplier") val supplier: String = "",
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("delivery_days") val deliveryDays: Int = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("rating") val rating: Double = 0.0
)