package com.example.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVoucherScreen(
    onBackClick: () -> Unit,
    onNavigateToAddParty: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ReminderApplication).container.appRepository
    val viewModel: AddVoucherViewModel = viewModel(factory = AddVoucherViewModelFactory(repository))

    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showPartyDropdown by remember { mutableStateOf(false) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showModeDropdown by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val voucherTypes = listOf("Payment Received", "Payment Paid", "Debit", "Credit")
    val paymentModes = listOf("Cash", "Bank", "UPI", "Cheque", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Voucher") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Party Dropdown
            ExposedDropdownMenuBox(
                expanded = showPartyDropdown,
                onExpandedChange = { showPartyDropdown = it }
            ) {
                val selectedParty = parties.find { it.id == viewModel.selectedPartyId }
                OutlinedTextField(
                    value = selectedParty?.name ?: "Select Party",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Party Name *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPartyDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showPartyDropdown,
                    onDismissRequest = { showPartyDropdown = false }
                ) {
                    if (parties.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No parties available. Add new.") },
                            onClick = {
                                showPartyDropdown = false
                                onNavigateToAddParty()
                            }
                        )
                    } else {
                        parties.forEach { party ->
                            DropdownMenuItem(
                                text = { Text(party.name) },
                                onClick = {
                                    viewModel.selectedPartyId = party.id
                                    showPartyDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Date Picker
            OutlinedTextField(
                value = dateFormatter.format(Date(viewModel.date)),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date *") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = "Select Date"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = viewModel.date)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    viewModel.date = it
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Type Dropdown
            ExposedDropdownMenuBox(
                expanded = showTypeDropdown,
                onExpandedChange = { showTypeDropdown = it }
            ) {
                OutlinedTextField(
                    value = viewModel.type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voucher Type *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false }
                ) {
                    voucherTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                viewModel.type = type
                                showTypeDropdown = false
                            }
                        )
                    }
                }
            }

            // Amount
            OutlinedTextField(
                value = viewModel.amount,
                onValueChange = { viewModel.amount = it },
                label = { Text("Amount *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Payment Mode Dropdown
            ExposedDropdownMenuBox(
                expanded = showModeDropdown,
                onExpandedChange = { showModeDropdown = it }
            ) {
                OutlinedTextField(
                    value = viewModel.paymentMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false }
                ) {
                    paymentModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                viewModel.paymentMode = mode
                                showModeDropdown = false
                            }
                        )
                    }
                }
            }

            // Reference Number
            OutlinedTextField(
                value = viewModel.referenceNumber,
                onValueChange = { viewModel.referenceNumber = it },
                label = { Text("Reference Number (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Notes / Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (viewModel.selectedPartyId != null && viewModel.amount.isNotBlank()) {
                        viewModel.saveVoucher(onSuccess = { onBackClick() })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.selectedPartyId != null && viewModel.amount.isNotBlank()
            ) {
                Text("Save Voucher")
            }
        }
    }
}
