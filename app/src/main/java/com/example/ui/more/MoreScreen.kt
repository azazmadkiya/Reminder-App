package com.example.ui.more

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ReminderApplication
import com.example.backup.BackupPreview
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appContainer = (context.applicationContext as ReminderApplication).container
    val securityManager = appContainer.securityManager
    val backupManager = appContainer.backupManager
    val voiceSettingsManager = appContainer.voiceSettingsManager
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Security states
    var isAppLockEnabled by remember { mutableStateOf(securityManager.isAppLockEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(securityManager.isBiometricEnabled()) }
    val isBiometricSupported = remember { securityManager.canUseBiometrics(context) }
    
    // Voice Settings state
    var isVoiceInternetAllowed by remember { mutableStateOf(voiceSettingsManager.isInternetAllowedForVoice()) }

    // Dialog states for PIN
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    // PIN Form states
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Backup states
    var lastBackupTime by remember { mutableLongStateOf(backupManager.getLastBackupTime()) }
    var isLoadingBackup by remember { mutableStateOf(false) }
    var isBackupReminderEnabled by remember { mutableStateOf(backupManager.isBackupReminderEnabled()) }
    var backupFrequency by remember { mutableStateOf(backupManager.getBackupReminderFrequency()) }
    var nextBackupReminderTime by remember { mutableLongStateOf(backupManager.getNextBackupReminderTime()) }

    // Restore Dialog states
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var replaceExistingOnRestore by remember { mutableStateOf(false) }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // File Picker for Saving Backup
    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoadingBackup = true
                val success = backupManager.exportToFile(uri)
                isLoadingBackup = false
                if (success) {
                    lastBackupTime = backupManager.getLastBackupTime()
                    snackbarHostState.showSnackbar("Backup successfully exported to file!")
                } else {
                    snackbarHostState.showSnackbar("Failed to export backup file.")
                }
            }
        }
    }

    // File Picker for Opening/Restoring Backup
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoadingBackup = true
                val jsonString = backupManager.readTextFromUri(uri)
                isLoadingBackup = false
                if (jsonString != null) {
                    val preview = backupManager.parsePreview(jsonString)
                    if (preview != null) {
                        pendingRestoreJson = jsonString
                        pendingRestorePreview = preview
                        showRestoreConfirmDialog = true
                    } else {
                        snackbarHostState.showSnackbar("Invalid or corrupted backup file format.")
                    }
                } else {
                    snackbarHostState.showSnackbar("Could not read the selected backup file.")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // -----------------------------------------------------------------
            // SECTION 1: BIOMETRIC & APP SECURITY (Upon Launch)
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAppLockEnabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Biometric & App Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Badge(
                                    containerColor = if (isAppLockEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isAppLockEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(
                                        if (isAppLockEnabled) "Active on Launch" else "Disabled",
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                            Text(
                                "Secure sensitive ledger and tax records upon app launch with fingerprint, face unlock, or PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Toggle: Enable App Lock on Launch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = !isAppLockEnabled
                                securityManager.setAppLockEnabled(newState)
                                isAppLockEnabled = newState
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (newState) "App lock enabled: Biometric/PIN prompt active upon launch"
                                        else "App lock disabled"
                                    )
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Lock App upon Launch",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isAppLockEnabled) "Requires fingerprint, face unlock, or 4-digit PIN when opening app"
                                else "Direct access without biometric or PIN check",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { checked ->
                                securityManager.setAppLockEnabled(checked)
                                isAppLockEnabled = checked
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "App lock enabled: Biometric/PIN prompt active upon launch"
                                        else "App lock disabled"
                                    )
                                }
                            }
                        )
                    }

                    // Options visible when App Lock is enabled
                    AnimatedVisibility(visible = isAppLockEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Fingerprint / Biometric Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newState = !isBiometricEnabled
                                        securityManager.setBiometricEnabled(newState)
                                        isBiometricEnabled = newState
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Fingerprint,
                                        contentDescription = null,
                                        tint = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Biometric Authentication",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            when (securityManager.getBiometricStatus(context)) {
                                                com.example.security.BiometricStatus.READY -> "Sensor ready: Fingerprint & Face Unlock active"
                                                com.example.security.BiometricStatus.NOT_ENROLLED -> "Sensor detected • Not enrolled in Android Settings"
                                                com.example.security.BiometricStatus.NO_HARDWARE -> "No biometric hardware • Using PIN"
                                                com.example.security.BiometricStatus.UNAVAILABLE -> "Sensor unavailable • Using PIN"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isBiometricEnabled,
                                    onCheckedChange = { checked ->
                                        securityManager.setBiometricEnabled(checked)
                                        isBiometricEnabled = checked
                                    }
                                )
                            }

                            // Test Biometric Button
                            OutlinedButton(
                                onClick = {
                                    activity?.let { act ->
                                        securityManager.authenticateWithBiometrics(
                                            activity = act,
                                            title = "Biometric Verification",
                                            subtitle = "Confirm fingerprint or face recognition",
                                            onSuccess = {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Biometric authentication successful!")
                                                }
                                            },
                                            onError = { err ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Biometric: $err")
                                                }
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Biometric Prompt", style = MaterialTheme.typography.labelMedium)
                            }

                            // Change PIN button & Lock App Now
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        oldPin = ""
                                        newPin = ""
                                        confirmPin = ""
                                        pinError = null
                                        showChangePinDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (securityManager.hasCustomPinSet()) "Change PIN" else "Set Custom PIN",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        securityManager.lockSession()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lock Now", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // SECTION 2: BACKUP AND RESTORE OPTIONS
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Offline JSON backup for parties, transactions, and reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tip: When uninstalling the app, check 'Keep app data' to easily recover your data on reinstall without needing a backup file.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Last Backup status
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Last Backup Status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (lastBackupTime > 0) dateFormatter.format(Date(lastBackupTime)) else "Never backed up yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // -----------------------------------------------------------------
                    // RECURRING BACKUP REMINDER TOGGLE & SETTINGS
                    // -----------------------------------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = !isBackupReminderEnabled
                                backupManager.setBackupReminderEnabled(newState)
                                isBackupReminderEnabled = newState
                                nextBackupReminderTime = backupManager.getNextBackupReminderTime()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (newState) "Recurring backup reminders enabled ($backupFrequency)"
                                        else "Recurring backup reminders turned off"
                                    )
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isBackupReminderEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = if (isBackupReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Backup Reminder",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isBackupReminderEnabled) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(backupFrequency, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                    }
                                }
                                Text(
                                    "Recurring alerts to backup records and avoid business data loss",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isBackupReminderEnabled,
                            onCheckedChange = { checked ->
                                backupManager.setBackupReminderEnabled(checked)
                                isBackupReminderEnabled = checked
                                nextBackupReminderTime = backupManager.getNextBackupReminderTime()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "Recurring backup reminders enabled ($backupFrequency)"
                                        else "Recurring backup reminders turned off"
                                    )
                                }
                            }
                        )
                    }

                    // Expanded Recurring Reminder Options (Frequency & Next Schedule)
                    AnimatedVisibility(visible = isBackupReminderEnabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Reminder Frequency",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Daily", "Weekly", "Monthly").forEach { freq ->
                                        val isSelected = backupFrequency.equals(freq, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                backupFrequency = freq
                                                backupManager.setBackupReminderFrequency(freq)
                                                nextBackupReminderTime = backupManager.getNextBackupReminderTime()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Backup reminder set to $freq (at 8:00 PM)")
                                                }
                                            },
                                            label = { Text(freq) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (nextBackupReminderTime > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Next reminder: ${dateFormatter.format(Date(nextBackupReminderTime))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        com.example.notification.NotificationHelper.showBackupReminderNotification(context)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Test backup reminder notification sent!")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send Test Reminder Notification", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Backup Action 1: Share / Export Backup
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoadingBackup = true
                                val shareIntent = backupManager.shareBackupFile()
                                isLoadingBackup = false
                                if (shareIntent != null) {
                                    lastBackupTime = backupManager.getLastBackupTime()
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Backup File"))
                                } else {
                                    snackbarHostState.showSnackbar("Could not create backup share file.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share / Export Backup File")
                    }

                    // Backup Action 2: Save to device file
                    OutlinedButton(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            saveBackupLauncher.launch("Reminder_Backup_$timestamp.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Backup to Device Storage")
                    }

                    // Restore Action: Pick File to Restore
                    FilledTonalButton(
                        onClick = {
                            openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Data from Backup File")
                    }
                }
            }

            // -----------------------------------------------------------------
            // SECTION 3: VOICE INTERNET ACCESS OPTIONS
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color(0xFFE0F2F1),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Cloud,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF00897B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Voice Internet Access",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (isVoiceInternetAllowed) "Choice: YES • Uses internet for improved recognition." else "Choice: NO • Strictly 100% offline speech recognition without internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isVoiceInternetAllowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                if (isVoiceInternetAllowed) "YES (Internet)" else "NO (Offline)",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isVoiceInternetAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                voiceSettingsManager.setInternetAllowedForVoice(true)
                                isVoiceInternetAllowed = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isVoiceInternetAllowed) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
                            )
                        ) {
                            if (isVoiceInternetAllowed) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("YES (Allow Internet)", color = if (isVoiceInternetAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }

                        OutlinedButton(
                            onClick = {
                                voiceSettingsManager.setInternetAllowedForVoice(false)
                                isVoiceInternetAllowed = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (!isVoiceInternetAllowed) androidx.compose.ui.graphics.Color(0xFFE1BEE7) else androidx.compose.ui.graphics.Color.Transparent
                            )
                        ) {
                            if (!isVoiceInternetAllowed) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("NO (100% Offline)", color = androidx.compose.ui.graphics.Color.Black)
                        }
                    }

                    Text(
                        "Restricted ONLY to voice recognizer. Financial data stays offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Zero Financial Cloud Tracking: Internet access is restricted EXCLUSIVELY to speech recognition if enabled. Financial ledger, balances, assets, and liabilities remain 100% offline on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // SETTING TAB FOOTER: APP CREATOR CREDIT
            // -----------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Created with",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Heart",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "by",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Azaz Madkiya",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Reminder App • Version 1.0.0\n© 2026 Azaz Madkiya. All Rights Reserved.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // -------------------------------------------------------------------------
    // DIALOG: SET 4-DIGIT PIN (When enabling App Lock for first time)
    // -------------------------------------------------------------------------
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set 4-Digit Security PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a 4-digit PIN. This PIN will be required to unlock the app when App Lock is active.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("Enter 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length != 4) {
                            pinError = "PIN must be exactly 4 digits"
                        } else if (newPin != confirmPin) {
                            pinError = "PINs do not match. Re-enter correctly."
                        } else {
                            securityManager.setPin(newPin)
                            securityManager.setAppLockEnabled(true)
                            isAppLockEnabled = true
                            showSetPinDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("App Lock enabled with your 4-digit PIN.")
                            }
                        }
                    }
                ) {
                    Text("Set PIN & Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // DIALOG: CHANGE 4-DIGIT PIN
    // -------------------------------------------------------------------------
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Security PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!securityManager.hasCustomPinSet()) {
                        Text(
                            "Default PIN is 1234. Enter 1234 as Current PIN to set your custom PIN.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) oldPin = it },
                        label = { Text("Current 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!securityManager.verifyPin(oldPin)) {
                            pinError = "Current PIN is incorrect."
                        } else if (newPin.length != 4) {
                            pinError = "New PIN must be exactly 4 digits."
                        } else if (newPin != confirmPin) {
                            pinError = "New PINs do not match."
                        } else {
                            securityManager.setPin(newPin)
                            showChangePinDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Security PIN changed successfully.")
                            }
                        }
                    }
                ) {
                    Text("Update PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // DIALOG: RESTORE CONFIRMATION PREVIEW
    // -------------------------------------------------------------------------
    if (showRestoreConfirmDialog && pendingRestorePreview != null) {
        val preview = pendingRestorePreview!!
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreJson = null
                pendingRestorePreview = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Restore Data", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Found valid backup from ${dateFormatter.format(Date(preview.exportDate))}:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Parties: ${preview.partyCount} records", style = MaterialTheme.typography.bodySmall)
                            Text("• Transactions: ${preview.transactionCount} records", style = MaterialTheme.typography.bodySmall)
                            Text("• Reminders: ${preview.reminderCount} records", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Text("Restore Mode:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceExistingOnRestore = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !replaceExistingOnRestore,
                            onClick = { replaceExistingOnRestore = false }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Merge with existing data", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Preserves current records, adds and updates from backup", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceExistingOnRestore = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = replaceExistingOnRestore,
                            onClick = { replaceExistingOnRestore = true }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Clean Replace (Overwrite all)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Erases existing records and restores backup exactly", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingRestoreJson
                        if (json != null) {
                            showRestoreConfirmDialog = false
                            coroutineScope.launch {
                                isLoadingBackup = true
                                val result = backupManager.restoreFromJson(json, replaceExistingOnRestore)
                                isLoadingBackup = false
                                pendingRestoreJson = null
                                pendingRestorePreview = null
                                snackbarHostState.showSnackbar(result.message)
                            }
                        }
                    }
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreJson = null
                        pendingRestorePreview = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
