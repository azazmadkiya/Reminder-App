package com.example.utils

import com.example.data.local.PartyEntity
import com.example.data.local.TransactionEntity
import java.util.Calendar

data class AgingResult(
    val netBalance: Double,
    val rawBalance: Double,
    val balanceType: String, // "Receivable" or "Payable"
    val outstandingDays: Int, // e.g. 15
    val oldestPendingDate: Long?,
    val displayBadge: String, // "15 Days Pending", "Pending Today", "Settled"
    val daysPendingLabel: String // "Pending since 15 days", "Pending today", "All settled"
)

object LedgerAgingUtils {

    private class AgingEntry(
        var remainingAmount: Double,
        val date: Long,
        val type: String
    )

    fun calculateAging(
        party: PartyEntity,
        transactions: List<TransactionEntity>
    ): AgingResult {
        // Sort transactions chronologically
        val sortedTxns = transactions.sortedWith(compareBy({ it.date }, { it.id }))

        // Calculate signed balance: positive = Receivable (they owe us), negative = Payable (we owe them)
        var signedBalance = if (party.balanceType == "Receivable") party.openingBalance else -party.openingBalance
        for (txn in sortedTxns) {
            when (txn.type) {
                "Payment Received" -> signedBalance -= txn.amount
                "Payment Paid" -> signedBalance += txn.amount
                "Debit" -> signedBalance += txn.amount
                "Credit" -> signedBalance -= txn.amount
            }
        }

        val netBalance = Math.abs(signedBalance)
        val isReceivable = signedBalance >= 0
        val balanceType = if (isReceivable) "Receivable" else "Payable"

        // If settled (net balance is zero)
        if (netBalance < 0.01) {
            return AgingResult(
                netBalance = 0.0,
                rawBalance = 0.0,
                balanceType = balanceType,
                outstandingDays = 0,
                oldestPendingDate = null,
                displayBadge = "Settled",
                daysPendingLabel = "All settled (₹0)"
            )
        }

        // Trace oldest unsettled amount using FIFO matching
        var oldestPendingDate: Long? = null

        if (isReceivable) {
            // Receivable: entries that created debt
            val debitEntries = mutableListOf<AgingEntry>()
            if (party.balanceType == "Receivable" && party.openingBalance > 0) {
                debitEntries.add(AgingEntry(party.openingBalance, party.createdAt, "Opening Balance"))
            }
            for (txn in sortedTxns) {
                if (txn.type == "Debit" || txn.type == "Payment Paid") {
                    debitEntries.add(AgingEntry(txn.amount, txn.date, txn.type))
                }
            }

            // Total credits that settle this receivable
            var creditsToApply = 0.0
            if (party.balanceType == "Payable" && party.openingBalance > 0) {
                creditsToApply += party.openingBalance
            }
            for (txn in sortedTxns) {
                if (txn.type == "Payment Received" || txn.type == "Credit") {
                    creditsToApply += txn.amount
                }
            }

            // Deduct credits from earliest debit entries
            for (entry in debitEntries) {
                if (creditsToApply <= 0) break
                val deduct = minOf(entry.remainingAmount, creditsToApply)
                entry.remainingAmount -= deduct
                creditsToApply -= deduct
            }

            val unsettled = debitEntries.filter { it.remainingAmount > 0.01 }
            oldestPendingDate = unsettled.firstOrNull()?.date ?: party.createdAt
        } else {
            // Payable: entries that created our liability
            val creditEntries = mutableListOf<AgingEntry>()
            if (party.balanceType == "Payable" && party.openingBalance > 0) {
                creditEntries.add(AgingEntry(party.openingBalance, party.createdAt, "Opening Balance"))
            }
            for (txn in sortedTxns) {
                if (txn.type == "Credit" || txn.type == "Payment Received") {
                    creditEntries.add(AgingEntry(txn.amount, txn.date, txn.type))
                }
            }

            // Total debits that settle this payable
            var debitsToApply = 0.0
            if (party.balanceType == "Receivable" && party.openingBalance > 0) {
                debitsToApply += party.openingBalance
            }
            for (txn in sortedTxns) {
                if (txn.type == "Payment Paid" || txn.type == "Debit") {
                    debitsToApply += txn.amount
                }
            }

            // Deduct debits from earliest credit entries
            for (entry in creditEntries) {
                if (debitsToApply <= 0) break
                val deduct = minOf(entry.remainingAmount, debitsToApply)
                entry.remainingAmount -= deduct
                debitsToApply -= deduct
            }

            val unsettled = creditEntries.filter { it.remainingAmount > 0.01 }
            oldestPendingDate = unsettled.firstOrNull()?.date ?: party.createdAt
        }

        val days = getDaysAgo(oldestPendingDate)

        val displayBadge = when (days) {
            0 -> "Pending: Today"
            1 -> "1 Day Pending"
            else -> "$days Days Pending"
        }

        val daysPendingLabel = when (days) {
            0 -> "Pending since today"
            1 -> "Pending since 1 day"
            else -> "Pending since $days days"
        }

        return AgingResult(
            netBalance = netBalance,
            rawBalance = signedBalance,
            balanceType = balanceType,
            outstandingDays = days,
            oldestPendingDate = oldestPendingDate,
            displayBadge = displayBadge,
            daysPendingLabel = daysPendingLabel
        )
    }

    fun getDaysAgo(dateMillis: Long): Int {
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = nowCal.timeInMillis - targetCal.timeInMillis
        return maxOf(0, (diffMillis / (1000L * 60 * 60 * 24)).toInt())
    }

    fun formatDaysAgo(dateMillis: Long): String {
        val days = getDaysAgo(dateMillis)
        return when (days) {
            0 -> "Today"
            1 -> "1 day ago"
            else -> "$days days ago"
        }
    }
}
