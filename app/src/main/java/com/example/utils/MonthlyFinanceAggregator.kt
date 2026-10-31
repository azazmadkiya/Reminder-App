package com.example.utils

import com.example.data.local.PartyEntity
import com.example.data.local.TransactionEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateRangeGranularity(val title: String, val shortUnit: String) {
    DAILY("Daily", "Days"),
    WEEKLY("Weekly", "Weeks"),
    MONTHLY("Monthly", "Months")
}

data class FinancialTimeBucket(
    val label: String,            // e.g. "Mon 26" or "26 Oct" or "W42" or "Oct"
    val fullLabel: String,        // e.g. "Mon, 26 Oct 2026" or "19 Oct - 25 Oct 2026" or "October 2026"
    val receivableAmount: Double, // Debits/Invoices billed to customers
    val payableAmount: Double,    // Credits/Purchases owed to vendors
    val paymentsCollected: Double, // Actual cash received
    val paymentsPaid: Double,     // Actual cash paid
    val netCashflow: Double,      // receivableAmount - payableAmount
    val transactionCount: Int,
    val year: Int = 0,
    val month: Int = 0,
    val startEpochMs: Long = 0L,
    val endEpochMs: Long = 0L
) {
    // Backward compatibility aliases
    val monthLabel: String get() = label
    val fullMonthLabel: String get() = fullLabel
}

typealias MonthlyBucket = FinancialTimeBucket

enum class HealthStatus(val label: String, val colorHex: Long) {
    EXCELLENT("Excellent Health", 0xFF10B981),
    STRONG("Strong Cashflow", 0xFF059669),
    MODERATE("Moderate / Balanced", 0xFF3B82F6),
    ATTENTION_NEEDED("Attention Needed", 0xFFF59E0B),
    CRITICAL("High Liability Risk", 0xFFEF4444)
}

enum class TipSeverity {
    POSITIVE, INFO, WARNING, ALERT
}

data class FinancialTip(
    val title: String,
    val description: String,
    val severity: TipSeverity
)

data class FinancialHealthMetrics(
    val totalReceivable: Double,
    val totalPayable: Double,
    val netWorkingCapital: Double,
    val quickRatio: Double, // Receivables / Payables
    val healthScore: Int, // 0 - 100
    val healthStatus: HealthStatus,
    val healthSummary: String,
    val overdueReceivablesAmount: Double,
    val collectionEfficiencyPct: Double,
    val tips: List<FinancialTip>
)

data class MonthlyFinanceSummary(
    val buckets: List<FinancialTimeBucket>,
    val maxBarAmount: Double,
    val totalPeriodReceivables: Double,
    val totalPeriodPayables: Double,
    val netPeriodCashflow: Double,
    val healthMetrics: FinancialHealthMetrics,
    val granularity: DateRangeGranularity = DateRangeGranularity.MONTHLY,
    val periodCount: Int = 6,
    val periodDescription: String = "Past 6 Months"
)

object MonthlyFinanceAggregator {

    fun aggregate(
        parties: List<PartyEntity>,
        transactions: List<TransactionEntity>,
        granularity: DateRangeGranularity = DateRangeGranularity.MONTHLY,
        count: Int = 6
    ): MonthlyFinanceSummary {
        val buckets: List<FinancialTimeBucket> = when (granularity) {
            DateRangeGranularity.DAILY -> {
                val dayCount = count.coerceIn(1, 60)
                val now = Calendar.getInstance()
                now.set(Calendar.HOUR_OF_DAY, 0)
                now.set(Calendar.MINUTE, 0)
                now.set(Calendar.SECOND, 0)
                now.set(Calendar.MILLISECOND, 0)

                val dayLabelFormat = if (dayCount <= 7) {
                    SimpleDateFormat("EEE d", Locale.getDefault())
                } else {
                    SimpleDateFormat("d MMM", Locale.getDefault())
                }
                val fullDayFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

                val slots = mutableListOf<FinancialTimeBucket>()
                for (i in (dayCount - 1) downTo 0) {
                    val startCal = now.clone() as Calendar
                    startCal.add(Calendar.DAY_OF_YEAR, -i)
                    val startEpoch = startCal.timeInMillis

                    val endCal = startCal.clone() as Calendar
                    endCal.set(Calendar.HOUR_OF_DAY, 23)
                    endCal.set(Calendar.MINUTE, 59)
                    endCal.set(Calendar.SECOND, 59)
                    endCal.set(Calendar.MILLISECOND, 999)
                    val endEpoch = endCal.timeInMillis

                    val slotTxns = transactions.filter { it.date in startEpoch..endEpoch }
                    var rec = 0.0
                    var pay = 0.0
                    var col = 0.0
                    var pd = 0.0

                    for (txn in slotTxns) {
                        when (txn.type) {
                            "Debit" -> rec += txn.amount
                            "Credit" -> pay += txn.amount
                            "Payment Received" -> {
                                col += txn.amount
                                rec += txn.amount
                            }
                            "Payment Paid" -> {
                                pd += txn.amount
                                pay += txn.amount
                            }
                        }
                    }

                    for (party in parties) {
                        if (party.createdAt in startEpoch..endEpoch && party.openingBalance > 0) {
                            if (party.balanceType == "Receivable") rec += party.openingBalance else pay += party.openingBalance
                        }
                    }

                    slots.add(
                        FinancialTimeBucket(
                            label = dayLabelFormat.format(Date(startEpoch)),
                            fullLabel = fullDayFormat.format(Date(startEpoch)),
                            receivableAmount = rec,
                            payableAmount = pay,
                            paymentsCollected = col,
                            paymentsPaid = pd,
                            netCashflow = rec - pay,
                            transactionCount = slotTxns.size,
                            year = startCal.get(Calendar.YEAR),
                            month = startCal.get(Calendar.MONTH),
                            startEpochMs = startEpoch,
                            endEpochMs = endEpoch
                        )
                    )
                }
                slots
            }

            DateRangeGranularity.WEEKLY -> {
                val weekCount = count.coerceIn(1, 26)
                val now = Calendar.getInstance()
                now.set(Calendar.HOUR_OF_DAY, 0)
                now.set(Calendar.MINUTE, 0)
                now.set(Calendar.SECOND, 0)
                now.set(Calendar.MILLISECOND, 0)
                val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                now.add(Calendar.DAY_OF_YEAR, -daysFromMonday)

                val weekDateLabelFormat = SimpleDateFormat("d MMM", Locale.getDefault())
                val fullDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                val slots = mutableListOf<FinancialTimeBucket>()
                for (i in (weekCount - 1) downTo 0) {
                    val startCal = now.clone() as Calendar
                    startCal.add(Calendar.DAY_OF_YEAR, -i * 7)
                    val startEpoch = startCal.timeInMillis

                    val endCal = startCal.clone() as Calendar
                    endCal.add(Calendar.DAY_OF_YEAR, 6)
                    endCal.set(Calendar.HOUR_OF_DAY, 23)
                    endCal.set(Calendar.MINUTE, 59)
                    endCal.set(Calendar.SECOND, 59)
                    endCal.set(Calendar.MILLISECOND, 999)
                    val endEpoch = endCal.timeInMillis

                    val slotTxns = transactions.filter { it.date in startEpoch..endEpoch }
                    var rec = 0.0
                    var pay = 0.0
                    var col = 0.0
                    var pd = 0.0

                    for (txn in slotTxns) {
                        when (txn.type) {
                            "Debit" -> rec += txn.amount
                            "Credit" -> pay += txn.amount
                            "Payment Received" -> {
                                col += txn.amount
                                rec += txn.amount
                            }
                            "Payment Paid" -> {
                                pd += txn.amount
                                pay += txn.amount
                            }
                        }
                    }

                    for (party in parties) {
                        if (party.createdAt in startEpoch..endEpoch && party.openingBalance > 0) {
                            if (party.balanceType == "Receivable") rec += party.openingBalance else pay += party.openingBalance
                        }
                    }

                    val weekLabel = weekDateLabelFormat.format(Date(startEpoch))
                    val fullWeekLabel = "${weekDateLabelFormat.format(Date(startEpoch))} – ${fullDateFormat.format(Date(endEpoch))}"

                    slots.add(
                        FinancialTimeBucket(
                            label = weekLabel,
                            fullLabel = fullWeekLabel,
                            receivableAmount = rec,
                            payableAmount = pay,
                            paymentsCollected = col,
                            paymentsPaid = pd,
                            netCashflow = rec - pay,
                            transactionCount = slotTxns.size,
                            year = startCal.get(Calendar.YEAR),
                            month = startCal.get(Calendar.MONTH),
                            startEpochMs = startEpoch,
                            endEpochMs = endEpoch
                        )
                    )
                }
                slots
            }

            DateRangeGranularity.MONTHLY -> {
                val monthCount = count.coerceIn(1, 24)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                val monthSlots = mutableListOf<Calendar>()
                for (i in (monthCount - 1) downTo 0) {
                    val cal = calendar.clone() as Calendar
                    cal.add(Calendar.MONTH, -i)
                    monthSlots.add(cal)
                }

                val monthLabelFormat = SimpleDateFormat("MMM", Locale.getDefault())
                val fullMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

                monthSlots.map { slotCal ->
                    val slotYear = slotCal.get(Calendar.YEAR)
                    val slotMonth = slotCal.get(Calendar.MONTH)

                    val startCal = slotCal.clone() as Calendar
                    startCal.set(Calendar.DAY_OF_MONTH, 1)
                    startCal.set(Calendar.HOUR_OF_DAY, 0)
                    startCal.set(Calendar.MINUTE, 0)
                    startCal.set(Calendar.SECOND, 0)
                    startCal.set(Calendar.MILLISECOND, 0)
                    val startEpoch = startCal.timeInMillis

                    val endCal = slotCal.clone() as Calendar
                    endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    endCal.set(Calendar.HOUR_OF_DAY, 23)
                    endCal.set(Calendar.MINUTE, 59)
                    endCal.set(Calendar.SECOND, 59)
                    endCal.set(Calendar.MILLISECOND, 999)
                    val endEpoch = endCal.timeInMillis

                    val monthTxns = transactions.filter { it.date in startEpoch..endEpoch }

                    var monthReceivable = 0.0
                    var monthPayable = 0.0
                    var monthCollected = 0.0
                    var monthPaid = 0.0

                    for (txn in monthTxns) {
                        when (txn.type) {
                            "Debit" -> monthReceivable += txn.amount
                            "Credit" -> monthPayable += txn.amount
                            "Payment Received" -> {
                                monthCollected += txn.amount
                                monthReceivable += txn.amount
                            }
                            "Payment Paid" -> {
                                monthPaid += txn.amount
                                monthPayable += txn.amount
                            }
                        }
                    }

                    for (party in parties) {
                        if (party.createdAt in startEpoch..endEpoch && party.openingBalance > 0) {
                            if (party.balanceType == "Receivable") {
                                monthReceivable += party.openingBalance
                            } else {
                                monthPayable += party.openingBalance
                            }
                        }
                    }

                    FinancialTimeBucket(
                        year = slotYear,
                        month = slotMonth,
                        label = monthLabelFormat.format(Date(startEpoch)),
                        fullLabel = fullMonthFormat.format(Date(startEpoch)),
                        receivableAmount = monthReceivable,
                        payableAmount = monthPayable,
                        paymentsCollected = monthCollected,
                        paymentsPaid = monthPaid,
                        netCashflow = monthReceivable - monthPayable,
                        transactionCount = monthTxns.size,
                        startEpochMs = startEpoch,
                        endEpochMs = endEpoch
                    )
                }
            }
        }

        // Calculate overall metrics
        var totalReceivables = 0.0
        var totalPayables = 0.0

        for (party in parties) {
            var signedBalance = if (party.balanceType == "Receivable") party.openingBalance else -party.openingBalance
            val partyTxns = transactions.filter { it.partyId == party.id }
            for (txn in partyTxns) {
                when (txn.type) {
                    "Payment Received" -> signedBalance -= txn.amount
                    "Payment Paid" -> signedBalance += txn.amount
                    "Debit" -> signedBalance += txn.amount
                    "Credit" -> signedBalance -= txn.amount
                }
            }
            if (signedBalance >= 0) {
                totalReceivables += signedBalance
            } else {
                totalPayables += Math.abs(signedBalance)
            }
        }

        // Calculate aging metrics for overdue risk
        var overdueReceivables = 0.0
        for (party in parties) {
            val partyTxns = transactions.filter { it.partyId == party.id }
            val aging = LedgerAgingUtils.calculateAging(party, partyTxns)
            if (aging.balanceType == "Receivable" && aging.outstandingDays > 30 && aging.netBalance > 0) {
                overdueReceivables += aging.netBalance
            }
        }

        val netCapital = totalReceivables - totalPayables
        val quickRatio = if (totalPayables > 0) totalReceivables / totalPayables else if (totalReceivables > 0) 3.0 else 1.0

        // Collection efficiency
        val totalBilled = buckets.sumOf { it.receivableAmount }
        val totalCollected = buckets.sumOf { it.paymentsCollected }
        val collectionEff = if (totalBilled > 0) (totalCollected / totalBilled * 100.0).coerceIn(0.0, 100.0) else 100.0

        // Compute Financial Health Score (0 to 100)
        var score = 70

        // Liquidity ratio impact (+/- 15)
        when {
            quickRatio >= 1.5 -> score += 15
            quickRatio >= 1.0 -> score += 8
            quickRatio >= 0.7 -> score -= 5
            else -> score -= 20
        }

        // Overdue exposure impact (-15 max)
        if (totalReceivables > 0) {
            val overdueFraction = overdueReceivables / totalReceivables
            if (overdueFraction > 0.4) score -= 15
            else if (overdueFraction > 0.2) score -= 8
            else if (overdueFraction == 0.0) score += 5
        }

        // Net Cashflow trend impact (+/- 10)
        val periodNet = buckets.sumOf { it.netCashflow }
        if (periodNet > 0) score += 10 else if (periodNet < 0) score -= 10

        val healthScore = score.coerceIn(15, 98)

        val healthStatus = when {
            healthScore >= 85 -> HealthStatus.EXCELLENT
            healthScore >= 70 -> HealthStatus.STRONG
            healthScore >= 55 -> HealthStatus.MODERATE
            healthScore >= 40 -> HealthStatus.ATTENTION_NEEDED
            else -> HealthStatus.CRITICAL
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        val healthSummary = when (healthStatus) {
            HealthStatus.EXCELLENT -> "Outstanding financial position with strong cash inflows and minimal overdue exposure."
            HealthStatus.STRONG -> "Healthy cashflow. Receivables comfortably cover current liabilities."
            HealthStatus.MODERATE -> "Balanced position. Monitor aging receivables to maintain positive cashflow."
            HealthStatus.ATTENTION_NEEDED -> "Payables are approaching or exceeding receivables. Prioritize collections."
            HealthStatus.CRITICAL -> "Significant liabilities exceed available receivables. Follow up on dues immediately."
        }

        // Build actionable tips
        val tips = mutableListOf<FinancialTip>()

        if (totalReceivables > totalPayables) {
            val surplus = currencyFormat.format(netCapital)
            tips.add(
                FinancialTip(
                    title = "Positive Working Capital",
                    description = "Your receivables exceed liabilities by $surplus, giving you healthy operating leverage.",
                    severity = TipSeverity.POSITIVE
                )
            )
        } else if (totalPayables > totalReceivables) {
            val deficit = currencyFormat.format(totalPayables - totalReceivables)
            tips.add(
                FinancialTip(
                    title = "Liability Deficit Detected",
                    description = "Pending payables exceed receivables by $deficit. Plan your cash disbursements carefully.",
                    severity = TipSeverity.ALERT
                )
            )
        }

        if (overdueReceivables > 0) {
            val overdueFormatted = currencyFormat.format(overdueReceivables)
            tips.add(
                FinancialTip(
                    title = "Aging Invoices Alert",
                    description = "$overdueFormatted has been pending over 30 days. Send follow-up reminders to accelerate collection.",
                    severity = TipSeverity.WARNING
                )
            )
        } else {
            tips.add(
                FinancialTip(
                    title = "Zero 30+ Day Overdues",
                    description = "No parties have overdue receivables beyond 30 days. Excellent collection discipline!",
                    severity = TipSeverity.POSITIVE
                )
            )
        }

        if (buckets.isNotEmpty()) {
            val latest = buckets.last()
            val latestNetFormatted = currencyFormat.format(Math.abs(latest.netCashflow))
            val periodWord = granularity.shortUnit.dropLast(1).lowercase(Locale.getDefault())
            if (latest.netCashflow >= 0) {
                tips.add(
                    FinancialTip(
                        title = "${latest.label} Net Inflow",
                        description = "Surplus of $latestNetFormatted this $periodWord with ${latest.transactionCount} entries recorded.",
                        severity = TipSeverity.INFO
                    )
                )
            } else {
                tips.add(
                    FinancialTip(
                        title = "${latest.label} Outflow Spike",
                        description = "Net outflow of $latestNetFormatted this $periodWord. Review vendor credits and pending bills.",
                        severity = TipSeverity.WARNING
                    )
                )
            }
        }

        val maxBar = buckets.maxOfOrNull { maxOf(it.receivableAmount, it.payableAmount) } ?: 0.0
        val maxAmount = if (maxBar > 0) maxBar * 1.15 else 10000.0

        val periodDesc = when (granularity) {
            DateRangeGranularity.DAILY -> "Past $count Days"
            DateRangeGranularity.WEEKLY -> "Past $count Weeks"
            DateRangeGranularity.MONTHLY -> "Past $count Months"
        }

        return MonthlyFinanceSummary(
            buckets = buckets,
            maxBarAmount = maxAmount,
            totalPeriodReceivables = buckets.sumOf { it.receivableAmount },
            totalPeriodPayables = buckets.sumOf { it.payableAmount },
            netPeriodCashflow = periodNet,
            healthMetrics = FinancialHealthMetrics(
                totalReceivable = totalReceivables,
                totalPayable = totalPayables,
                netWorkingCapital = netCapital,
                quickRatio = quickRatio,
                healthScore = healthScore,
                healthStatus = healthStatus,
                healthSummary = healthSummary,
                overdueReceivablesAmount = overdueReceivables,
                collectionEfficiencyPct = collectionEff,
                tips = tips
            ),
            granularity = granularity,
            periodCount = count,
            periodDescription = periodDesc
        )
    }
}
