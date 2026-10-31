package com.example.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PartyEntity
import com.example.data.local.TransactionEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LedgerDetailViewModel(
    private val repository: AppRepository,
    val partyId: Long
) : ViewModel() {

    private val _party = MutableStateFlow<PartyEntity?>(null)
    val party: StateFlow<PartyEntity?> = _party.asStateFlow()

    init {
        viewModelScope.launch {
            _party.value = repository.getPartyById(partyId)
        }
    }

    val partyTransactions: StateFlow<List<TransactionEntity>> = repository.getTransactionsForParty(partyId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    fun calculateAging(party: PartyEntity, transactions: List<TransactionEntity>): com.example.utils.AgingResult {
        return com.example.utils.LedgerAgingUtils.calculateAging(party, transactions)
    }

    // Calculate net balance synchronously based on transactions flow
    fun calculateBalance(party: PartyEntity, transactions: List<TransactionEntity>): Pair<Double, String> {
        val aging = calculateAging(party, transactions)
        return Pair(aging.netBalance, aging.balanceType)
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransactionById(transaction.id)
        }
    }

    fun insertTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }
}

class LedgerDetailViewModelFactory(
    private val repository: AppRepository,
    private val partyId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerDetailViewModel(repository, partyId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
