package com.example.ui.parties

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import com.example.data.local.PartyEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartyScreen(partyId: Long = 0L, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val viewModel: PartiesViewModel = viewModel(
        factory = PartiesViewModelFactory(appContainer.appRepository)
    )

    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("") }
    var balanceType by remember { mutableStateOf("Receivable") }
    
    val balanceTypes = listOf("Receivable", "Payable")
    val isEditMode = partyId != 0L

    LaunchedEffect(partyId) {
        if (isEditMode) {
            val party = viewModel.getParty(partyId)
            if (party != null) {
                name = party.name
                mobile = party.mobile
                gstin = party.gstin
                openingBalance = if (party.openingBalance > 0) party.openingBalance.toString() else ""
                balanceType = party.balanceType
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Party" else "Add Party") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Party Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = gstin,
                onValueChange = { gstin = it },
                label = { Text("GSTIN (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = openingBalance,
                onValueChange = { openingBalance = it },
                label = { Text("Opening Balance") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            
            Text("Balance Type", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                balanceTypes.forEach { type ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = (type == balanceType),
                            onClick = { balanceType = type }
                        )
                        Text(type)
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val party = PartyEntity(
                            id = if (isEditMode) partyId else 0L,
                            name = name,
                            businessName = name, // default
                            mobile = mobile,
                            email = "",
                            gstin = gstin,
                            pan = "",
                            address = "",
                            openingBalance = openingBalance.toDoubleOrNull() ?: 0.0,
                            balanceType = balanceType
                        )
                        if (isEditMode) {
                            viewModel.updateParty(party)
                        } else {
                            viewModel.addParty(party)
                        }
                        onBackClick()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditMode) "Update Party" else "Save Party")
            }
        }
    }
}
