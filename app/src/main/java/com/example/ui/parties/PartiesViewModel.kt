package com.example.ui.parties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PartyEntity
import com.example.data.repository.AppRepository
import com.example.ui.ledger.PartyLedgerSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PartiesViewModel(private val repository: AppRepository) : ViewModel() {
    val uiState: StateFlow<List<PartyLedgerSummary>> = combine(
        repository.allParties,
        repository.allTransactions
    ) { parties, transactions ->
        parties.map { party ->
            var balance = if (party.balanceType == "Receivable") party.openingBalance else -party.openingBalance
            val partyTransactions = transactions.filter { it.partyId == party.id }
            for (txn in partyTransactions) {
                when (txn.type) {
                    "Payment Received" -> balance -= txn.amount
                    "Payment Paid" -> balance += txn.amount
                    "Debit" -> balance += txn.amount
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
        
    fun addParty(party: PartyEntity) {
        viewModelScope.launch {
            repository.insertParty(party)
        }
    }

    fun updateParty(party: PartyEntity) {
        viewModelScope.launch {
            repository.updateParty(party)
        }
    }

    fun deleteParty(partyId: Long) {
        viewModelScope.launch {
            repository.deletePartyById(partyId)
        }
    }

    suspend fun getParty(id: Long): PartyEntity? {
        return repository.getPartyById(id)
    }
}

class PartiesViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PartiesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PartiesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
