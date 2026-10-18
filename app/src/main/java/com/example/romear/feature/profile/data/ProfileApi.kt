package com.example.romear.feature.profile.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface ProfileApi {
    @GET("v1/me/profile")
    suspend fun getMyProfile(): ResponseBody

    @retrofit2.http.PATCH("v1/me/profile")
    suspend fun updateProfile(@Body body: RequestBody): ResponseBody

    @POST("v1/me/data-export")
    suspend fun requestDataExport(): ResponseBody

    @POST("v1/me/delete")
    suspend fun requestAccountDeletion(): ResponseBody

    @POST("v1/me/delete/cancel")
    suspend fun cancelAccountDeletion(): ResponseBody
}
