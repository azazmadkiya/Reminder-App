package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppRepository
import com.example.data.network.VoiceParserService
import com.example.notification.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val todayReminders: Int = 0,
    val upcomingReminders: Int = 0,
    val overdueReminders: Int = 0,
    val paymentToReceive: Double = 0.0,
    val paymentToPay: Double = 0.0,
    val totalParties: Int = 0,
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val repository: AppRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allParties,
        repository.allReminders,
        repository.allTransactions
    ) { parties, reminders, transactions ->
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        
        val overdue = reminders.count { it.status == "Overdue" || (it.dueDate < now && it.status != "Completed") }
        val today = reminders.count { Math.abs(it.dueDate - now) < oneDayMillis && it.status != "Completed" }
        val upcoming = reminders.count { it.dueDate > now + oneDayMillis && it.status != "Completed" }
        
        var totalReceivable = 0.0
        var totalPayable = 0.0
        
        for (party in parties) {
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
            if (balance >= 0) {
                totalReceivable += balance
            } else {
                totalPayable += Math.abs(balance)
            }
        }
        
        HomeUiState(
            todayReminders = today,
            upcomingReminders = upcoming,
            overdueReminders = overdue,
            paymentToReceive = totalReceivable,
            paymentToPay = totalPayable,
            totalParties = parties.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun processVoiceCommand(text: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onMessage("Analyzing spoken task...")
                val parser = VoiceParserService()
                val result = parser.createTaskFromVoice(text, repository, alarmScheduler)
                result.fold(
                    onSuccess = { reminder ->
                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(reminder.dueDate))
                        val party = reminder.partyId?.let { repository.getPartyById(it) }
                        val partyInfo = if (party != null) " for ${party.name}" else ""
                        val amountInfo = if (reminder.amount != null && reminder.amount > 0) " (₹${reminder.amount.toLong()})" else ""
                        onMessage("✓ Created [${reminder.tag}] Task: '${reminder.title}'$partyInfo$amountInfo due $dateStr")
                    },
                    onFailure = { error ->
                        onMessage("Failed to create task: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                onMessage("Voice processing failed: ${e.message}")
            }
        }
    }
}

class HomeViewModelFactory(
    private val repository: AppRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, alarmScheduler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

