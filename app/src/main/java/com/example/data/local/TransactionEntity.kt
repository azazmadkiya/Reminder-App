package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partyId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val date: Long,
    val type: String, // "Payment Received", "Payment Paid", "Credit", "Debit", "Opening Balance", "Adjustment"
    val amount: Double,
    val paymentMode: String, // "Cash", "Bank", "UPI", "Cheque", "Other"
    val referenceNumber: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)
