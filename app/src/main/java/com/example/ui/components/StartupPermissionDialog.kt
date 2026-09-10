package com.example.ui.components

import android.Manifest
import android.app.AlarmManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R

@Composable
fun StartupPermissionDialog(isReady: Boolean = true) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    var hasShownPopup by rememberSaveable {
        mutableStateOf(sharedPrefs.getBoolean("has_shown_startup_permissions", false))
    }
    var isDismissed by rememberSaveable { mutableStateOf(false) }

    fun checkNotifGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun checkExactAlarmGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun checkMicGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    var isNotificationGranted by remember { mutableStateOf(checkNotifGranted()) }
    var isExactAlarmGranted by remember { mutableStateOf(checkExactAlarmGranted()) }
    var isMicGranted by remember { mutableStateOf(checkMicGranted()) }

    fun dismissPermanently() {
        try {
            sharedPrefs.edit().putBoolean("has_shown_startup_permissions", true).apply()
        } catch (_: Exception) {}
        hasShownPopup = true
        isDismissed = true
    }

    // Refresh permission states whenever screen is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationGranted = checkNotifGranted()
                isExactAlarmGranted = checkExactAlarmGranted()
                isMicGranted = checkMicGranted()
                if (isNotificationGranted && isExactAlarmGranted && isMicGranted && !hasShownPopup) {
                    dismissPermanently()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isNotificationGranted = checkNotifGranted()
        isExactAlarmGranted = checkExactAlarmGranted()
        isMicGranted = checkMicGranted()
        if (isNotificationGranted && isExactAlarmGranted && isMicGranted) {
            dismissPermanently()
        }
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        isNotificationGranted = checkNotifGranted()
        isExactAlarmGranted = checkExactAlarmGranted()
        isMicGranted = checkMicGranted()
        if (isNotificationGranted && isExactAlarmGranted && isMicGranted) {
            dismissPermanently()
        }
    }

    fun requestNotificationPermission() {
        if (isNotificationGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                Log.w("StartupPermission", "Launcher failed, falling back to ActivityCompat: ${e.message}")
                val activity = context as? Activity
                if (activity != null) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        } else {
            // Android 12 and below: Notifications don't need runtime permission
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun requestMicPermission() {
        if (isMicGranted) return
        try {
            singleLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } catch (e: Exception) {
            Log.w("StartupPermission", "Launcher failed, falling back to ActivityCompat: ${e.message}")
            val activity = context as? Activity
            if (activity != null) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    102
                )
            }
        }
    }

    fun requestExactAlarmPermission() {
        if (isExactAlarmGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w("StartupPermission", "Failed to open exact alarm settings: ${e.message}")
            }
        }
    }

    fun requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isExactAlarmGranted) {
            requestExactAlarmPermission()
            // We launch exact alarm first since it opens settings, user will come back.
            return 
        }

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!isMicGranted) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isEmpty()) {
            dismissPermanently()
            return
        }
        try {
            multipleLauncher.launch(needed.toTypedArray())
        } catch (e: Exception) {
            Log.w("StartupPermission", "Multiple launcher failed, falling back: ${e.message}")
            val activity = context as? Activity
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, needed.toTypedArray(), 103)
            }
        }
    }

    val shouldShow = isReady && !hasShownPopup && !isDismissed && !(isNotificationGranted && isMicGranted && isExactAlarmGranted)

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(99f)
    ) {
        // Full screen safe scrim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent tap through */ }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = shouldShow,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 500.dp)
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF00897B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Welcome to ${stringResource(R.string.app_name)}",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Enable permissions for reminders & voice",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "To ensure timely alerts for tax deadlines, SIPs & loan dues, and to create tasks hands-free using voice, please grant access:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Notifications Card
                        PermissionStatusCard(
                            icon = Icons.Filled.Notifications,
                            title = "Notifications",
                            description = "Timely alerts for upcoming GST, tax deadlines, SIP payments, and payment dues.",
                            isGranted = isNotificationGranted,
                            onAllowClick = { requestNotificationPermission() }
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Exact Alarm Card
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PermissionStatusCard(
                                icon = Icons.Filled.Alarm,
                                title = "Exact Alarms",
                                description = "Ensures background alerts fire exactly on time, even if the app is closed.",
                                isGranted = isExactAlarmGranted,
                                onAllowClick = { requestExactAlarmPermission() }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Microphone Card
                        PermissionStatusCard(
                            icon = Icons.Filled.Mic,
                            title = "Microphone",
                            description = "Hands-free voice recognition to speak and record financial tasks instantly.",
                            isGranted = isMicGranted,
                            onAllowClick = { requestMicPermission() }
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        // Primary Action Button
                        val allGranted = isNotificationGranted && isMicGranted && isExactAlarmGranted
                        val buttonText = if (allGranted) "Continue to Dashboard" else "Allow All Permissions"

                        Button(
                            onClick = {
                                if (allGranted) {
                                    dismissPermanently()
                                } else {
                                    requestAllPermissions()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                buttonText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Skip / Dismiss Button
                        TextButton(
                            onClick = { dismissPermanently() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Skip for Now",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onAllowClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) Color(0xFFC8E6C9) else Color(0xFFE0F2F1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFF00897B),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isGranted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Granted",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFC8E6C9),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Allowed",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAllowClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00897B))
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
