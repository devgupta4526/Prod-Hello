package com.example.romear.feature.discovery.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscoveryApi {
    @PUT("v1/presence")
    suspend fun updatePresence(@Body body: RequestBody): ResponseBody

    @DELETE("v1/presence")
    suspend fun clearPresence(): ResponseBody

    @GET("v1/discovery/nearby")
    suspend fun getNearbyUsers(
        @Query("radiusKm") radiusKm: Double? = null,
        @Query("interests") interests: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ResponseBody

    @GET("v1/users/{id}")
    suspend fun getUserProfile(@Path("id") id: String): ResponseBody
}
