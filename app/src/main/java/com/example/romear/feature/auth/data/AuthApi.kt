package com.example.romear.feature.auth.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

interface AuthApi {
    @POST("v1/auth/otp/request") suspend fun requestOtp(@Body body: RequestBody): ResponseBody
    @POST("v1/auth/otp/verify") suspend fun verifyOtp(@Body body: RequestBody): ResponseBody
    @POST("v1/auth/refresh") suspend fun refresh(@Body body: RequestBody): ResponseBody
    @POST("v1/auth/logout") suspend fun logout(): ResponseBody
    @GET("v1/auth/sessions") suspend fun sessions(): ResponseBody
    @DELETE("v1/auth/sessions/{id}") suspend fun revokeSession(@Path("id") id: String): ResponseBody
    @GET("v1/me") suspend fun me(): ResponseBody
    @PUT("v1/me/dob") suspend fun dob(@Body body: RequestBody): ResponseBody
    @PATCH("v1/me/profile") suspend fun profile(@Body body: RequestBody): ResponseBody
    @POST("v1/me/guidelines/accept") suspend fun guidelines(): ResponseBody
    @POST("v1/me/photos/upload-url") suspend fun uploadUrl(): ResponseBody
    @POST("v1/me/photos") suspend fun confirmPhoto(@Body body: RequestBody): ResponseBody
}
