package com.example.myapplicationbetat
import java.util.Date
data class HistoryItem(
    val periodLabel: String,
    val totalConsumption: Double,
    val totalInjection: Double,
    val periodStartDate: Date
)
