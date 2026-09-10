package com.example.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ReminderApplication
import com.example.data.local.PartyEntity
import com.example.data.local.ReminderEntity
import com.example.notification.rememberNotificationPermissionState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.example.ui.components.DeleteTaskConfirmationDialog
import kotlinx.coroutines.launch

import com.example.utils.ExportUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onAddReminderClick: () -> Unit = {},
    onEditReminderClick: (Long) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val viewModel: RemindersViewModel = viewModel(
        factory = RemindersViewModelFactory(appContainer.appRepository, appContainer.alarmScheduler)
    )
    val reminders by viewModel.uiState.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val partyMap = remember(parties) { parties.associateBy { it.id } }

    val permissionState = rememberNotificationPermissionState()
    var showTestNotificationSnackbar by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedDateFilter by remember { mutableStateOf("All Time") }
    var reminderToDelete by remember { mutableStateOf<ReminderEntity?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showVoiceInputDialog by remember { mutableStateOf(false) }
    var voiceCommandText by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.processVoiceCommand(spokenText) { message ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }

    val voiceSettingsManager = appContainer.voiceSettingsManager
    
    fun startSpeechRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a tax deadline or task (e.g., 'File GST return for Sharma Traders by Sept 20')")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !voiceSettingsManager.isInternetAllowedForVoice())
                }
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            showVoiceInputDialog = true
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Speech recognizer unavailable on device. You can type your command.")
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone access is needed for voice task creation.")
            }
            showVoiceInputDialog = true
        }
    }

    fun handleVoiceClick() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            startSpeechRecognition()
        } else {
            try {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        202
                    )
                } else {
                    showVoiceInputDialog = true
                }
            }
        }
    }

    if (showVoiceInputDialog) {
        com.example.ui.components.VoiceControlDialog(
            onDismiss = { showVoiceInputDialog = false },
            onCommandSubmit = { command ->
                viewModel.processVoiceCommand(command) { message ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }

    reminderToDelete?.let { reminder ->
        val party = reminder.partyId?.let { partyMap[it] }
        DeleteTaskConfirmationDialog(
            reminder = reminder,
            party = party,
            onConfirm = {
                val deletedReminder = reminder
                viewModel.deleteReminder(deletedReminder.id) 
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Task '${deletedReminder.title}' deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.addReminder(deletedReminder)
                        val redoResult = snackbarHostState.showSnackbar(
                            message = "Task restored",
                            actionLabel = "Redo",
                            duration = SnackbarDuration.Short
                        )
                        if (redoResult == SnackbarResult.ActionPerformed) {
                            viewModel.deleteReminder(deletedReminder.id)
                        }
                    }
                }
                reminderToDelete = null
            },
            onDismiss = { reminderToDelete = null }
        )
    }

    LaunchedEffect(showTestNotificationSnackbar) {
        if (showTestNotificationSnackbar) {
            snackbarHostState.showSnackbar("Test alert scheduled for 5 seconds from now!")
            showTestNotificationSnackbar = false
        }
    }

    val filterOptions = listOf("All", "Pending", "GST", "Income Tax", "Payment", "Completed")
    val dateFilterOptions = listOf("All Time", "Today", "This Week", "This Month")

    val filteredReminders = remember(reminders, selectedFilter, selectedDateFilter, searchQuery, partyMap) {
        val catFiltered = when (selectedFilter) {
            "All" -> reminders
            "Pending" -> reminders.filter { it.status != "Completed" }
            "GST" -> reminders.filter { it.category == "GST" }
            "Income Tax" -> reminders.filter { it.category == "Income Tax" }
            "Payment" -> reminders.filter { it.category.contains("Payment") }
            "Completed" -> reminders.filter { it.status == "Completed" }
            else -> reminders
        }
        
        val now = System.currentTimeMillis()
        
        val dateFiltered = when (selectedDateFilter) {
            "Today" -> {
                val endOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                catFiltered.filter { it.dueDate in 0..endOfToday }
            }
            "This Week" -> {
                val endOfWeek = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 7)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                catFiltered.filter { it.dueDate in 0..endOfWeek }
            }
            "This Month" -> {
                val endOfMonth = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 30)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                catFiltered.filter { it.dueDate in 0..endOfMonth }
            }
            else -> catFiltered
        }
        
        if (searchQuery.isNotBlank()) {
            val lowerQuery = searchQuery.lowercase()
            dateFiltered.filter { reminder ->
                val partyName = reminder.partyId?.let { partyMap[it]?.name ?: "" } ?: ""
                reminder.title.lowercase().contains(lowerQuery) ||
                reminder.notes.lowercase().contains(lowerQuery) ||
                partyName.lowercase().contains(lowerQuery)
            }
        } else {
            dateFiltered
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search reminders...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text("Reminders & Alerts", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = "" 
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { handleVoiceClick() },
                            modifier = Modifier.testTag("reminders_mic_button")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice Task Creation")
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Export Options")
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Test Alert (5s)") },
                            onClick = {
                                showExportMenu = false
                                if (!permissionState.hasPermission) {
                                    permissionState.requestPermission()
                                }
                                viewModel.scheduleTestAlarm(
                                    title = "GST Compliance Alert",
                                    category = "GST",
                                    delaySeconds = 5
                                )
                                showTestNotificationSnackbar = true
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as CSV") },
                            onClick = {
                                showExportMenu = false
                                ExportUtils.exportRemindersToCsv(context, reminders)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF") },
                            onClick = {
                                showExportMenu = false
                                ExportUtils.exportRemindersToPdf(context, reminders)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddReminderClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Reminder") },
                text = { Text("New Reminder", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    FilterChip(
                        selected = (selectedFilter == filter),
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Date Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dateFilterOptions) { dateFilter ->
                    FilterChip(
                        selected = (selectedDateFilter == dateFilter),
                        onClick = { selectedDateFilter = dateFilter },
                        label = { Text(dateFilter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            if (filteredReminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (selectedFilter == "All") "No reminders scheduled yet." else "No $selectedFilter reminders found.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Schedule due dates and reminder alert options with party dropdown.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = onAddReminderClick) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Reminder")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredReminders, key = { it.id }) { reminder ->
                        val party = reminder.partyId?.let { partyMap[it] }
                        ReminderItemCard(
                            reminder = reminder,
                            party = party,
                            onComplete = { viewModel.completeReminder(reminder) },
                            onEdit = { onEditReminderClick(reminder.id) },
                            onDelete = { reminderToDelete = reminder }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: ReminderEntity,
    party: PartyEntity?,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val isCompleted = reminder.status == "Completed"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onComplete,
                        enabled = !isCompleted,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = "Mark Complete",
                            tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        if (reminder.amount != null && reminder.amount > 0) {
                            Text(
                                text = "₹${NumberFormat.getInstance().format(reminder.amount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit Reminder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Reminder",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tags / Chips Row: Category + Party
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Chip
                val priorityColor = when (reminder.priority) {
                    "High" -> MaterialTheme.colorScheme.error
                    "Medium" -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Amber/Orange
                    else -> MaterialTheme.colorScheme.primary
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(reminder.priority, style = MaterialTheme.typography.labelSmall, color = priorityColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = {
                        Icon(
                            Icons.Filled.Flag,
                            contentDescription = "Priority",
                            tint = priorityColor,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = priorityColor.copy(alpha = 0.1f)),
                    border = null
                )

                // Category Chip
                SuggestionChip(
                    onClick = {},
                    label = { Text(reminder.category, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )

                // Tag Chip
                SuggestionChip(
                    onClick = {},
                    label = { Text(reminder.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                )

                // Party Chip if linked
                if (party != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text(party.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Due Date and Alert Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Due Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Due Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            dateFormat.format(Date(reminder.dueDate)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Reminder Alert Time / Setting
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Reminder Alert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val alertTimeText = if (reminder.dueTime != null) {
                            dateTimeFormat.format(Date(reminder.dueTime))
                        } else {
                            reminder.notificationSettings
                        }
                        Text(
                            alertTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Notes if any
            if (reminder.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes: ${reminder.notes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
