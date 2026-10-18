package com.example.romear.core.network

import okhttp3.ResponseBody
import retrofit2.http.GET

interface FoundationApi {
    @GET("health")
    suspend fun health(): ResponseBody

    @GET("v1/config")
    suspend fun config(): ResponseBody
}
