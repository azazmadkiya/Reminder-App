package com.example.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PartyEntity
import com.example.data.local.TransactionEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PartyLedgerSummary(
    val party: PartyEntity,
    val netBalance: Double,
    val balanceType: String // "Receivable" or "Payable"
)

class LedgerViewModel(private val repository: AppRepository) : ViewModel() {

    val partyLedgerSummaries: StateFlow<List<PartyLedgerSummary>> = combine(
        repository.allParties,
        repository.allTransactions
    ) { parties, transactions ->
        parties.map { party ->
            // Calculate net balance for the party
            var balance = if (party.balanceType == "Receivable") party.openingBalance else -party.openingBalance

            val partyTransactions = transactions.filter { it.partyId == party.id }
            for (txn in partyTransactions) {
                // "Payment Received" means we got money from them (decreases receivable balance)
                // "Payment Paid" means we paid money to them (increases receivable balance, or decreases payable)
                // "Credit" usually means they owe us (increases receivable)
                // "Debit" means we owe them (decreases receivable)
                when (txn.type) {
                    "Payment Received" -> balance -= txn.amount
                    "Payment Paid" -> balance += txn.amount
                    "Debit" -> balance += txn.amount // Invoice sent to them
                    "Credit" -> balance -= txn.amount
                }
            }

            PartyLedgerSummary(
                party = party,
                netBalance = Math.abs(balance),
                balanceType = if (balance >= 0) "Receivable" else "Payable"
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

class LedgerViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
