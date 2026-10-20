package com.example.romear.feature.chat.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("v1/conversations")
    suspend fun getConversations(
        @Query("before") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ResponseBody

    @GET("v1/conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: String,
        @Query("before") cursor: String? = null,
        @Query("limit") limit: Int = 50,
    ): ResponseBody

    @POST("v1/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body body: RequestBody,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): ResponseBody

    @POST("v1/conversations/{id}/ack")
    suspend fun acknowledge(
        @Path("id") conversationId: String,
        @Body body: RequestBody,
    ): ResponseBody
}
