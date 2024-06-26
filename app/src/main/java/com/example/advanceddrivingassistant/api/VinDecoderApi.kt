package com.example.advanceddrivingassistant.api

import com.example.advanceddrivingassistant.dto.DecodeResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface VinDecoderApi {
    @GET("{apiKey}/{controlSum}/decode/{vin}.json")
    fun decodeVin(
        @Path("apiKey") apiKey: String,
        @Path("controlSum") controlSum: String,
        @Path("vin") vin: String
    ): Call<DecodeResponse>
}