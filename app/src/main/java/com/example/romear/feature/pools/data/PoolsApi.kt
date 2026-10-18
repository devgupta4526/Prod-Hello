package com.example.romear.feature.pools.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PoolsApi {
    @POST("v1/pools")
    suspend fun createPool(@Body body: RequestBody): ResponseBody

    @GET("v1/pools/nearby")
    suspend fun listPools(
        @Query("category") category: String? = null,
    ): ResponseBody

    @GET("v1/pools/{id}")
    suspend fun getPool(@Path("id") id: String): ResponseBody

    @POST("v1/pools/{id}/knocks")
    suspend fun knock(
        @Path("id") id: String,
        @Body body: RequestBody,
    ): ResponseBody

    @GET("v1/pools/{id}/knocks")
    suspend fun listKnocks(@Path("id") id: String): ResponseBody

    @POST("v1/pools/{poolId}/knocks/{knockId}/accept")
    suspend fun acceptKnock(
        @Path("poolId") poolId: String,
        @Path("knockId") knockId: String,
    ): ResponseBody

    @POST("v1/pools/{poolId}/knocks/{knockId}/reject")
    suspend fun declineKnock(
        @Path("poolId") poolId: String,
        @Path("knockId") knockId: String,
    ): ResponseBody

    @DELETE("v1/pools/{id}/members/me")
    suspend fun leavePool(@Path("id") id: String): ResponseBody

    @POST("v1/pools/{id}/cancel")
    suspend fun closePool(@Path("id") id: String): ResponseBody
}
