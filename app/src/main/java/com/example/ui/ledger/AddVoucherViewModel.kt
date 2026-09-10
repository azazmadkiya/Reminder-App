package com.example.ui.ledger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransactionEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddVoucherViewModel(private val repository: AppRepository) : ViewModel() {

    val parties = repository.allParties.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var selectedPartyId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(System.currentTimeMillis())
    var type by mutableStateOf("Payment Received")
    var amount by mutableStateOf("")
    var paymentMode by mutableStateOf("Cash")
    var referenceNumber by mutableStateOf("")
    var description by mutableStateOf("")

    fun saveVoucher(onSuccess: () -> Unit) {
        val currentPartyId = selectedPartyId ?: return
        val parsedAmount = amount.toDoubleOrNull() ?: 0.0

        val transaction = TransactionEntity(
            partyId = currentPartyId,
            date = date,
            type = type,
            amount = parsedAmount,
            paymentMode = paymentMode,
            referenceNumber = referenceNumber,
            description = description
        )

        viewModelScope.launch {
            repository.insertTransaction(transaction)
            onSuccess()
        }
    }
}

class AddVoucherViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddVoucherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddVoucherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
