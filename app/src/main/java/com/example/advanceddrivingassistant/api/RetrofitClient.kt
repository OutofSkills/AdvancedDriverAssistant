package com.example.advanceddrivingassistant.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val api: VinDecoderApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.vindecoder.eu/3.2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VinDecoderApi::class.java)
    }
}