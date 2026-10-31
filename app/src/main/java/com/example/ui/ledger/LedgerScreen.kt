package com.example.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAddVoucherClick: () -> Unit,
    onPartyClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ReminderApplication).container.appRepository
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val summaries by viewModel.partyLedgerSummaries.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Receivable", "Payable"

    val format = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val totalReceivable = remember(summaries) {
        summaries.filter { it.balanceType == "Receivable" && it.netBalance > 0.01 }.sumOf { it.netBalance }
    }
    val totalPayable = remember(summaries) {
        summaries.filter { it.balanceType == "Payable" && it.netBalance > 0.01 }.sumOf { it.netBalance }
    }
    val receivableCount = remember(summaries) {
        summaries.count { it.balanceType == "Receivable" && it.netBalance > 0.01 }
    }
    val payableCount = remember(summaries) {
        summaries.count { it.balanceType == "Payable" && it.netBalance > 0.01 }
    }

    val filteredSummaries = remember(searchQuery, summaries, selectedFilter) {
        summaries.filter { summary ->
            val matchesSearch = searchQuery.isBlank() ||
                summary.party.name.contains(searchQuery, ignoreCase = true) ||
                summary.party.businessName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Receivable" -> summary.balanceType == "Receivable" && summary.netBalance > 0.01
                "Payable" -> summary.balanceType == "Payable" && summary.netBalance > 0.01
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddVoucherClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_voucher_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Amount Voucher")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Outstanding Summary for Receivable & Payable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "To Receive (Receivable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = format.format(totalReceivable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                        Text(
                            text = "$receivableCount pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF047857).copy(alpha = 0.8f)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "To Pay (Payable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = format.format(totalPayable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                        Text(
                            text = "$payableCount pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB91C1C).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search by name...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = { Text("All (${summaries.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "Receivable",
                        onClick = { selectedFilter = "Receivable" },
                        label = { Text("Receivable ($receivableCount)") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == "Payable",
                        onClick = { selectedFilter = "Payable" },
                        label = { Text("Payable ($payableCount)") }
                    )
                }
            }

            if (filteredSummaries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (summaries.isEmpty()) "No parties found. Please add a party first." else "No results matching your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSummaries) { summary ->
                        LedgerSummaryCard(
                            summary = summary,
                            dateFormatter = dateFormatter,
                            onClick = { onPartyClick(summary.party.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerSummaryCard(
    summary: PartyLedgerSummary,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit
) {
    val isReceivable = summary.balanceType == "Receivable"
    val isPending = summary.netBalance > 0.01

    val balanceColor = if (isPending) {
        if (isReceivable) Color(0xFF047857) else Color(0xFFB91C1C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val badgeBgColor = if (isPending) {
        if (isReceivable) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.party.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.party.businessName.isNotBlank()) {
                    Text(
                        text = summary.party.businessName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Outstanding Pending Days Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBgColor,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPending) {
                            Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = balanceColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (summary.outstandingDays == 0) "Pending: Today" else "${summary.outstandingDays} Days Pending",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = balanceColor,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = "Settled (₹0)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (isPending && summary.oldestPendingDate != null) {
                    Text(
                        text = "Pending since ${dateFormatter.format(Date(summary.oldestPendingDate))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹%.2f".format(summary.netBalance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Text(
                    text = if (isPending) summary.balanceType else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = balanceColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
