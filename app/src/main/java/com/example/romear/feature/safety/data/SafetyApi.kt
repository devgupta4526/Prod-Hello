package com.example.romear.feature.safety.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SafetyApi {
    @POST("v1/safety/blocks")
    suspend fun blockUser(@Body body: RequestBody): ResponseBody

    @GET("v1/safety/blocks")
    suspend fun listBlocks(): ResponseBody

    @DELETE("v1/safety/blocks/{userId}")
    suspend fun unblockUser(@Path("userId") userId: String): ResponseBody

    @POST("v1/safety/reports")
    suspend fun report(@Body body: RequestBody): ResponseBody

    @GET("v1/safety/me/status")
    suspend fun getSafetyStatus(): ResponseBody

    @POST("v1/safety/appeals")
    suspend fun submitAppeal(@Body body: RequestBody): ResponseBody
}
