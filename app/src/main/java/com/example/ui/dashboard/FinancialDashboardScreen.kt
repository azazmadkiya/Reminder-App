package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import com.example.utils.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDashboardScreen(
    onBackClick: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToParties: () -> Unit = {},
    onNavigateToLedger: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val viewModel: FinancialDashboardViewModel = viewModel(
        factory = FinancialDashboardViewModelFactory(appContainer.appRepository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val summary = uiState.summary
    val health = summary?.healthMetrics

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Financial Dashboard", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${uiState.granularity.title} View (${summary?.periodDescription ?: ""})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToLedger) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Ledger")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (summary == null || uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("financial_dashboard_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Granularity & Date Range Filter Section
                item {
                    DateRangeFilterCard(
                        selectedGranularity = uiState.granularity,
                        selectedCount = uiState.periodCount,
                        onGranularitySelected = { viewModel.setGranularity(it) },
                        onCountSelected = { viewModel.setPeriodCount(it) },
                        periodDescription = summary.periodDescription
                    )
                }

                // 2. Period Performance Snapshot (Receivables, Payables, Net in selected period)
                item {
                    PeriodKpiSection(
                        summary = summary,
                        currencyFormat = currencyFormat,
                        granularity = uiState.granularity
                    )
                }

                // 3. Recharts-style Visualizer (Payables vs Receivables)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            MonthlyFinancialChart(
                                buckets = summary.buckets,
                                maxAmount = summary.maxBarAmount,
                                title = "${uiState.granularity.title} Payables vs Receivables",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 4. Overall Financial Health Score Hero Card
                item {
                    HealthScoreHeroCard(health = health!!, currencyFormat = currencyFormat)
                }

                // 5. Total Balance Standing (All-Time Ledger Balances)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricKpiCard(
                            title = "Total Receivable",
                            amount = currencyFormat.format(health!!.totalReceivable),
                            subtext = "Total owed by parties",
                            color = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToParties
                        )
                        MetricKpiCard(
                            title = "Total Payable",
                            amount = currencyFormat.format(health.totalPayable),
                            subtext = "Total owed to vendors",
                            color = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToParties
                        )
                    }
                }

                // 6. Actionable Insights
                if (health!!.tips.isNotEmpty()) {
                    item {
                        Text(
                            text = "Financial Health Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(health.tips) { tip ->
                        FinancialTipCard(tip = tip)
                    }
                }

                // 7. Granular Performance Breakdown List (Daily / Weekly / Monthly)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.granularity.title} Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${summary.buckets.size} intervals",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(summary.buckets.reversed()) { bucket ->
                    FinancialBucketBreakdownRow(
                        bucket = bucket,
                        format = currencyFormat,
                        granularity = uiState.granularity
                    )
                }
            }
        }
    }
}

@Composable
private fun DateRangeFilterCard(
    selectedGranularity: DateRangeGranularity,
    selectedCount: Int,
    onGranularitySelected: (DateRangeGranularity) -> Unit,
    onCountSelected: (Int) -> Unit,
    periodDescription: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("date_range_filter_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Timeframe View",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = periodDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Granularity Toggle Segmented Control (Daily / Weekly / Monthly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GranularitySegmentTab(
                    title = "Daily",
                    icon = Icons.Filled.Today,
                    isSelected = selectedGranularity == DateRangeGranularity.DAILY,
                    onClick = { onGranularitySelected(DateRangeGranularity.DAILY) },
                    modifier = Modifier.weight(1f).testTag("granularity_daily")
                )
                GranularitySegmentTab(
                    title = "Weekly",
                    icon = Icons.Filled.ViewWeek,
                    isSelected = selectedGranularity == DateRangeGranularity.WEEKLY,
                    onClick = { onGranularitySelected(DateRangeGranularity.WEEKLY) },
                    modifier = Modifier.weight(1f).testTag("granularity_weekly")
                )
                GranularitySegmentTab(
                    title = "Monthly",
                    icon = Icons.Filled.CalendarMonth,
                    isSelected = selectedGranularity == DateRangeGranularity.MONTHLY,
                    onClick = { onGranularitySelected(DateRangeGranularity.MONTHLY) },
                    modifier = Modifier.weight(1f).testTag("granularity_monthly")
                )
            }

            // Sub-range chips for selected granularity
            val countOptions = when (selectedGranularity) {
                DateRangeGranularity.DAILY -> listOf(7, 14, 30)
                DateRangeGranularity.WEEKLY -> listOf(4, 8, 12)
                DateRangeGranularity.MONTHLY -> listOf(3, 6, 12)
            }
            val unitLabel = when (selectedGranularity) {
                DateRangeGranularity.DAILY -> "D"
                DateRangeGranularity.WEEKLY -> "W"
                DateRangeGranularity.MONTHLY -> "M"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Range:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                countOptions.forEach { count ->
                    val isSelected = selectedCount == count
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCountSelected(count) },
                        label = {
                            Text(
                                text = "$count$unitLabel",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("filter_chip_${selectedGranularity.name.lowercase()}_$count"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GranularitySegmentTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "segment_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "segment_content"
    )

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun PeriodKpiSection(
    summary: MonthlyFinanceSummary,
    currencyFormat: NumberFormat,
    granularity: DateRangeGranularity
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PeriodMetricCard(
                title = "Period Inflows",
                amount = currencyFormat.format(summary.totalPeriodReceivables),
                badge = "${summary.periodCount} ${granularity.shortUnit}",
                color = Color(0xFF10B981),
                icon = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
            PeriodMetricCard(
                title = "Period Outflows",
                amount = currencyFormat.format(summary.totalPeriodPayables),
                badge = "${summary.periodCount} ${granularity.shortUnit}",
                color = Color(0xFFEF4444),
                icon = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
        }

        // Net Cashflow Bar Card
        val net = summary.netPeriodCashflow
        val netColor = if (net >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
        Card(
            colors = CardDefaults.cardColors(containerColor = netColor.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (net >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = netColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Net Period Cashflow:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = (if (net >= 0) "+ " else "- ") + currencyFormat.format(Math.abs(net)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
            }
        }
    }
}

@Composable
private fun PeriodMetricCard(
    title: String,
    amount: String,
    badge: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Recorded in $badge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HealthScoreHeroCard(
    health: FinancialHealthMetrics,
    currencyFormat: NumberFormat
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(health.healthStatus.colorHex).copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color(health.healthStatus.colorHex).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = health.healthStatus.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(health.healthStatus.colorHex),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Financial Health Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = health.healthSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Circular Score Display
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(health.healthStatus.colorHex).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${health.healthScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(health.healthStatus.colorHex)
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricKpiCard(
    title: String,
    amount: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtext, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinancialTipCard(tip: FinancialTip) {
    val (icon, tint) = when (tip.severity) {
        TipSeverity.POSITIVE -> Icons.Filled.CheckCircle to Color(0xFF10B981)
        TipSeverity.INFO -> Icons.Filled.Info to Color(0xFF3B82F6)
        TipSeverity.WARNING -> Icons.Filled.Warning to Color(0xFFF59E0B)
        TipSeverity.ALERT -> Icons.Filled.Error to Color(0xFFEF4444)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tip.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FinancialBucketBreakdownRow(
    bucket: FinancialTimeBucket,
    format: NumberFormat,
    granularity: DateRangeGranularity
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bucket.fullLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${bucket.transactionCount} transactions recorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val net = bucket.netCashflow
                val netColor = if (net >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                Surface(
                    color = netColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = (if (net >= 0) "Net +" else "Net -") + format.format(Math.abs(net)),
                        color = netColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bar proportion visualization
            val totalBucket = (bucket.receivableAmount + bucket.payableAmount).coerceAtLeast(1.0)
            val recShare = (bucket.receivableAmount / totalBucket).toFloat()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.LightGray.copy(alpha = 0.3f))
            ) {
                if (bucket.receivableAmount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(recShare.coerceAtLeast(0.01f))
                            .background(Color(0xFF10B981))
                    )
                }
                if (bucket.payableAmount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight((1f - recShare).coerceAtLeast(0.01f))
                            .background(Color(0xFFEF4444))
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Receivable: ${format.format(bucket.receivableAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Payable: ${format.format(bucket.payableAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
