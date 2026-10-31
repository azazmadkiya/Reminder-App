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
    val balanceType: String, // "Receivable" or "Payable"
    val outstandingDays: Int = 0,
    val oldestPendingDate: Long? = null,
    val statusText: String = ""
)

class LedgerViewModel(private val repository: AppRepository) : ViewModel() {

    val partyLedgerSummaries: StateFlow<List<PartyLedgerSummary>> = combine(
        repository.allParties,
        repository.allTransactions
    ) { parties, transactions ->
        parties.map { party ->
            val partyTransactions = transactions.filter { it.partyId == party.id }
            val aging = com.example.utils.LedgerAgingUtils.calculateAging(party, partyTransactions)
            PartyLedgerSummary(
                party = party,
                netBalance = aging.netBalance,
                balanceType = aging.balanceType,
                outstandingDays = aging.outstandingDays,
                oldestPendingDate = aging.oldestPendingDate,
                statusText = aging.displayBadge
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
