package com.example.vexodns.network // این خط به صورت خودکار اضافه می‌شود

import com.example.vexodns.data.SubscriptionData // این خط را اضافه کنید
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ApiService {
    @GET
    suspend fun getSubscriptionData(@Url url: String): Response<SubscriptionData>
}