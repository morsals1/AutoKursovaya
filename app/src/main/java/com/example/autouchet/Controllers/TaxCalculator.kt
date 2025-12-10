package com.example.autouchet.Controllers

import java.util.*

class TaxCalculator {
    companion object {
        val taxRates = mapOf(  // Сделал public
            "Москва" to 12.0,
            "Московская область" to 10.0,
            "Свердловская область" to 35.0,
            "Санкт-Петербург" to 24.0,
            "Ленинградская область" to 25.0,
            "Республика Татарстан" to 25.0,
            "Краснодарский край" to 25.0,
            "Новосибирская область" to 25.0,
            "Челябинская область" to 25.0,
            "Ростовская область" to 20.0,
            "Башкортостан" to 25.0,
            "Красноярский край" to 25.0,
            "Пермский край" to 25.0,
            "Воронежская область" to 25.0,
            "Волгоградская область" to 22.0,
            "Самарская область" to 25.0,
            "Омская область" to 20.0,
            "Тюменская область" to 25.0,
            "Республика Дагестан" to 8.0,
            "Белгородская область" to 20.0,
            "Ставропольский край" to 25.0,
            "Хабаровский край" to 20.0,
            "Архангельская область" to 25.0,
            "Алтайский край" to 20.0
        )

        fun calculateYearlyTax(horsepower: Int, region: String): Double {
            val rate = taxRates[region] ?: 25.0
            return horsepower * rate
        }

        fun calculateMonthlyTax(horsepower: Int, region: String): Double {
            return calculateYearlyTax(horsepower, region) / 12
        }

        fun getRegions(): List<String> {
            return taxRates.keys.sorted()
        }
    }
}