package com.example.romear.feature.events.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EventsApi {
    @GET("v1/events/nearby")
    suspend fun listEvents(
        @Query("category") category: String? = null,
    ): ResponseBody

    @GET("v1/events/{id}")
    suspend fun getEvent(@Path("id") id: String): ResponseBody

    @POST("v1/events/{id}/orders")
    suspend fun createOrder(
        @Path("id") eventId: String,
        @Body body: RequestBody,
    ): ResponseBody

    @POST("v1/orders/{id}/confirm")
    suspend fun confirmPayment(
        @Path("id") orderId: String,
        @Body body: RequestBody,
    ): ResponseBody

    @GET("v1/me/tickets")
    suspend fun listMyTickets(): ResponseBody

    @GET("v1/tickets/{id}")
    suspend fun getTicket(@Path("id") id: String): ResponseBody
}
