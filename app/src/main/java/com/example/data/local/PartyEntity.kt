package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val businessName: String,
    val mobile: String,
    val email: String,
    val gstin: String,
    val pan: String,
    val address: String,
    val openingBalance: Double,
    val balanceType: String, // "Receivable" or "Payable"
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
