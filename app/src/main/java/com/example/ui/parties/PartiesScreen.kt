package com.example.ui.parties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
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
import com.example.data.local.PartyEntity
import com.example.ui.ledger.PartyLedgerSummary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    onAddPartyClick: () -> Unit = {},
    onEditPartyClick: (Long) -> Unit = {},
    onPartyClick: (Long) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val viewModel: PartiesViewModel = viewModel(
        factory = PartiesViewModelFactory(appContainer.appRepository)
    )
    val parties by viewModel.uiState.collectAsStateWithLifecycle()

    var partyToDelete by remember { mutableStateOf<PartyEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Receivable", "Payable"

    val format = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    // Aggregate statistics
    val totalReceivable = remember(parties) {
        parties.filter { it.balanceType == "Receivable" && it.netBalance > 0.01 }.sumOf { it.netBalance }
    }
    val totalPayable = remember(parties) {
        parties.filter { it.balanceType == "Payable" && it.netBalance > 0.01 }.sumOf { it.netBalance }
    }
    val receivableCount = remember(parties) {
        parties.count { it.balanceType == "Receivable" && it.netBalance > 0.01 }
    }
    val payableCount = remember(parties) {
        parties.count { it.balanceType == "Payable" && it.netBalance > 0.01 }
    }

    val filteredParties = remember(parties, searchQuery, selectedFilter) {
        parties.filter { summary ->
            val matchesSearch = searchQuery.isBlank() ||
                summary.party.name.contains(searchQuery, ignoreCase = true) ||
                summary.party.businessName.contains(searchQuery, ignoreCase = true) ||
                summary.party.mobile.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Receivable" -> summary.balanceType == "Receivable" && summary.netBalance > 0.01
                "Payable" -> summary.balanceType == "Payable" && summary.netBalance > 0.01
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    if (partyToDelete != null) {
        AlertDialog(
            onDismissRequest = { partyToDelete = null },
            title = { Text("Delete Party") },
            text = { Text("Are you sure you want to delete ${partyToDelete?.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        partyToDelete?.let { viewModel.deleteParty(it.id) }
                        partyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_party")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { partyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parties & Accounts", fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
                onClick = onAddPartyClick,
                containerColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("add_party_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Party")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Outstanding Summary Cards for Receivable and Payable
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search by name or number...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips
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
                        label = { Text("All (${parties.size})") }
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

            if (parties.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No parties added yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredParties.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching parties found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredParties) { summary ->
                        PartyItemCard(
                            summary = summary,
                            onClick = { onPartyClick(summary.party.id) },
                            onEdit = { onEditPartyClick(summary.party.id) },
                            onDelete = { partyToDelete = summary.party }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PartyItemCard(
    summary: PartyLedgerSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val party = summary.party
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val isReceivable = summary.balanceType == "Receivable"
    val isPending = summary.netBalance > 0.01

    val balanceColor = if (isPending) {
        if (isReceivable) Color(0xFF047857) else Color(0xFFB91C1C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val badgeBgColor = if (isPending) {
        if (isReceivable) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFEF4444).copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = party.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (party.businessName.isNotEmpty()) {
                    Text(
                        text = party.businessName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (party.mobile.isNotEmpty()) {
                    Text(
                        text = party.mobile,
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
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = format.format(summary.netBalance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Text(
                    text = if (isPending) summary.balanceType else "No dues",
                    style = MaterialTheme.typography.labelSmall,
                    color = balanceColor,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
