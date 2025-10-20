package com.example.vexodns.network

import com.example.vexodns.data.SubscriptionData
import com.example.vexodns.data.UpdateIpRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    // برای گرفتن اطلاعات اشتراک
    @GET
    suspend fun getSubscriptionData(@Url url: String): Response<SubscriptionData>

    // برای گرفتن IP عمومی کاربر
    @GET
    suspend fun getPublicIp(@Url url: String): Response<String>

    // برای ارسال آپدیت IP
    @POST
    suspend fun updateIp(@Url url: String, @Body request: UpdateIpRequest): Response<ResponseBody>
}