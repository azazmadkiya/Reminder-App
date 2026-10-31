package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppRepository
import com.example.utils.DateRangeGranularity
import com.example.utils.MonthlyFinanceAggregator
import com.example.utils.MonthlyFinanceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FinancialDashboardUiState(
    val granularity: DateRangeGranularity = DateRangeGranularity.MONTHLY,
    val periodCount: Int = 6,
    val summary: MonthlyFinanceSummary? = null,
    val isLoading: Boolean = true
) {
    // Backward compatibility
    val selectedMonths: Int get() = periodCount
}

class FinancialDashboardViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _granularity = MutableStateFlow(DateRangeGranularity.MONTHLY)
    private val _periodCount = MutableStateFlow(6)

    val uiState: StateFlow<FinancialDashboardUiState> = combine(
        repository.allParties,
        repository.allTransactions,
        _granularity,
        _periodCount
    ) { parties, transactions, granularity, count ->
        val summary = MonthlyFinanceAggregator.aggregate(
            parties = parties,
            transactions = transactions,
            granularity = granularity,
            count = count
        )
        FinancialDashboardUiState(
            granularity = granularity,
            periodCount = count,
            summary = summary,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinancialDashboardUiState()
    )

    fun setGranularity(granularity: DateRangeGranularity) {
        _granularity.value = granularity
        // Default period counts for optimal visualization per granularity
        _periodCount.value = when (granularity) {
            DateRangeGranularity.DAILY -> 7
            DateRangeGranularity.WEEKLY -> 4
            DateRangeGranularity.MONTHLY -> 6
        }
    }

    fun setPeriodCount(count: Int) {
        _periodCount.value = count
    }

    // Backward compatibility helper
    fun setTimeframe(months: Int) {
        _granularity.value = DateRangeGranularity.MONTHLY
        _periodCount.value = months
    }
}

class FinancialDashboardViewModelFactory(
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinancialDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinancialDashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
