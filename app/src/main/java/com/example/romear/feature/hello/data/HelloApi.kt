package com.example.romear.feature.hello.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HelloApi {
    @POST("v1/hellos")
    suspend fun sendHello(@Body body: RequestBody): ResponseBody

    @GET("v1/hellos")
    suspend fun listHellos(
        @Query("box") box: String = "received",
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ResponseBody

    @GET("v1/hellos/{id}")
    suspend fun getHello(@Path("id") id: String): ResponseBody

    @POST("v1/hellos/{id}/accept")
    suspend fun acceptHello(@Path("id") id: String): ResponseBody

    @POST("v1/hellos/{id}/decline")
    suspend fun declineHello(@Path("id") id: String): ResponseBody

    @POST("v1/hellos/{id}/withdraw")
    suspend fun withdrawHello(@Path("id") id: String): ResponseBody

    @GET("v1/connections")
    suspend fun listConnections(): ResponseBody

    @DELETE("v1/connections/{id}")
    suspend fun disconnect(@Path("id") id: String): ResponseBody
}
