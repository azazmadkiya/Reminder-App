package com.example.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import com.example.data.local.PartyEntity
import com.example.data.local.ReminderEntity
import com.example.notification.NotificationPermissionCard
import com.example.notification.rememberNotificationPermissionState
import com.example.ui.components.DeleteTaskConfirmationDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReminderLeadTime(val title: String, val offsetMillis: Long) {
    ON_DUE_DATE("On Due Date", 0L),
    ONE_DAY_BEFORE("1 Day Before", 24 * 60 * 60 * 1000L),
    TWO_DAYS_BEFORE("2 Days Before", 2 * 24 * 60 * 60 * 1000L),
    THREE_DAYS_BEFORE("3 Days Before", 3 * 24 * 60 * 60 * 1000L),
    ONE_WEEK_BEFORE("1 Week Before", 7 * 24 * 60 * 60 * 1000L),
    CUSTOM("Custom Date & Time", -1L),
    QUICK_TEST("⚡ Quick Test (15s)", 15 * 1000L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    reminderId: Long = 0L,
    onBackClick: () -> Unit,
    onNavigateToAddParty: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val viewModel: RemindersViewModel = viewModel(
        factory = RemindersViewModelFactory(appContainer.appRepository, appContainer.alarmScheduler)
    )

    val permissionState = rememberNotificationPermissionState()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val categoriesList by viewModel.categories.collectAsStateWithLifecycle()
    val templatesList by viewModel.templates.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("GST") }
    var tag by remember { mutableStateOf("General") }
    val availableTags = listOf("General", "Tax", "Payment")
    var notes by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("High") }
    var repeatType by remember { mutableStateOf("One-time") }

    val repeatOptions = listOf("One-time", "Every Month", "Quarterly", "Yearly")

    // 1. Party Selection State
    var selectedParty by remember { mutableStateOf<PartyEntity?>(null) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    // 2. Due Date State (default: Tomorrow at 09:00 AM)
    val defaultCalendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    var dueDateMillis by remember { mutableLongStateOf(defaultCalendar.timeInMillis) }
    var dueHour by remember { mutableIntStateOf(9) }
    var dueMinute by remember { mutableIntStateOf(0) }

    // 3. Set Reminder Date State
    var selectedReminderOption by remember { mutableStateOf(ReminderLeadTime.ONE_DAY_BEFORE) }
    var customReminderMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    val isEditMode = reminderId != 0L
    var existingReminderForDelete by remember { mutableStateOf<ReminderEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reminderId, parties) {
        if (isEditMode) {
            val reminder = viewModel.getReminder(reminderId)
            existingReminderForDelete = reminder
            if (reminder != null) {
                title = reminder.title
                category = reminder.category
                tag = reminder.tag
                notes = reminder.notes
                amountText = if (reminder.amount != null && reminder.amount > 0) reminder.amount.toString() else ""
                priority = reminder.priority
                repeatType = reminder.repeatType
                
                selectedParty = parties.find { it.id == reminder.partyId }
                dueDateMillis = reminder.dueDate
                
                // For simplicity, we just set it to CUSTOM if it's an edit, or we could try to reverse engineer the LeadTime.
                selectedReminderOption = ReminderLeadTime.CUSTOM
                customReminderMillis = reminder.dueTime ?: reminder.dueDate
                
                val cal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                dueHour = cal.get(Calendar.HOUR_OF_DAY)
                dueMinute = cal.get(Calendar.MINUTE)
            }
        }
    }

    val currentCategoryEntity = categoriesList.find { it.name == category }
    val currentTemplates = if (currentCategoryEntity != null) {
        templatesList.filter { it.categoryId == currentCategoryEntity.id }.map { it.name }
    } else emptyList()

    val dateFormatter = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    val shortDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val fullDateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // Calculate actual trigger time based on reminder option
    val calculatedReminderTriggerMillis = when (selectedReminderOption) {
        ReminderLeadTime.ON_DUE_DATE -> dueDateMillis
        ReminderLeadTime.ONE_DAY_BEFORE -> dueDateMillis - ReminderLeadTime.ONE_DAY_BEFORE.offsetMillis
        ReminderLeadTime.TWO_DAYS_BEFORE -> dueDateMillis - ReminderLeadTime.TWO_DAYS_BEFORE.offsetMillis
        ReminderLeadTime.THREE_DAYS_BEFORE -> dueDateMillis - ReminderLeadTime.THREE_DAYS_BEFORE.offsetMillis
        ReminderLeadTime.ONE_WEEK_BEFORE -> dueDateMillis - ReminderLeadTime.ONE_WEEK_BEFORE.offsetMillis
        ReminderLeadTime.QUICK_TEST -> System.currentTimeMillis() + 15 * 1000L
        ReminderLeadTime.CUSTOM -> customReminderMillis
    }

    // Helper to open Due Date Picker
    fun showDueDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, dueHour)
                    set(Calendar.MINUTE, dueMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dueDateMillis = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Helper to open Due Time Picker
    fun showDueTimePicker() {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                dueHour = hourOfDay
                dueMinute = minute
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = dueDateMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dueDateMillis = newCal.timeInMillis
            },
            dueHour,
            dueMinute,
            false
        ).show()
    }

    // Helper to open Custom Reminder Date Picker
    fun showCustomReminderDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = customReminderMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = customReminderMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                customReminderMillis = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Helper to open Custom Reminder Time Picker
    fun showCustomReminderTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = customReminderMillis }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = customReminderMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customReminderMillis = newCal.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        ).show()
    }

    if (showDeleteDialog && existingReminderForDelete != null) {
        DeleteTaskConfirmationDialog(
            reminder = existingReminderForDelete!!,
            party = selectedParty,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteReminder(reminderId)
                onBackClick()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Reminder" else "Add Reminder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode && existingReminderForDelete != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("delete_task_button")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete Task",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Notification Permission Banner if needed
            NotificationPermissionCard()

            // 1. Category Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCategoryDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage")
                    }
                }
                
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoriesList.size) { index ->
                        val cat = categoriesList[index].name
                        FilterChip(
                            selected = (cat == category),
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // 1.5. Tag Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tag", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableTags.size) { index ->
                        val currentTag = availableTags[index]
                        FilterChip(
                            selected = (currentTag == tag),
                            onClick = { tag = currentTag },
                            label = { Text(currentTag) }
                        )
                    }
                }
            }

            // Quick Suggestions based on category
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick Templates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (currentCategoryEntity != null) {
                        TextButton(onClick = { showTemplateDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage")
                        }
                    }
                }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentTemplates.size) { index ->
                        val tmpl = currentTemplates[index]
                        SuggestionChip(
                            onClick = { title = tmpl },
                            label = { Text(tmpl, maxLines = 1) }
                        )
                    }
                }
            }

            // Reminder Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Reminder Title *") },
                placeholder = { Text("e.g. GSTR-3B Return Filing or Client Payment") },
                leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // -------------------------------------------------------------
            // FEATURE 1: PARTY DROPDOWN OPTION ("droup-doun party optins")
            // -------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Party / Customer / Vendor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (parties.isEmpty()) {
                        TextButton(onClick = onNavigateToAddParty) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Party")
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { partyDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selectedParty != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedParty != null) Icons.Filled.Person else Icons.Filled.Business,
                                contentDescription = null,
                                tint = if (selectedParty != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (selectedParty == null) {
                                    Text(
                                        "None / General (Not party specific)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Tap to select a linked party from ledger",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Text(
                                        selectedParty!!.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val mobileText = if (selectedParty!!.mobile.isNotBlank()) selectedParty!!.mobile else "No mobile"
                                    val balanceText = if (selectedParty!!.openingBalance > 0) {
                                        " • ₹${NumberFormat.getInstance().format(selectedParty!!.openingBalance)} (${selectedParty!!.balanceType})"
                                    } else ""
                                    Text(
                                        "$mobileText$balanceText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (selectedParty != null) {
                                IconButton(
                                    onClick = { selectedParty = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear party",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Select Party",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Dropdown Menu for Party Selection
                    DropdownMenu(
                        expanded = partyDropdownExpanded,
                        onDismissRequest = { partyDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("None / General (No Party)", fontWeight = FontWeight.Medium) },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = {
                                selectedParty = null
                                partyDropdownExpanded = false
                            }
                        )
                        HorizontalDivider()

                        if (parties.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("No parties registered yet", fontWeight = FontWeight.SemiBold)
                                        Text("Tap to add your first party", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    partyDropdownExpanded = false
                                    onNavigateToAddParty()
                                }
                            )
                        } else {
                            parties.forEach { party ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(party.name, fontWeight = FontWeight.SemiBold)
                                            val phone = if (party.mobile.isNotBlank()) party.mobile else "No phone"
                                            val balance = if (party.openingBalance > 0) " • ₹${NumberFormat.getInstance().format(party.openingBalance)} (${party.balanceType})" else ""
                                            Text(
                                                "$phone$balance",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    onClick = {
                                        selectedParty = party
                                        partyDropdownExpanded = false
                                        // Auto-fill amount if empty and party has a positive balance
                                        if (amountText.isBlank() && party.openingBalance > 0) {
                                            amountText = party.openingBalance.toInt().toString()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Associated Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Associated Amount (₹ Optional)") },
                placeholder = { Text("e.g. 15000") },
                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // -------------------------------------------------------------
            // FEATURE 2: DUE DATE OPTION ("due date option")
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
                        Text(
                            "Due Date (Final Deadline)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { showDueDatePicker() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Date", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton(
                                onClick = { showDueTimePicker() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Time", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Display Current Due Date and Time
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().clickable { showDueDatePicker() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    dateFormatter.format(Date(dueDateMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Deadline Time: ${timeFormatter.format(Date(dueDateMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Quick presets for Due Date
                    Text("Quick Due Date Presets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            Pair("Today", 0),
                            Pair("Tomorrow", 1),
                            Pair("In 3 Days", 3),
                            Pair("In 7 Days", 7)
                        )
                        presets.forEach { (label, days) ->
                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        add(Calendar.DAY_OF_YEAR, days)
                                        set(Calendar.HOUR_OF_DAY, dueHour)
                                        set(Calendar.MINUTE, dueMinute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    dueDateMillis = cal.timeInMillis
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // GST Special Preset: 20th of the month
                    SuggestionChip(
                        onClick = {
                            val cal = Calendar.getInstance()
                            if (cal.get(Calendar.DAY_OF_MONTH) >= 20) {
                                cal.add(Calendar.MONTH, 1)
                            }
                            cal.set(Calendar.DAY_OF_MONTH, 20)
                            cal.set(Calendar.HOUR_OF_DAY, dueHour)
                            cal.set(Calendar.MINUTE, dueMinute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            dueDateMillis = cal.timeInMillis
                        },
                        label = { Text("📌 20th of Month (GST Filing Due Date)", style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            // -------------------------------------------------------------------
            // FEATURE 3: SET REMINDER DATE OPTIONS ("sate reminder date optins")
            // -------------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Set Reminder Alert Date & Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "When should the phone sound an alarm & notify you before the due date?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Options list
                    ReminderLeadTime.values().forEach { option ->
                        val isSelected = (selectedReminderOption == option)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReminderOption = option }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReminderOption = option }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        option.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val subtitle = when (option) {
                                        ReminderLeadTime.ON_DUE_DATE -> "Alerts at deadline time (${shortDateFormatter.format(Date(dueDateMillis))})"
                                        ReminderLeadTime.ONE_DAY_BEFORE -> "Alerts 24 hours prior to deadline (Recommended)"
                                        ReminderLeadTime.TWO_DAYS_BEFORE -> "Alerts 2 days in advance"
                                        ReminderLeadTime.THREE_DAYS_BEFORE -> "Alerts 3 days in advance"
                                        ReminderLeadTime.ONE_WEEK_BEFORE -> "Alerts 7 days in advance"
                                        ReminderLeadTime.CUSTOM -> "Pick your own alert date and alarm time"
                                        ReminderLeadTime.QUICK_TEST -> "Alarms in 15 seconds (Perfect for testing sound/notification)"
                                    }
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // If Custom Date & Time is selected, show pickers
                    if (selectedReminderOption == ReminderLeadTime.CUSTOM) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Custom Alert Schedule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showCustomReminderDatePicker() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(shortDateFormatter.format(Date(customReminderMillis)), style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { showCustomReminderTimePicker() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(timeFormatter.format(Date(customReminderMillis)), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Scheduled Trigger Preview Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Alarm will ring on:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    fullDateTimeFormatter.format(Date(calculatedReminderTriggerMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (calculatedReminderTriggerMillis < System.currentTimeMillis()) {
                                    Text(
                                        "⚠️ Alert time is in the past; alarm will fire right away after saving.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Repeat Type Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Repeat Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(repeatOptions.size) { index ->
                        val rt = repeatOptions[index]
                        FilterChip(
                            selected = (repeatType == rt),
                            onClick = { repeatType = rt },
                            label = { Text(rt) }
                        )
                    }
                }
            }

            // Priority Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Priority", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("High", "Medium", "Low").forEach { p ->
                        FilterChip(
                            selected = (priority == p),
                            onClick = { priority = p },
                            label = { Text(p) }
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Challan details (Optional)") },
                placeholder = { Text("e.g. Challan #9281, Bank account details, or filing instructions") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Submit Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        if (!permissionState.hasPermission) {
                            permissionState.requestPermission()
                        }

                        val notificationDescription = when (selectedReminderOption) {
                            ReminderLeadTime.CUSTOM -> "Custom: ${fullDateTimeFormatter.format(Date(customReminderMillis))}"
                            ReminderLeadTime.QUICK_TEST -> "Quick Test (15s)"
                            else -> "${selectedReminderOption.title} at ${timeFormatter.format(Date(dueDateMillis))}"
                        }

                        val reminder = ReminderEntity(
                            id = if (isEditMode) reminderId else 0L,
                            title = title.trim(),
                            category = category,
                            tag = tag,
                            partyId = selectedParty?.id,
                            dueDate = dueDateMillis,
                            dueTime = calculatedReminderTriggerMillis,
                            repeatType = repeatType,
                            notificationSettings = notificationDescription,
                            priority = priority,
                            status = "Pending",
                            notes = notes.trim(),
                            amount = amountText.toDoubleOrNull()
                        )
                        if (isEditMode) {
                            viewModel.updateReminder(reminder)
                        } else {
                            viewModel.addReminder(reminder)
                        }
                        onBackClick()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditMode) "Update Reminder Alert" else "Schedule Reminder Alert", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCategoryDialog) {
        ManageCategoriesDialog(
            categories = categoriesList,
            onDismiss = { showCategoryDialog = false },
            onAdd = { viewModel.addCategory(it) },
            onUpdate = { viewModel.updateCategory(it) },
            onDelete = { viewModel.deleteCategory(it) }
        )
    }

    if (showTemplateDialog && currentCategoryEntity != null) {
        ManageTemplatesDialog(
            categoryName = currentCategoryEntity.name,
            templates = templatesList.filter { it.categoryId == currentCategoryEntity.id },
            onDismiss = { showTemplateDialog = false },
            onAdd = { viewModel.addTemplate(currentCategoryEntity.id, it) },
            onUpdate = { viewModel.updateTemplate(it) },
            onDelete = { viewModel.deleteTemplate(it) }
        )
    }
}

@Composable
fun ManageCategoriesDialog(
    categories: List<com.example.data.local.ReminderCategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (com.example.data.local.ReminderCategoryEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<com.example.data.local.ReminderCategoryEntity?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("New Category Name") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onAdd(newCategoryName.trim())
                                newCategoryName = ""
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(categories.size) { index ->
                        val cat = categories[index]
                        if (editingCategory?.id == cat.id) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (editingName.isNotBlank()) {
                                            onUpdate(cat.copy(name = editingName.trim()))
                                            editingCategory = null
                                        }
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = "Save")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.name, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(onClick = {
                                        editingCategory = cat
                                        editingName = cat.name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onDelete(cat.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ManageTemplatesDialog(
    categoryName: String,
    templates: List<com.example.data.local.ReminderTemplateEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (com.example.data.local.ReminderTemplateEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    var newTemplateName by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableStateOf<com.example.data.local.ReminderTemplateEntity?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Templates for $categoryName") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text("New Template Name") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newTemplateName.isNotBlank()) {
                                onAdd(newTemplateName.trim())
                                newTemplateName = ""
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(templates.size) { index ->
                        val tmpl = templates[index]
                        if (editingTemplate?.id == tmpl.id) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (editingName.isNotBlank()) {
                                            onUpdate(tmpl.copy(name = editingName.trim()))
                                            editingTemplate = null
                                        }
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = "Save")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tmpl.name, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(onClick = {
                                        editingTemplate = tmpl
                                        editingName = tmpl.name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onDelete(tmpl.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
