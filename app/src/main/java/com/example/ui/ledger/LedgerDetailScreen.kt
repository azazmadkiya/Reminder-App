package com.example.ui.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import com.example.data.local.TransactionEntity
import com.example.ui.components.DeleteLedgerEntryConfirmationDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.utils.ExportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDetailScreen(
    partyId: Long,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ReminderApplication).container.appRepository
    val viewModel: LedgerDetailViewModel = viewModel(
        factory = LedgerDetailViewModelFactory(repository, partyId)
    )

    val party by viewModel.party.collectAsStateWithLifecycle()
    val transactions by viewModel.partyTransactions.collectAsStateWithLifecycle()

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredTransactions = remember(searchQuery, transactions, dateFormatter) {
        if (searchQuery.isBlank()) {
            transactions
        } else {
            transactions.filter { txn ->
                val dateStr = dateFormatter.format(Date(txn.date))
                txn.type.contains(searchQuery, ignoreCase = true) ||
                txn.description.contains(searchQuery, ignoreCase = true) ||
                txn.paymentMode.contains(searchQuery, ignoreCase = true) ||
                txn.referenceNumber.contains(searchQuery, ignoreCase = true) ||
                dateStr.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    transactionToDelete?.let { txn ->
        DeleteLedgerEntryConfirmationDialog(
            transaction = txn,
            partyName = party?.name,
            onConfirm = {
                val deletedTxn = txn
                viewModel.deleteTransaction(deletedTxn)
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Ledger entry deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.insertTransaction(deletedTxn)
                        val redoResult = snackbarHostState.showSnackbar(
                            message = "Ledger entry restored",
                            actionLabel = "Redo",
                            duration = SnackbarDuration.Short
                        )
                        if (redoResult == SnackbarResult.ActionPerformed) {
                            viewModel.deleteTransaction(deletedTxn)
                        }
                    }
                }
                transactionToDelete = null
            },
            onDismiss = { transactionToDelete = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(party?.name ?: "Ledger Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export as CSV") },
                            onClick = {
                                showMenu = false
                                party?.let {
                                    ExportUtils.exportLedgerToCsv(context, it, transactions)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF") },
                            onClick = {
                                showMenu = false
                                party?.let {
                                    ExportUtils.exportLedgerToPdf(context, it, transactions)
                                }
                            }
                        )
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
        if (party == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val (netBalance, balanceType) = viewModel.calculateBalance(party!!, transactions)
            val isReceivable = balanceType == "Receivable"
            val balanceColor = if (isReceivable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header card with balance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Net Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹%.2f".format(netBalance),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )
                        Text(
                            text = balanceType,
                            style = MaterialTheme.typography.bodyMedium,
                            color = balanceColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search by date, type, or note...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (transactions.isEmpty()) "No transactions found." else "No results matching your search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Let's show opening balance as first item if needed, but it's simpler to just show transactions
                        items(filteredTransactions) { txn ->
                            TransactionCard(
                                txn = txn, 
                                dateFormatter = dateFormatter,
                                onDeleteClick = { transactionToDelete = txn }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCard(
    txn: TransactionEntity, 
    dateFormatter: SimpleDateFormat,
    onDeleteClick: () -> Unit
) {
    val isIncoming = txn.type == "Payment Received" || txn.type == "Debit" // Depends on perspective, let's keep it simple
    // Just color based on Type
    val color = when (txn.type) {
        "Payment Received", "Credit" -> Color(0xFF4CAF50)
        "Payment Paid", "Debit" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    text = txn.type,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormatter.format(Date(txn.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (txn.paymentMode.isNotBlank()) {
                    Text(
                        text = "Mode: ${txn.paymentMode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (txn.description.isNotBlank()) {
                    Text(
                        text = txn.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹%.2f".format(txn.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete, 
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
