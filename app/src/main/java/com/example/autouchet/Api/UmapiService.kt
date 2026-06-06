package com.example.autouchet.Api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface UmapiService {

    @GET("v2/autocatalog/{languageCode}-{regionCode}/BrandRefinement/{article}")
    suspend fun searchParts(
        @Header("X-App-Key") apiKey: String,
        @Path("languageCode") languageCode: String = "ru",
        @Path("regionCode") regionCode: String = "RU",
        @Path("article") article: String,
        @Query("limit") limit: Int = 20
    ): Response<List<UmapiPart>>
}