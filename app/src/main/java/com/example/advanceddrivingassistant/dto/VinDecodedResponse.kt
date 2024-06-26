package com.example.advanceddrivingassistant.dto

data class DecodeResponse(
    val decode: List<DecodeItem>
)

data class DecodeItem(
    val label: String,
    val value: Any
)
